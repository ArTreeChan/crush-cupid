package cn.yzfy.crushcupidserver.agent.advisor;

import cn.hutool.core.util.StrUtil;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 系统提示注入 advisor 基类（Template Method）。
 * 子类只需提供要追加的系统文本与优先级。
 */
public abstract class AbstractPromptAdvisor implements BaseAdvisor {

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String text = resolveSystemText(request);
        if (StrUtil.isBlank(text)) {
            return request;
        }
        Prompt prompt = request.prompt().augmentSystemMessage(systemMessage -> {
            String existing = systemMessage.getText();
            String combined = (existing == null || existing.isBlank()) ? text : existing + "\n\n" + text;
            return new SystemMessage(combined);
        });
        return request.mutate().prompt(prompt).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return order();
    }

    /** 优先级，越小越先执行 */
    protected abstract int order();

    /** 要追加到系统提示中的文本 */
    protected abstract String resolveSystemText(ChatClientRequest request);
}
