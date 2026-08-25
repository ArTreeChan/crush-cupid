package cn.yzfy.crushcupidserver.model.dto;

import lombok.Data;

import java.util.List;

/**
 * @className ChatRequestDTO
 * @description 对话入参。支持文本消息 + 多模态 media（图片/音频 URL 或 base64）。
 * @author 一朝风月
 * @code dto
 * @createTime 2026-08-26
 */
@Data
public class ChatRequestDTO {

    /** 暗恋对象 slug */
    private String crushSlug;

    /** 用户文本消息（与 media 二者至少有一个） */
    private String message;

    /** 指定 LLM 供应商代号（如 deepseek / qwen / openai）；为空走默认供应商 */
    private String provider;

    /** 多模态输入列表，可包含图片/音频的 URL 或 base64；为空则纯文本对话 */
    private List<ChatMedia> media;
}
