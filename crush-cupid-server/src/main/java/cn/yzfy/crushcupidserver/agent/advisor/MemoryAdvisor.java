package cn.yzfy.crushcupidserver.agent.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.stereotype.Component;

/**
 * 关系记忆 advisor：从请求上下文注入 Relationship Memory。
 */
@Component
public class MemoryAdvisor extends AbstractPromptAdvisor {

    public static final String CONTEXT_KEY = "memory";

    @Override
    protected int order() {
        return 300;
    }

    @Override
    protected String resolveSystemText(ChatClientRequest request) {
        Object value = request.context().get(CONTEXT_KEY);
        return value == null ? null : value.toString();
    }
}
