package cn.yzfy.crushcupidserver.skill;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 目录服务：解析并暴露远端 SKILL.md 的元信息、可用 prompt 与安全边界。
 */
@Service
@RequiredArgsConstructor
public class SkillCatalogService {

    private static final Pattern PROMPT_REF = Pattern.compile("prompts/([a-z_0-9]+)\\.md");
    private static final Pattern FM_ENTRY = Pattern.compile("(?m)^([a-zA-Z-]+):\\s*(.*)$");

    private static final String SAFETY_FALLBACK = """
            安全边界：
            1. 仅用于个人情感分析，不用于骚扰、跟踪或侵犯他人隐私
            2. 不主动联系真人：对话模拟不会替代真实沟通
            3. 不鼓励纠缠：如用户表现出不健康的执念，温和提醒并建议寻求专业帮助
            4. 保持朋友以上恋人未满的分寸感
            """;

    private final CachingSkillResourceClient client;
    private final PromptResolver promptResolver;

    public String getSkillMarkdown() {
        return client.fetch("SKILL.md");
    }

    public SkillMeta getSkillMeta() {
        SkillMeta meta = new SkillMeta();
        Matcher m = FM_ENTRY.matcher(extractFrontmatter(getSkillMarkdown()));
        while (m.find()) {
            String key = m.group(1).trim();
            String value = m.group(2).trim();
            switch (key) {
                case "name" -> meta.setName(value);
                case "description" -> meta.setDescription(value);
                case "version" -> meta.setVersion(value);
                case "argument-hint" -> meta.setArgumentHint(value);
                case "user-invocable" -> meta.setUserInvocable(Boolean.parseBoolean(value));
                default -> {
                }
            }
        }
        return meta;
    }

    public List<String> listPrompts() {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = PROMPT_REF.matcher(getSkillMarkdown());
        while (m.find()) {
            names.add(m.group(1));
        }
        return new ArrayList<>(names);
    }

    public String getPrompt(String name) {
        return client.fetch("prompts/" + name + ".md");
    }

    public String getPrompt(String name, Map<String, String> variables) {
        return promptResolver.resolve(getPrompt(name), variables);
    }

    /**
     * 远端 SKILL.md 的「安全边界」章节；失败时回退到内置规则。
     */
    public String getSafetyRules() {
        try {
            String section = extractSection(getSkillMarkdown(), "安全边界");
            if (StrUtil.isNotBlank(section)) {
                return section;
            }
        } catch (Exception ignored) {
            // 网络不可用时回退
        }
        return SAFETY_FALLBACK;
    }

    private String extractFrontmatter(String md) {
        if (md == null) {
            return "";
        }
        String[] parts = md.split("---", 3);
        return parts.length >= 2 ? parts[1] : "";
    }

    private String extractSection(String md, String title) {
        if (md == null) {
            return null;
        }
        int idx = md.indexOf("## " + title);
        if (idx < 0) {
            return null;
        }
        int start = md.indexOf('\n', idx);
        if (start < 0) {
            return null;
        }
        int end = md.indexOf("\n---", start);
        if (end < 0) {
            end = md.indexOf("\n## ", start);
        }
        if (end < 0) {
            end = md.length();
        }
        return md.substring(start, end).trim();
    }
}
