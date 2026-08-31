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
    private Boolean isDefault;
}
