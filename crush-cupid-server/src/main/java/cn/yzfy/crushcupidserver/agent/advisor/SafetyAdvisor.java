package cn.yzfy.crushcupidserver.agent.advisor;

import cn.yzfy.crushcupidserver.skill.SkillCatalogService;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.stereotype.Component;

/**
 * 安全边界 advisor：注入远端 SKILL.md 的「安全边界 / Layer 0 硬规则」。
 */
@Component
public class SafetyAdvisor extends AbstractPromptAdvisor {

    private final SkillCatalogService skillCatalogService;

    public SafetyAdvisor(SkillCatalogService skillCatalogService) {
        this.skillCatalogService = skillCatalogService;
    }

    @Override
    protected int order() {
        return 100;
    }

    @Override
    protected String resolveSystemText(ChatClientRequest request) {
        return skillCatalogService.getSafetyRules();
    }
}
