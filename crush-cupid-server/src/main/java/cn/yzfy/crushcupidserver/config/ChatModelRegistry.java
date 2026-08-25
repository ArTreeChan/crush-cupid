package cn.yzfy.crushcupidserver.config;

import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.exception.BizException;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @className ChatModelRegistry
 * @description LLM 供应商注册中心。启动时按 {@link LlmProperties} 为每个供应商构造一个
 * {@link OpenAiChatModel}（统一走 OpenAI 兼容协议），并暴露按 key 路由的能力。
 * <p>
 * 设计要点：
 * 1. 所有供应商（DeepSeek / 通义千问 / OpenAI）均使用 OpenAI 兼容协议，零额外依赖；
 * 2. 每个供应商独立的 apiKey/baseUrl/model/temperature，互不影响；
 * 3. 业务侧不再直接依赖单一 ChatModel Bean，而是通过本 Registry 路由。
 * @author 一朝风月
 * @code registry
 * @createTime 2026-08-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatModelRegistry {

    private final LlmProperties llmProperties;

    /**
     * Alibaba DashScope 原生 ChatModel（由 spring-ai-alibaba-starter-dashscope 自动配置注册）。
     * 用 ObjectProvider 容错：未配 DASHSCOPE_API_KEY 时不阻塞启动，仅 [qwen-native] 不可用。
     */
    private final ObjectProvider<DashScopeChatModel> dashscopeChatModelProvider;

    /** [qwen-native] 供应商代号：走 Alibaba DashScope 原生协议，拿通义全家桶 */
    public static final String QWEN_NATIVE = "qwen-native";

    /** 供应商代号 -> ChatModel 实例 */
    @Getter
    private final Map<String, ChatModel> models = new LinkedHashMap<>();

    /** 供应商代号 -> 供应商配置（供业务侧查询是否多模态等元信息） */
    @Getter
    private final Map<String, LlmProperties.ProviderConfig> configs = new LinkedHashMap<>();

    /**
     * 启动时为每个供应商构造 {@link OpenAiChatModel}。
     * 跳过缺 apiKey 的供应商，避免硬启动失败。
     */
    @PostConstruct
    public void init() {
        if (llmProperties.getProviders() == null || llmProperties.getProviders().isEmpty()) {
            log.warn("crush.ai.providers 未配置任何 LLM 供应商，LLM 调用将不可用");
            return;
        }
        llmProperties.getProviders().forEach((key, cfg) -> {
            if (StrUtil.isBlank(cfg.getApiKey())) {
                log.warn("供应商 [{}] 未配置 apiKey，跳过注册", key);
                return;
            }
            ChatModel model = buildModel(cfg);
            models.put(key, model);
            configs.put(key, cfg);
            log.info("已注册 LLM 供应商 [{}] -> model={}, baseUrl={}, multimodal={}",
                    key, cfg.getModel(), cfg.getBaseUrl(), cfg.isMultimodal());
        });

        if (!models.containsKey(llmProperties.getDefaultProvider())) {
            throw new IllegalStateException("默认 LLM 供应商 [" + llmProperties.getDefaultProvider()
                    + "] 未注册成功，请检查 crush.ai.providers 配置");
        }

        // 探测 Alibaba DashScope 原生 ChatModel Bean，注册为 [qwen-native] 供应商
        DashScopeChatModel dashscope = dashscopeChatModelProvider.getIfAvailable();
        if (dashscope != null) {
            models.put(QWEN_NATIVE, dashscope);
            LlmProperties.ProviderConfig nativeCfg = new LlmProperties.ProviderConfig();
            nativeCfg.setBaseUrl("dashscope-native");
            nativeCfg.setModel("(alibaba-managed: qwen-plus/qwen-vl-plus/qwen-omni-turbo)");
            nativeCfg.setMultimodal(true);
            configs.put(QWEN_NATIVE, nativeCfg);
            log.info("已注册 Alibaba DashScope 原生 ChatModel -> [{}]（通义全家桶 + 多模态）", QWEN_NATIVE);
        } else {
            log.warn("未探测到 DashScopeChatModel Bean，[qwen-native] 不可用（需配置 spring.ai.dashscope.api-key）");
        }
    }

    /**
     * 按代号获取 ChatModel，缺省时返回默认供应商的 ChatModel。
     */
    public ChatModel get(String provider) {
        if (StrUtil.isBlank(provider)) {
            return getDefault();
        }
        ChatModel model = models.get(provider);
        if (model == null) {
            throw BizException.badRequest("未知的 LLM 供应商：" + provider);
        }
        return model;
    }

    /** 默认供应商 ChatModel */
    public ChatModel getDefault() {
        return models.get(llmProperties.getDefaultProvider());
    }

    /** 默认供应商代号 */
    public String defaultProvider() {
        return llmProperties.getDefaultProvider();
    }

    /** 是否多模态供应商 */
    public boolean isMultimodal(String provider) {
        LlmProperties.ProviderConfig cfg = provider == null ? null : configs.get(provider);
        return cfg != null && cfg.isMultimodal();
    }

    /**
     * 构造单个 OpenAI 兼容协议的 ChatModel。
     */
    private ChatModel buildModel(LlmProperties.ProviderConfig cfg) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(cfg.getBaseUrl())
                .apiKey(cfg.getApiKey())
                .build();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(cfg.getModel())
                .temperature(cfg.getTemperature());
        if (cfg.getTopP() != null) {
            optionsBuilder.topP(cfg.getTopP());
        }
        if (cfg.getMaxTokens() != null) {
            optionsBuilder.maxTokens(cfg.getMaxTokens());
        }

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(optionsBuilder.build())
                .build();
    }
}
