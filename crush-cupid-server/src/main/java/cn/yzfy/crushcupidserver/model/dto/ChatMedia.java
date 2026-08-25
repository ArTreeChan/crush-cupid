package cn.yzfy.crushcupidserver.model.dto;

import lombok.Data;

/**
 * @className ChatMedia
 * @description 多模态输入片段。支持图片/音频的 URL 或 base64 两种数据形态，
 * 由 CupidAgent 转换为 Spring AI 的 {@link org.springframework.ai.chat.messages.Media}。
 * @author 一朝风月
 * @code dto
 * @createTime 2026-08-26
 */
@Data
public class ChatMedia {

    /** 图片前缀 */
    public static final String TYPE_IMAGE_URL = "IMAGE_URL";
    /** 图片 base64 */
    public static final String TYPE_IMAGE_BASE64 = "IMAGE_BASE64";
    /** 音频 URL */
    public static final String TYPE_AUDIO_URL = "AUDIO_URL";
    /** 音频 base64 */
    public static final String TYPE_AUDIO_BASE64 = "AUDIO_BASE64";

    /** 类型：IMAGE_URL / IMAGE_BASE64 / AUDIO_URL / AUDIO_BASE64 */
    private String type;

    /**
     * MIME 类型，如 image/png、image/jpeg、audio/wav、audio/mpeg。
     * 为空时按 {@link #type} 推断默认值。
     */
    private String mimeType;

    /** 数据：URL 字符串（*_URL）或 base64 字符串（*_BASE64） */
    private String data;
}
