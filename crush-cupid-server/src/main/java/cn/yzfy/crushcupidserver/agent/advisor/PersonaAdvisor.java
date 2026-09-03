package cn.yzfy.crushcupidserver.agent.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.stereotype.Component;

/**
 * 人物性格 advisor：从请求上下文注入 Persona。
 */
@Component
public class PersonaAdvisor extends AbstractPromptAdvisor {

    public static final String CONTEXT_KEY = "persona";

    @Override
    protected int order() {
        return 200;
    }

    @Override
    protected String resolveSystemText(ChatClientRequest request) {
        Object value = request.context().get(CONTEXT_KEY);
        return value == null ? null : value.toString();
    }
}
