package cn.yzfy.crushcupidserver.agent;

import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.config.ChatModelRegistry;
import cn.yzfy.crushcupidserver.model.entity.Crush;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @className SourceAnalysisService
 * @description 原材料「LLM 理解解析」：上传的 pdf/docx/图片/文本在抽取原文后，再交给默认 LLM
 * 推理提炼结构化理解，结果以 JSON 落地 {@link cn.yzfy.crushcupidserver.model.entity.ChatSource#getRawAnalysis()}
 * （列类型为 json，故必须保证输出合法 JSON）。
 * <p>
 * JSON 结构：facts / portraitClues / keyPoints / emotionSignals / risks。
 * <p>
 * 降级策略：LLM 未配置 / 调用失败 / 返回非法 JSON 时，回退为 {@code {"raw": 原文}}，
 * 保证始终是合法 JSON，绝不阻断上传主流程。
 * @author crush-cupid
 * @code service
 * @createTime 2026-09-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceAnalysisService {

    private static final int MAX_INPUT = 20000;

    private final ChatModelRegistry chatModelRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 让 LLM 理解并提炼附件原文，返回结构化 JSON 字符串。
     *
     * @param crush      所属暗恋对象（可选）
     * @param fileName   文件名（可选）
     * @param rawContent 已抽取的原始文本（图片 OCR / pdf 抽取 / 文本读取）
     * @return 合法 JSON 字符串；任何失败回退 {@code {"raw": 原文}}，绝不抛异常
     */
    public String analyze(Crush crush, String fileName, String rawContent) {
        String raw = rawContent == null ? "" : rawContent.trim();
        if (StrUtil.isBlank(raw)) {
            return wrapRaw("");
        }
        String truncated = raw.length() > MAX_INPUT ? raw.substring(0, MAX_INPUT) + "\n…（超长已截断）" : raw;

        String system = "你是一名暗恋分析助手，负责读取用户上传的「原材料」（聊天记录、日记、照片/文档解读等）"
                + "并提炼其中关于暗恋对象的可靠信息。\n"
                + "只依据给定材料内容，不得编造材料里没有的信息；不确定的内容标注『不确定』。\n"
                + "请严格输出单个合法 JSON 对象（不要 Markdown、不要多余文字），键固定为：\n"
                + "facts（客观事实/时间/事件/人物关系）、portraitClues（性格/兴趣/喜好/习惯等画像线索）、"
                + "keyPoints（对推进关系最有价值的关键点）、emotionSignals（可推断的情绪与态度）、"
                + "risks（明显矛盾或需要注意的风险）。\n"
                + "值为中文自然语言字符串，无内容时返回空字符串。";
        String user = "暗恋对象：" + (crush != null && StrUtil.isNotBlank(crush.getName())
                ? crush.getName() : "（未知）")
                + "\n材料文件名：" + (StrUtil.isBlank(fileName) ? "（未知）" : fileName)
                + "\n\n材料原文如下：\n" + truncated;

        try {
            ChatModel chatModel = chatModelRegistry.getDefault();
            if (chatModel == null) {
                log.warn("LLM 解析原材料跳过：无默认 ChatModel");
                return wrapRaw(raw);
            }
            var response = chatModel.call(new Prompt(List.of(
                    new SystemMessage(system),
                    new UserMessage(user))));
            if (response == null || response.getResult() == null
                    || response.getResult().getOutput() == null) {
                log.warn("LLM 解析原材料返回为空，回退原文");
                return wrapRaw(raw);
            }
            String content = response.getResult().getOutput().getText();
            JsonNode node = parseJson(content);
            if (node != null && node.isObject()) {
                return objectMapper.writeValueAsString(node);
            }
            log.warn("LLM 解析原材料返回非法 JSON，回退原文");
            return wrapRaw(raw);
        } catch (Exception e) {
            log.warn("LLM 解析原材料失败，回退原文：{}", e.getMessage());
            return wrapRaw(raw);
        }
    }

    /** 从 LLM 输出中裁剪出合法 JSON 对象，解析失败返回 null */
    private JsonNode parseJson(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String t = text.trim();
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            t = t.substring(start, end + 1);
        }
        try {
            return objectMapper.readTree(t);
        } catch (Exception e) {
            log.debug("LLM 输出无法解析为 JSON：{}", e.getMessage());
            return null;
        }
    }

    /** 回退包装：始终输出合法 JSON */
    private String wrapRaw(String raw) {
        try {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("raw", raw);
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return "{\"raw\":\"\"}";
        }
    }
}
