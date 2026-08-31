package cn.yzfy.crushcupidserver.model.dto;

import lombok.Data;

/**
 * @className AiProviderDTO
 * @description 自定义大模型供应商的新建/更新入参（更新时 null 字段表示不修改）
 * @author crush-cupid
 * @code dto
 * @createTime 2026-08-31
 */
@Data
public class AiProviderDTO {

    /** 显示名，如「自定义 OpenAI」 */
    private String name;

    /** 供应商代号（路由 key），唯一，如 my-openai */
    private String providerKey;

    /** OpenAI 兼容 base-url */
    private String baseUrl;

    /** API Key（允许空，走环境密钥） */
    private String apiKey;

    /** 模型名 */
    private String model;

    /** 温度 */
    private Double temperature;

    /** top-p 核采样（可选） */
    private Double topP;

    /** 最大生成 token 数（可选） */
    private Integer maxTokens;

    /** 能力列表：vision=视觉看图, audio=音频听语音 */
    private java.util.List<String> capabilities;

    /** 是否设为默认供应商 */
    private Boolean isDefault;
}
