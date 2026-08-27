package cn.yzfy.crushcupidserver.config;

import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.agent.advisor.SafetyAdvisor;
import cn.yzfy.crushcupidserver.agent.tool.CrushTools;
import cn.yzfy.crushcupidserver.agent.tool.OcrTools;
import cn.yzfy.crushcupidserver.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
@Component
@RequiredArgsConstructor
public class ChatClientProvider {

    private final ChatModelRegistry chatModelRegistry;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final SafetyAdvisor safetyAdvisor;
    private final CrushTools crushTools;
    private final OcrTools ocrTools;

    /** 供应商代号 -> ChatClient（懒加载，线程安全由 ConcurrentHashMap 保证） */
    private final Map<String, ChatClient> clients = new ConcurrentHashMap<>();

    /** 本地 @Tool 方法回调，所有 ChatClient 共享 */
    private ToolCallbackProvider methodToolCallbackProvider;

    /** 工具回调（本地 @Tool），所有 ChatClient 共享 */
    private ToolCallback[] allToolCallbacks;

    @PostConstruct
    public void init() {
        this.methodToolCallbackProvider = MethodToolCallbackProvider.builder()
                .toolObjects(crushTools, ocrTools)
                .build();
        // 只合并本地 @Tool 方法；MCP 远端工具不进 ChatClient（避免启动期对百炼 listTools 失败拖垮启动），
        // OCR 主路径由 OcrService 直调 MCP，聊天工具面保持本地即可
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
}
