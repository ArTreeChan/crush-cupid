package cn.yzfy.crushcupidserver.skill;

import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 军师模式（Advisor Mode）子命令注册表与调用。
 * <p>
 * 军师模式定位：模拟模式 = 「你和 ta 说话」；军师模式 = 「军师帮你分析怎么和 ta 说话」，
 * 目标是帮助用户从「依赖 AI 模拟」走向「在现实中行动」。
 * 每个子命令对应远端 skill 仓库 {@code prompts/advisor_*.md} 模板，本地维护一份触发词/说明，
 * 调用时装载模板 -> 注入变量 -> 让 LLM 以「军师」角色产出策略。
 */
@Service
@RequiredArgsConstructor
public class SkillAdvisorService {

    /** 军师固定人设（所有子命令必须保持） */
    private static final String ADVISOR_PERSONA = """
            ## 军师角色设定
            - 毒舌但靠谱，直球不绕弯，不灌鸡汤
            - 站在用户这边，但该泼冷水时绝不含糊
            - 所有建议必须具体、可执行，拒绝泛泛而谈
            - 检测到用户过度沉溺时，主动提醒回归现实
            ## 输出风格
            - 中文口语化，带点毒舌和幽默
            - 分点回答，每点附带可执行建议
            - 每次回复末尾附「军师总结」（一句话核心建议）
            ## 反例（禁止）
            - "你要相信爱情是美好的" → 灌鸡汤，零分
            - "放轻松，顺其自然就好" → 泛泛而谈，零分
            - "联系双方要认真复盘这段关系呢" → 说废话
            """;

    /** 军师子命令注册表：trigger 触发词、title 标题、description 说明、promptName 远端模板、是否强制绑定 crush */
    private static final List<AdvisorDescriptor> DESCRIPTORS = List.of(
            new AdvisorDescriptor("advisor", "/advisor", "自由咨询", "开启军师对话，可自由咨询感情问题", "advisor", false),
            new AdvisorDescriptor("report", "/advisor report", "关系报告", "整合聊天记录、互动频率、信号分析，生成当前关系进展报告", "advisor_report", true),
            new AdvisorDescriptor("strategy", "/advisor strategy", "策略制定", "基于当前进展阶段，推荐具体的下一步行动", "advisor_strategy", true),
            new AdvisorDescriptor("prep", "/advisor prep", "行动前准备", "约会/聊天前的战术准备：话题清单、雷区提醒、穿搭建议", "advisor_prep", false),
            new AdvisorDescriptor("analyze", "/advisor analyze", "互动复盘", "用户贴入聊天记录，军师解读对方信号", "advisor_analyze", false),
            new AdvisorDescriptor("confession", "/advisor confession", "告白规划", "制定告白策略：时机、方式、话术、备选方案", "advisor_confession", false),
            new AdvisorDescriptor("reality", "/advisor reality", "现实检验", "客观评估暗恋健康程度，防止过度沉溺", "advisor_reality", false));

    private final SkillCatalogService skillCatalogService;
    private final cn.yzfy.crushcupidserver.config.ChatModelRegistry chatModelRegistry;

    /** 列出所有军师子命令描述。 */
    public List<AdvisorDescriptor> listDescriptors() {
        return DESCRIPTORS;
    }

    /**
     * 按子命令名取其描述；不存在返回 null。
     */
    public AdvisorDescriptor getDescriptor(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        return DESCRIPTORS.stream()
                .filter(d -> d.name().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 调用军师子命令，让 LLM 以军师角色针对用户问题产出回复（非流式单条文本）。
     *
     * @param name        子命令名（advisor / report / strategy / prep / analyze / confession / reality）
     * @param question    用户输入/粘贴的聊天记录或问题
     * @param contextText 额外上下文（如 crush 画像/记忆摘要），可为空
     * @return LLM 产出文本
     */
    public String invoke(String name, String question, String contextText) {
        AdvisorDescriptor desc = getDescriptor(name);
        if (desc == null) {
            throw BizException.badRequest("未知的军师子命令：" + name + "（可选：" + joinedNames() + "）");
        }
        String template = loadTemplate(desc.promptName());
        Map<String, String> variables = Map.of(
                "question", StrUtil.blankToDefault(question, desc.title() + "，请给出建议"),
                "context", StrUtil.blankToDefault(contextText, "（暂无更多上下文）"));

        StringBuilder system = new StringBuilder();
        system.append("你是一名暗恋军师。");
        system.append(ADVISOR_PERSONA);
        if (StrUtil.isNotBlank(template)) {
            system.append("\n\n## 本次任务模板\n").append(template);
        }

        String user = "问题/材料：\n" + variables.get("question")
                + "\n\n上下文：\n" + variables.get("context");

        ChatModel chatModel = chatModelRegistry.getDefault();
        var response = chatModel.call(new Prompt(List.of(
                new SystemMessage(system.toString()),
                new UserMessage(user))));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new BizException("模型返回为空");
        }
        String content = response.getResult().getOutput().getText();
        if (StrUtil.isBlank(content)) {
            throw new BizException("模型返回为空");
        }
        return content.trim();
    }

    /** 军师固定人设（供子命令 / 报告生成复用） */
    public String publicPersonaSnippet() {
        return ADVISOR_PERSONA;
    }

    /**
     * 组装军师模式的完整系统提示（供对话页军师开关流式注入使用）：
     * 军师人设 + 可选注入的 skill prompt 任务。返回文本由调用方塞进 system。
     */
    public String advisorSystemPrompt(String skillPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("你现在是「暗恋军师」，不是暗恋对象本人。");
        sb.append("目标：帮用户分析「怎么和 ta 说话、要不要行动」，而不是替用户模拟对话。\n");
        sb.append(ADVISOR_PERSONA);
        if (StrUtil.isNotBlank(skillPrompt)) {
            sb.append("\n\n## 本次任务要求\n").append(skillPrompt.trim()).append("\n");
        }
        return sb.toString();
    }

    private String loadTemplate(String promptName) {
        try {
            String t = skillCatalogService.getPrompt(promptName);
            return t == null ? "" : t;
        } catch (Exception e) {
            return "";
        }
    }

    private String joinedNames() {
        return String.join(" / ", DESCRIPTORS.stream().map(AdvisorDescriptor::name).toList());
    }

    /** 军师子命令描述（不可变记录） */
    public record AdvisorDescriptor(String name, String trigger, String title, String description, String promptName, boolean requiresCrush) {
    }
}
