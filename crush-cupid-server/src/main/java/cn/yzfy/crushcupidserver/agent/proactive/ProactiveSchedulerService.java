package cn.yzfy.crushcupidserver.agent.proactive;

import cn.yzfy.crushcupidserver.agent.CupidAgent;
import cn.yzfy.crushcupidserver.model.entity.Crush;
import cn.yzfy.crushcupidserver.service.CrushService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * 主动消息调度器：定时「触发探测」器。
 * <p>
 * 设计要点（对应需求「根据已有记忆/对话，crush 主动发消息」）：
 * <ul>
 *   <li><b>定时任务只做唤醒与分发</b>：{@code @Scheduled} 线程保持轻量，只做「扫描到期 + 硬约束过滤 +
 *       提交到虚拟线程池」，不做任何 LLM/DB 阻塞调用，避免单线程串行卡住所有 crush；</li>
 *   <li><b>DDL 决策并发化</b>：每个 crush 的「决策 + 生成 + 推送」通过 {@link ProactiveExecutorConfig}
 *       的虚拟线程执行器并行跑，配合信号量控制瞬时并发上限；</li>
 *   <li><b>真正决策交给 LLM</b>：{@link ProactiveDecisionService} 结合 persona + 记忆 + 关系 + 用户活跃度
 *       判断「此刻该不该发、发什么方向、下次什么时候」——次数与内容由状态推理得出；</li>
 *   <li><b>硬约束兜底（防骚扰）</b>：活跃时段、冷却期、每日上限，代码层面强校验，避免 LLM 情绪化刷屏；</li>
 *   <li><b>推送</b>：通过 {@link ProactivePushService} 把新消息推给正在查看该 crush 的前端页面。</li>
 * </ul>
 *
 * @author 一朝风月
 * @code scheduler
 * @createTime 2026-08-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProactiveSchedulerService {

    private final CrushService crushService;
    private final CupidAgent cupidAgent;
    private final ProactiveDecisionService decisionService;
    private final ProactivePushService pushService;
    private final ProactiveProperties properties;

    @Qualifier("proactiveExecutor")
    private final Executor proactiveExecutor;
    @Qualifier("proactiveLimiter")
    private final Semaphore limiter;

    /** 正在处理中的 crushId（防窗口期重复提交到虚拟线程池） */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    /** 兜底扫描 + 心跳，按配置间隔执行。方法本身轻量：只挑任务提交，不阻塞。 */
    @Scheduled(fixedDelayString = "${crush.proactive.scan-interval-ms:60000}")
    public void scanAndDispatch() {
        if (!properties.isEnabled()) {
            return;
        }
        pushService.heartbeatAll();
        List<Crush> crushes;
        try {
            crushes = crushService.list();
        } catch (Exception e) {
            log.warn("主动调度扫描 crush 列表失败 err={}", e.getMessage());
            return;
        }
        for (Crush crush : crushes) {
            try {
                dispatchIfEligible(crush);
            } catch (Exception e) {
                log.warn("主动调度提交 crush={} 失败 err={}", crush.getSlug(), e.getMessage());
            }
        }
    }

    /**
     * 只做「同步快判 + 提交」：
     * 1) 硬约束过滤（纯内存，无 IO——nextProactiveAt/cooldown/limit 都从已加载的 crush 字段判断）；
     * 2) in-flight 去重 + 占位顺延（防止窗口期内被下一轮扫描重复提交）；
     * 3) 提交到虚拟线程池异步执行。
     */
    private void dispatchIfEligible(Crush crush) {
        if (!isEnabled(crush)) {
            return;
        }
        if (windowNotOpen(crush)) {
            return;
        }
        if (!inActiveHours()) {
            scheduleNext(crush, minutesUntilNextActiveHour());
            return;
        }
        if (inCooldown(crush)) {
            scheduleNext(crush, elapsedCooldownMinutes(crush));
            return;
        }
        if (dailyLimitReached(crush)) {
            scheduleTomorrow(crush);
            return;
        }
        // 进程内去重：同 crush 已在处理中则跳过（快速路径，避免额外 DB 往返）
        if (!inFlight.add(crush.getId())) {
            return;
        }
        // 原子抢窗：DB 条件更新「窗口已到期(含从未调度) → 顺延 60s 占位」，只有抢到
        // （影响 1 行）才提交执行器。防多线程/多扫描周期同时放行同一 crush 的 doSend，
        // 同时保证同 crush 的计数/状态更新单飞行。
        boolean claimed = crushService.update(
                new LambdaUpdateWrapper<Crush>()
                        .eq(Crush::getId, crush.getId())
                        .and(w -> w.isNull(Crush::getNextProactiveAt)
                                .or()
                                .le(Crush::getNextProactiveAt, new Date()))
                        .set(Crush::getNextProactiveAt,
                                Date.from(java.time.Instant.now().plusSeconds(60))));
        if (!claimed) {
            // 已被其它线程抢窗（或窗口被并发重置），释放进程内占位
            inFlight.remove(crush.getId());
            return;
        }
        proactiveExecutor.execute(() -> runWithLimiter(crush));
    }

    /** 在执行器的虚拟线程上，加信号量限流后执行完整任务；任务结束释放 in-flight 占位。 */
    private void runWithLimiter(Crush crush) {
        try {
            // 阻塞式 acquire：虚拟线程挂起在信号量上几乎零成本，排队等待而非丢失任务
            limiter.acquire();
            try {
                processAndSend(crush);
            } finally {
                limiter.release();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("主动任务被中断 crush={}", crush.getSlug());
        } catch (Exception e) {
            log.warn("主动调度处理 crush={} 失败 err={}", crush.getSlug(), e.getMessage());
        } finally {
            inFlight.remove(crush.getId());
        }
    }

    /** 完整处理：LLM 决策 → 若应发言则生成 + 落库 + 推送，并据决策更新下次窗口。 */
    private void processAndSend(Crush crush) {
        ProactiveDecision decision = decisionService.decide(crush);
        int nextMinutes = decision.getNextInMinutes() == null ? 120 : decision.getNextInMinutes();

        if (!decision.isShouldSend()) {
            log.info("LLM 决策此刻不主动发言 crush={} reason={}", crush.getSlug(), decision.getReason());
            scheduleNext(crush, nextMinutes);
            return;
        }
        doSend(crush, decision, nextMinutes);
    }

    /** 真正生成 + 落库 + 推送，并更新调度状态。 */
    private void doSend(Crush crush, ProactiveDecision decision, int nextMinutes) {
        String text = cupidAgent.proactiveSilent(crush, decision.getMessageDirection());
        if (text == null || text.isBlank()) {
            log.warn("主动消息生成结果为空 crush={}", crush.getSlug());
            scheduleNext(crush, nextMinutes);
            return;
        }

        Date now = new Date();
        int newCount = (isSameDay(crush.getProactiveDate(), now))
                ? countSafe(crush.getProactiveCount()) + 1
                : 1;

        Crush update = new Crush();
        update.setId(crush.getId());
        update.setLastProactiveAt(now);
        update.setProactiveDate(java.sql.Date.valueOf(LocalDate.now()));
        update.setProactiveCount(newCount);
        update.setNextProactiveAt(Date.from(java.time.Instant.now().plusSeconds(Math.max(30L, nextMinutes * 60L))));
        crushService.updateById(update);

        log.info("主动消息已发送 crush={} 当日第{}次 nextIn={}min direction={}",
                crush.getSlug(), newCount, nextMinutes, decision.getMessageDirection());

        pushService.broadcast(crush.getSlug(), text);
    }

    // ================= 硬约束校验（同步快判，无 IO） =================

    private boolean isEnabled(Crush crush) {
        return Boolean.TRUE.equals(crush.getProactiveEnabled());
    }

    /** 主动窗口是否尚未开启（nextProactiveAt > now）。null（从未调度）视为已开启，可触发。 */
    private boolean windowNotOpen(Crush crush) {
        return crush.getNextProactiveAt() != null && crush.getNextProactiveAt().after(new Date());
    }

    private boolean inActiveHours() {
        int h = LocalTime.now().getHour();
        return properties.getActiveHours().contains(h);
    }

    private boolean inCooldown(Crush crush) {
        Date last = crush.getLastProactiveAt();
        return last != null && minutesSince(last) < properties.getCooldownMinutes();
    }

    private long elapsedCooldownMinutes(Crush crush) {
        long since = minutesSince(crush.getLastProactiveAt());
        return Math.max(10L, (long) properties.getCooldownMinutes() - since);
    }

    private boolean dailyLimitReached(Crush crush) {
        return isSameDay(crush.getProactiveDate(), new Date())
                && countSafe(crush.getProactiveCount()) >= properties.getDailyLimit();
    }

    /** 更新 nextProactiveAt（分钟粒度，至少 1 分钟） */
    private void scheduleNext(Crush crush, long minutes) {
        Crush update = new Crush();
        update.setId(crush.getId());
        long seconds = Math.max(60L, minutes * 60L);
        update.setNextProactiveAt(Date.from(java.time.Instant.now().plusSeconds(seconds)));
        crushService.updateById(update);
    }

    /** 顺延到明日同一时段再考虑（每日上限触顶） */
    private void scheduleTomorrow(Crush crush) {
        Crush update = new Crush();
        update.setId(crush.getId());
        update.setNextProactiveAt(Date.from(java.time.Instant.now().plusSeconds(Duration.ofHours(16).toSeconds())));
        crushService.updateById(update);
    }

    /** 距离下一个活跃时段的分钟数 */
    private long minutesUntilNextActiveHour() {
        int hour = LocalTime.now().getHour();
        for (int i = 1; i <= 24; i++) {
            int candidate = (hour + i) % 24;
            if (properties.getActiveHours().contains(candidate)) {
                return i * 60L;
            }
        }
        return 6 * 60L;
    }

    private boolean isSameDay(Date date, Date other) {
        if (date == null) {
            return false;
        }
        return LocalDate.from(date.toInstant().atZone(java.time.ZoneId.systemDefault()))
                .equals(LocalDate.from(other.toInstant().atZone(java.time.ZoneId.systemDefault())));
    }

    private long minutesSince(Date date) {
        return Duration.between(date.toInstant(), java.time.Instant.now()).toMinutes();
    }

    private int countSafe(Integer v) {
        return v == null ? 0 : v;
    }
}
