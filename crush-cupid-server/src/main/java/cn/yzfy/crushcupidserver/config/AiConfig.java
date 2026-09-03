package cn.yzfy.crushcupidserver.config;

import cn.yzfy.crushcupidserver.agent.advisor.SafetyAdvisor;
import cn.yzfy.crushcupidserver.agent.tool.CrushTools;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * @className AiConfig
 * @description Agent 装配（Factory）。负责装配 memory advisor、ChatMemory（基于 PG）与开启 LlmProperties 绑定。
 * <p>
 * ChatClient 不再此处装配为单一 Bean，而是由 {@link ChatClientProvider} 按供应商代号动态构造，
 * 支持 DeepSeek / 通义千问 / OpenAI 多供应商路由与多模态扩展。
 * @author 一朝风月
 * @code configuration
 * @createTime 2026-08-26
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class AiConfig {

    /**
     * 基于 PG 的 ChatMemory：包装 {@link PgChatMemoryRepository}，窗口放大到 200 条
     * （默认 20 条对暗恋模拟器太短，聊几天就被砍）。
     * Spring AI autoconfigure 检测到本 Bean 后跳过默认 InMemory ChatMemory 注册。
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(200)
                .build();
    }

    /**
     * 基于会话记忆的 advisor，注入所有 ChatClient。@Primary 保证按类型注入唯一取到 PG 版。
     */
    @Bean
    @Primary
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    /**
     * 军师对话的独立内存记忆：不落库（不污染 conversation 表与模拟对话历史），
     * 仅在服务运行期内按 crush 保持军师会话的上下文连贯。
     */
    @Bean
    public MessageWindowChatMemory advisorChatMemory() {
        return MessageWindowChatMemory.builder().maxMessages(40).build();
    }

    // SafetyAdvisor / CrushTools 已用 @Component / @Service 自注册，无需在此重复声明。
    // ChatModelRegistry / ChatClientProvider 也是 @Component，自动扫描注册。
}

