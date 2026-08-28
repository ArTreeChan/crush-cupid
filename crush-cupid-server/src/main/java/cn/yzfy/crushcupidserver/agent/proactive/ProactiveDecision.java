package cn.yzfy.crushcupidserver.agent.proactive;

import lombok.Data;

/**
 * LLM 主动发言决策输出。
 * <p>
 * 由 {@link ProactiveDecisionService} 调用 LLM，结合暗恋对象的 persona / 记忆 / 关系阶段 /
 * 用户最近活跃情况，自主判断此刻是否该主动找人聊天、说什么方向、下一次再找的时间。
 *
 * @author 一朝风月
 * @code vo
 * @createTime 2026-08-27
 */
@Data
public class ProactiveDecision {

    /** 是否在当前这个时间窗口主动发消息 */
    private boolean shouldSend;

    /** 决策理由（仅供日志/调试，不发给用户） */
    private String reason;

    /** 给 LLM 生成内容时的方向暗示（如「分享日常 / 撒娇 / 关心」） */
    private String messageDirection;

    /** 若 shouldSend=false，下一次再考虑主动发言的相对分钟数（>0） */
    private Integer nextInMinutes;
}
