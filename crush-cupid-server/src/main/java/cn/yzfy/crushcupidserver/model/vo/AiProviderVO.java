package cn.yzfy.crushcupidserver.model.vo;

import lombok.Data;

/**
 * @className AiProviderVO
 * @description 自定义大模型供应商响应 VO
 * @author crush-cupid
 * @code vo
 * @createTime 2026-08-31
 */
@Data
public class AiProviderVO {

    private Long id;
    private String name;
    private String providerKey;
    private String baseUrl;
    private String apiKey;
    private String model;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    /** 能力列表：vision=视觉看图, audio=音频听语音 */
    private java.util.List<String> capabilities;
    /** 供应商类型：chat=对话大模型 / voice=语音大模型 */
    private String type;
    /** 语音合成默认音色（仅 type=voice 时用） */
    private String voice;
    private Boolean isDefault;
}
