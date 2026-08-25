package cn.yzfy.crushcupidserver.agent;

import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.agent.advisor.MemoryAdvisor;
import cn.yzfy.crushcupidserver.agent.advisor.PersonaAdvisor;
import cn.yzfy.crushcupidserver.exception.BizException;
import cn.yzfy.crushcupidserver.model.dto.ChatRequestDTO;
import cn.yzfy.crushcupidserver.model.entity.Crush;
import cn.yzfy.crushcupidserver.model.service.CrushService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 智能 agent 门面（Facade）：对外只暴露 chat，屏蔽工具注册、advisor、记忆、持久化细节。
 */
@Service
@RequiredArgsConstructor
public class CupidAgent {

    private final ChatClient chatClient;
    private final CrushService crushService;
    private final PersonaAdvisor personaAdvisor;
    private final MemoryAdvisor memoryAdvisor;

    public Flux<String> chat(ChatRequestDTO dto) {
        if (StrUtil.isBlank(dto.getCrushSlug())) {
            throw BizException.badRequest("缺少 crushSlug");
        }
        if (StrUtil.isBlank(dto.getMessage())) {
            throw BizException.badRequest("消息不能为空");
        }

        Crush crush = crushService.getBySlug(dto.getCrushSlug());
        if (crush == null) {
            throw BizException.notFound("未找到暗恋对象：" + dto.getCrushSlug());
        }

        String conversationId = "crush:" + crush.getId();
        String persona = buildPersona(crush);
        String memory = buildMemory(crush);

        return chatClient.prompt()
                .user(dto.getMessage())
                .advisors(a -> a
                        .advisors(personaAdvisor, memoryAdvisor)
                        .param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param(PersonaAdvisor.CONTEXT_KEY, persona)
                        .param(MemoryAdvisor.CONTEXT_KEY, memory))
                .stream()
                .content();
    }

    private String buildPersona(Crush c) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是").append(c.getName()).append("，不是 AI 助手。用 ta 的方式说话、用 ta 的逻辑思考。\n");
        if (notBlank(c.getMbti())) sb.append("MBTI：").append(c.getMbti()).append("\n");
        if (notBlank(c.getZodiac())) sb.append("星座：").append(c.getZodiac()).append("\n");
        if (notBlank(c.getRelationshipStatus())) sb.append("与用户关系：").append(c.getRelationshipStatus()).append("\n");
        if (notBlank(c.getImpression())) sb.append("用户对你的印象：").append(c.getImpression()).append("\n");
        appendLayer(sb, "Layer 0 硬规则", c.getPersonaLayer0());
        appendLayer(sb, "Layer 1 身份", c.getPersonaLayer1());
        appendLayer(sb, "Layer 2 说话风格", c.getPersonaLayer2());
        appendLayer(sb, "Layer 3 情感模式", c.getPersonaLayer3());
        appendLayer(sb, "Layer 4 关系行为", c.getPersonaLayer4());
        return sb.toString();
    }

    private String buildMemory(Crush c) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        if (notBlank(c.getMemoryOverview())) { sb.append("## 关系记忆\n").append(c.getMemoryOverview()).append("\n"); any = true; }
        if (notBlank(c.getMemoryTimeline())) { sb.append("## 时间线\n").append(c.getMemoryTimeline()).append("\n"); any = true; }
        if (notBlank(c.getMemorySweet())) { sb.append("## 甜蜜瞬间\n").append(c.getMemorySweet()).append("\n"); any = true; }
        if (notBlank(c.getMemoryInteraction())) { sb.append("## 互动模式\n").append(c.getMemoryInteraction()).append("\n"); any = true; }
        return any ? sb.toString() : "";
    }

    private void appendLayer(StringBuilder sb, String title, String content) {
        if (notBlank(content)) {
            sb.append("## ").append(title).append("\n").append(content).append("\n");
        }
    }

    private boolean notBlank(String s) {
        return StrUtil.isNotBlank(s);
    }
}
