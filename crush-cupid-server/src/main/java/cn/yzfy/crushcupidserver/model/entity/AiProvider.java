package cn.yzfy.crushcupidserver.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @className AiProvider
 * @description 自定义大模型 API 供应商（运行时增删改查）。统一走 OpenAI 兼容协议，
 * 入库后由 {@link cn.yzfy.crushcupidserver.config.ChatModelRegistry} 动态注册，无需改配置/重启。
 * @author crush-cupid
 * @code entity
 * @createTime 2026-08-31
 */
@Data
@TableName("ai_provider")
public class AiProvider implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

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

    /** 能力列表（逗号分隔：vision=视觉看图, audio=音频听语音，文本(text)是所有 LLM 基本能力无需声明） */
    private String capabilities;

    /** 供应商类型：chat=对话大模型 / voice=语音大模型，默认 chat */
    private String type;

    /** 语音合成默认音色（仅 type=voice 时用，如 longyingling_v3） */
    private String voice;

    /** 是否设为默认供应商 */
    private Boolean isDefault;

    private Date createdAt;
    private Date updatedAt;
}
