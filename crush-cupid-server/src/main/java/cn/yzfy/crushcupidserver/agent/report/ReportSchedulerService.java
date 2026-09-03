package cn.yzfy.crushcupidserver.agent.report;

import cn.yzfy.crushcupidserver.model.entity.Conversation;
import cn.yzfy.crushcupidserver.model.entity.Crush;
import cn.yzfy.crushcupidserver.service.ConversationService;
import cn.yzfy.crushcupidserver.service.CrushReportService;
import cn.yzfy.crushcupidserver.service.CrushService;
import cn.yzfy.crushcupidserver.skill.SkillReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 关系报告自动生成调度器：每天按 cron 为每个有聊天记录的暗恋对象生成一份关系进展报告。
 * <p>
 * 设计要点：
 * <ul>
 *   <li><b>轻量调度线程</b>：{@code @Scheduled} 方法只做「选择目标 + 逐份生成」，不等并发——报告是
 *       重量级 LLM 调用，且通常数量少，串行逐个生成避免同时打爆 LLM/DB；</li>
 *   <li><b>每日去重</b>：每个 crush 当天只生成一份（{@link CrushReportService#existsOnDate}），
 *       重复调度/重启不会重复生成；</li>
 *   <li><b>只给有对话的 crush 生成</b>：无任何聊天记录的 crush 跳过，避免生成无意义报告。</li>
 * </ul>
 *
 * @author 一朝风月
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportSchedulerService {

    private final CrushService crushService;
    private final ConversationService conversationService;
    private final CrushReportService crushReportService;
    private final SkillReportService skillReportService;
    private final ReportProperties properties;

    @Scheduled(cron = "${crush.report.cron:0 0 9 * * ?}")
    public void generateDaily() {
        if (!properties.isEnabled()) {
            return;
        }
        log.info("[report-sched] 开始每日关系报告自动生成");
        LocalDate today = LocalDate.now();
        List<Crush> crushes;
        try {
            crushes = crushService.list();
        } catch (Exception e) {
            log.warn("[report-sched] 扫描 crush 列表失败 err={}", e.getMessage());
            return;
        }
        int generated = 0;
        for (Crush crush : crushes) {
            try {
                if (ignorable(crush, today)) {
                    continue;
                }
                skillReportService.generateAndSave(crush.getSlug(), "scheduled");
                generated++;
                log.info("[report-sched] 已自动生成报告 crush={}", crush.getSlug());
            } catch (Exception e) {
                log.warn("[report-sched] 生成报告失败 crush={} err={}", crush.getSlug(), e.getMessage());
            }
        }
        log.info("[report-sched] 每日报告自动生成完成，本次生成 {} 份", generated);
    }

    /** 是否跳过该 crush：无对话记录，或当天已生成过 */
    private boolean ignorable(Crush crush, LocalDate today) {
        try {
            if (crushReportService.existsOnDate(crush.getId(), today)) {
                return true;
            }
            long convCount = conversationService.lambdaQuery()
                    .eq(Conversation::getCrushId, crush.getId())
                    .count();
            return convCount == 0;
        } catch (Exception e) {
            log.warn("[report-sched] 判定 crush={} 是否可生成失败 err={}", crush.getSlug(), e.getMessage());
            return true;
        }
    }
}
