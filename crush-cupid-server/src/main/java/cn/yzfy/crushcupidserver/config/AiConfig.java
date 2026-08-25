package cn.yzfy.crushcupidserver.config;

import cn.yzfy.crushcupidserver.agent.advisor.SafetyAdvisor;
import cn.yzfy.crushcupidserver.agent.tool.CrushTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 装配（Factory）：组装 ChatClient 的默认 advisor 与工具。
 */
@Configuration
public class AiConfig {

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 MessageChatMemoryAdvisor memoryAdvisor,
                                 SafetyAdvisor safetyAdvisor,
                                 CrushTools crushTools) {
        ToolCallbackProvider toolCallbackProvider = MethodToolCallbackProvider.builder()
                .toolObjects(crushTools)
                .build();
        return builder
                .defaultAdvisors(memoryAdvisor, safetyAdvisor)
                .defaultToolCallbacks(toolCallbackProvider)
                .build();
    }
}
