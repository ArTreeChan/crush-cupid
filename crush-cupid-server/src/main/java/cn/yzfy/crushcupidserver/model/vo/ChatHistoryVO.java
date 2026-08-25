package cn.yzfy.crushcupidserver.model.vo;

import cn.yzfy.crushcupidserver.model.entity.Conversation;
import lombok.Data;

import java.util.Date;

/**
 * @className ChatHistoryVO
 * @description 对话历史条目视图。前端按 role 渲染气泡，按 createdAt 排序。
 * @author 一朝风月
 * @code vo
 * @createTime 2026-08-26
 */
@Data
public class ChatHistoryVO {

    /** user / assistant / system / tool */
    private String role;

    private String content;

    private Date createdAt;

    public static ChatHistoryVO of(Conversation c) {
        ChatHistoryVO vo = new ChatHistoryVO();
        vo.role = c.getRole();
        vo.content = c.getContent();
        vo.createdAt = c.getCreatedAt();
        return vo;
    }
}
