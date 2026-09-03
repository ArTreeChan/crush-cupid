package cn.yzfy.crushcupidserver.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @className ChatMedia
 * @description 对话图片/媒体 URL 独立存储表，与 {@link Conversation} 解耦。
 * <p>
 * 每条对话消息可能关联一张图片（用户发送的图片落盘后的可访问 URL）。
 * 图片 URL 不再拼进消息文本，而是单独存此表，避免消息文本被 URL 污染导致回显异常。
 * <p>
 * 不设 FK 到 conversation(id)，因为 PgChatMemoryRepository.saveAll 采用「先清空再插入」
 * 覆盖语义，FK 级联删除会丢失图片记录。改为按 crush_id + created_at 独立管理，
 * ChatHistoryController 加载历史时按顺序匹配 [图片] 标记与 chat_media 记录。
 * @author 一朝风月
 * @code entity
 * @createTime 2026-08-28
 */
@Data
@TableName("chat_media")
public class ChatMedia implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long crushId;

    /** user / assistant */
    private String role;

    /** 图片可访问 URL（如 /api/uploads/20260828/xxx.jpg） */
    private String mediaUrl;

    /** 媒体类型，默认 image */
    private String mediaType;

    private Date createdAt;
}
