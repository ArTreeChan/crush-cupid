package cn.yzfy.crushcupidserver.config;

import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.agent.advisor.SafetyAdvisor;
import cn.yzfy.crushcupidserver.agent.tool.CrushTools;
import cn.yzfy.crushcupidserver.agent.tool.OcrTools;
import cn.yzfy.crushcupidserver.agent.tool.StickerTools;
import cn.yzfy.crushcupidserver.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @className ChatClientProvider
 * @description ChatClient 路由提供者。按供应商代号构造并缓存 {@link ChatClient}，
 * 每个 ChatClient 共享同一套 advisor（memory/safety）与工具回调，但绑定不同的底层 ChatModel。
 * <p>
 * 业务侧（如 CupidAgent）按 crush 或请求级 provider 选择 ChatClient，实现「一个对话用某个供应商」。
 * @author 一朝风月
 * @code provider
 * @createTime 2026-08-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatClientProvider {

    private final ChatModelRegistry chatModelRegistry;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final SafetyAdvisor safetyAdvisor;
    private final CrushTools crushTools;
    private final OcrTools ocrTools;
    private final StickerTools stickerTools;

    /** 供应商代号 -> ChatClient（懒加载，线程安全由 ConcurrentHashMap 保证） */
    private final Map<String, ChatClient> clients = new ConcurrentHashMap<>();

    /** 本地 @Tool 方法回调，所有 ChatClient 共享 */
    private ToolCallbackProvider methodToolCallbackProvider;

    /** 工具回调（本地 @Tool），所有 ChatClient 共享 */
    private ToolCallback[] allToolCallbacks;

    @PostConstruct
    public void init() {
        // 表情包不通过 tool call 实现——Spring AI 1.1.2 的 stream()+tool round-trip 不稳定，
        // 会导致 LLM 想调 pickSticker 时 SSE 流卡住、表情包发不出来。
        // 改为 prompt 标记方案：LLM 输出 [[sticker:情绪]] 文本标记，后端替换为真实图片 URL。
        // LLM 仍自主思考何时发、发什么情绪（由 appendStickerGuide prompt 指引），只是不走 tool call。
        this.methodToolCallbackProvider = MethodToolCallbackProvider.builder()
                .toolObjects(crushTools, ocrTools)
                .build();
        this.allToolCallbacks = methodToolCallbackProvider.getToolCallbacks();
        // 预构造默认供应商的 ChatClient
        getOrCreate(chatModelRegistry.defaultProvider());
    }

    /**
     * 按代号获取 ChatClient。缺省回退到默认供应商。
     */
    public ChatClient get(String provider) {
        String key = StrUtil.isBlank(provider) ? chatModelRegistry.defaultProvider() : provider;
        return getOrCreate(key);
    }

    /** 默认供应商 ChatClient */
    public ChatClient getDefault() {
        return getOrCreate(chatModelRegistry.defaultProvider());
    }

    private ChatClient getOrCreate(String provider) {
        return clients.computeIfAbsent(provider, this::buildClient);
    }

    /**
     * 为指定供应商构造 ChatClient：以对应 ChatModel 为底座，绑定 memory/safety advisor + 工具。
     */
    private ChatClient buildClient(String provider) {
        org.springframework.ai.chat.model.ChatModel chatModel = chatModelRegistry.get(provider);
        return ChatClient.builder(chatModel)
                .defaultAdvisors(memoryAdvisor, safetyAdvisor)
                .defaultToolCallbacks(allToolCallbacks)
                .build();
    }

    /**
     * 校验供应商是否多模态，便于在多模态请求时给业务层提示。
     */
    public void ensureMultimodal(String provider) {
        if (!chatModelRegistry.isMultimodal(provider)) {
            throw BizException.badRequest("供应商 [" + provider + "] 不支持多模态，请切换到 qwen-vl / gpt-4o 等");
        }
    }

    /**
     * 查询供应商是否声明支持多模态（vision/audio）。
     * 聊天发图按此分流：多模态直传原图走视觉理解；非多模态降级 OCR 提取文字。
     */
    public boolean isMultimodal(String provider) {
        return chatModelRegistry.isMultimodal(provider);
    }

    /**
     * 解析最终生效的供应商代号：
     * <ul>
     *   <li>请求带图片 media 且当前供应商非多模态时，自动切换到已注册的多模态视觉模型
     *       （优先 qwen-vl / qwen-native），让模型真正“看懂”聊天图片；</li>
     *   <li>否则维持请求指定或默认供应商。</li>
     * </ul>
     *
     * @param requestedProvider 请求级供应商代号（可为空）
     * @param hasImageMedia     本次请求是否携带图片 media
     * @return 最终生效的供应商代号
     */
    public String resolveProvider(String requestedProvider, boolean hasImageMedia) {
        String base = StrUtil.isBlank(requestedProvider) ? chatModelRegistry.defaultProvider() : requestedProvider;
        if (!hasImageMedia || chatModelRegistry.isMultimodal(base)) {
            return base;
        }
        String multimodal = chatModelRegistry.firstMultimodal();
        if (multimodal == null || multimodal.equals(base)) {
            throw BizException.badRequest("当前供应商 [" + base + "] 不支持图片，且未配置任何多模态视觉模型");
        }
        log.info("请求带图片但供应商 [{}] 非多模态，自动切换到 [{}] 视觉模型", base, multimodal);
        return multimodal;
    }
}
