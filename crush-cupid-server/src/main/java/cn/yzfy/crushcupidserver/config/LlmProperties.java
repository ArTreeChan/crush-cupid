package cn.yzfy.crushcupidserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @className LlmProperties
 * @description 多 LLM 供应商配置属性。所有供应商统一走 OpenAI 兼容协议
 * （DeepSeek / 通义千问 DashScope 兼容模式 / OpenAI 均可用），便于无差别切换与多模态扩展。
 * @author 一朝风月
 * @code config properties
 * @createTime 2026-08-26
 */
@Data
@ConfigurationProperties(prefix = "crush.ai")
public class LlmProperties {

    /** 默认供应商 key，对应 {@link #providers} 中的一个条目 */
    private String defaultProvider = "deepseek";

    /**
     * 供应商配置表，key 为供应商代号（如 deepseek / qwen / openai）。
     * 每个供应商独立 baseUrl / apiKey / model，互不影响。
     */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    /**
     * @className ProviderConfig
     * @description 单个 LLM 供应商连接配置
     * @author 一朝风月
     * @code config properties
     * @createTime 2026-08-26
     */
    @Data
    public static class ProviderConfig {

        /** OpenAI 兼容协议的 base-url，例如 https://api.deepseek.com 或 https://dashscope.aliyuncs.com/compatible-mode/v1 */
        private String baseUrl;

        /** API Key */
        private String apiKey;

        /** 默认模型名，如 deepseek-chat / qwen-plus / gpt-4o */
        private String model;

        /** 温度，越低越稳定，越高越发散 */
        private Double temperature = 0.7;

        /** top-p 核采样，可选 */
        private Double topP;

        /** 最大生成 token 数，可选 */
        private Integer maxTokens;

        /** 是否支持视觉（图像理解），默认 false */
        private boolean vision = false;

        /** 是否支持音频输入（语音理解），默认 false */
        private boolean audio = false;
    }
}
