package cn.yzfy.crushcupidserver.model.dto;

import lombok.Data;

/**
 * @className ProactiveRequestDTO
 * @description 主动消息请求入参。用户进入对话页或点击「等 ta 主动找我」时调用，
 * 让 crush 不依赖用户输入而主动发起连发多条消息。
 * @author 一朝风月
 * @code dto
 * @createTime 2026-08-26
 */
@Data
public class ProactiveRequestDTO {

    /** 暗恋对象 slug */
    private String crushSlug;

    /** 指定 LLM 供应商代号，可选；为空走默认 */
    private String provider;

    /** 上下文暗示，例如「凌晨三点」「用户刚发了一条朋友圈」「下雨天」，可选 */
    private String contextHint;
}
