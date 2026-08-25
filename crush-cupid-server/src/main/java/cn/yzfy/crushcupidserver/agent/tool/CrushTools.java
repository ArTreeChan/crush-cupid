package cn.yzfy.crushcupidserver.agent.tool;

import cn.yzfy.crushcupidserver.model.entity.Crush;
import cn.yzfy.crushcupidserver.model.service.CrushService;
import cn.yzfy.crushcupidserver.skill.SkillCatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具封装：把查库、远端拉 skill 等确定性动作暴露为 {@link Tool}。
 */
@Component
@RequiredArgsConstructor
public class CrushTools {

    private final CrushService crushService;
    private final SkillCatalogService skillCatalogService;
    private final ObjectMapper objectMapper;

    @Tool(description = "列出所有已创建的暗恋对象（代号 slug + 花名）")
    public String listCrushes() {
        List<Map<String, Object>> list = crushService.list().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("slug", c.getSlug());
            m.put("mbti", c.getMbti());
            m.put("relationshipStatus", c.getRelationshipStatus());
            return m;
        }).toList();
        return writeJson(list);
    }

    @Tool(description = "根据 slug 查询暗恋对象的画像（Persona 5 层）与关系记忆")
    public String getCrushProfile(@ToolParam(description = "暗恋对象代号 slug") String slug) {
        Crush c = crushService.getBySlug(slug);
        if (c == null) {
            return "未找到暗恋对象：" + slug;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", c.getName());
        m.put("slug", c.getSlug());
        m.put("mbti", c.getMbti());
        m.put("zodiac", c.getZodiac());
        m.put("occupation", c.getOccupation());
        m.put("relationshipStatus", c.getRelationshipStatus());
        m.put("impression", c.getImpression());
        m.put("persona", List.of(
                c.getPersonaLayer0(), c.getPersonaLayer1(), c.getPersonaLayer2(),
                c.getPersonaLayer3(), c.getPersonaLayer4()));
        m.put("memoryOverview", c.getMemoryOverview());
        m.put("memorySweet", c.getMemorySweet());
        m.put("memoryInteraction", c.getMemoryInteraction());
        return writeJson(m);
    }

    @Tool(description = "从 GitHub 远端加载 crush-skills 的 prompt 模板")
    public String loadSkillPrompt(@ToolParam(description = "prompt 名称，如 persona_builder / confession_simulator") String name) {
        String prompt = skillCatalogService.getPrompt(name);
        return prompt == null ? "未找到 prompt：" + name : prompt;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "序列化失败：" + e.getMessage();
        }
    }
}
