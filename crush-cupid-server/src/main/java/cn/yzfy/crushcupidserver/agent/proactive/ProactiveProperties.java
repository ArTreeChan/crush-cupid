package cn.yzfy.crushcupidserver.agent.proactive;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 主动消息调度配置（{@code crush.proactive.*}）。
 * <p>
 * 这里的配置只做「硬约束兜底」：真正的「发不发、发几次、发什么」由 {@link ProactiveDecisionService}
 * 的 LLM 决策决定，本配置提供冷却 / 每日上限 / 活跃时段等防骚扰硬边界。
 *
 * @author 一朝风月
 * @code config
 * @createTime 2026-08-27
 */
@Data
@ConfigurationProperties(prefix = "crush.proactive")
public class ProactiveProperties {

    /** 是否启用主动消息定时调度 */
    private boolean enabled = true;

    /** 调度器扫描间隔毫秒（探测到期的主动窗口） */
    private long scanIntervalMs = 60_000;

    /** 并发的 LLM 决策/生成任务上限（信号量限流，防同时打爆 LLM/数据库） */
    private int maxConcurrent = 3;

    /** 两次主动发言最小冷却分钟（避免短时间连发刷屏） */
    private int cooldownMinutes = 90;

    /** 每日主动发言上限次数 */
    private int dailyLimit = 3;

    /** 活动时段（小时制），只在这些时段内允许主动发言 */
    private List<Integer> activeHours = new ArrayList<>(List.of(8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22));

    /** 主动推送事件类型标识（前端据此判定新气泡） */
    public static final String EVENT_TYPE = "proactive";
}
