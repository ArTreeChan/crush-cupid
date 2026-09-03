package cn.yzfy.crushcupidserver.agent;

import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.exception.BizException;
import cn.yzfy.crushcupidserver.model.entity.AiProvider;
import cn.yzfy.crushcupidserver.model.entity.ChatSource;
import cn.yzfy.crushcupidserver.model.entity.Crush;
import cn.yzfy.crushcupidserver.model.vo.RelationshipResultVO;
import cn.yzfy.crushcupidserver.service.AiProviderService;
import cn.yzfy.crushcupidserver.service.ChatSourceService;
import cn.yzfy.crushcupidserver.service.CrushService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 关系分析流水线：把暗恋对象的聊天记录交给「她不一样（she-love-me）」分析引擎
 * （Python 统计脚本 + LLM 深度鉴定），生成全量统计、鉴定结果与 HTML 报告。
 * <p>
 * 流程：拼接 ChatSource 原文 → convert_weflow_html.py 转 messages.json →
 * stats_analyzer.py 全量统计 → build_chat_history.py 分层采样 →
 * LLM 依据分析框架输出 analysis.json → generate_html_report.py 生成报告。
 * <p>
 * she-love-me 目录默认取相对路径 {@code ../she-love-me}（相对后端运行目录），
 * 可用 {@code -Dshe.love.me.dir=...} 覆盖；工作产物落在
 * {@code D:/uploads/relationship/<crushId>/}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationshipService {

    private static final String WORK_ROOT = "D:/uploads/relationship/";
    private static final String REPORT_URL_PREFIX = "/api/uploads/relationship/";
    private static final int MAX_CHAT_HISTORY_CHARS = 9000;
    private static final int SCRIPT_TIMEOUT_SECONDS = 300;
    /** 分析结果指纹文件：记录最近一次分析对应的输入指纹，聊天记录未变时直接复用旧结果 */
    private static final String FINGERPRINT_FILE = ".analysis_fingerprint";

    private final CrushService crushService;
    private final ChatSourceService chatSourceService;
    private final AiProviderService aiProviderService;
    private final ObjectMapper objectMapper;

    /** 执行完整关系分析 */
    public RelationshipResultVO analyze(Long crushId) {
        Crush crush = crushService.getById(crushId);
        if (crush == null) {
            throw BizException.notFound("未找到暗恋对象 id=" + crushId);
        }

        Path dir = Path.of(WORK_ROOT, String.valueOf(crushId));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BizException("创建工作目录失败：" + e.getMessage());
        }
        Path html = dir.resolve("messages.html");
        Path messages = dir.resolve("messages.json");
        Path stats = dir.resolve("stats.json");
        Path history = dir.resolve("chat_history.txt");
        Path analysis = dir.resolve("analysis.json");
        Path reports = dir.resolve("reports");

        // 1. 拼接所有原材料原文（HTML / 文本）
        String raw = chatSourceService.listByCrushId(crushId).stream()
                .map(ChatSource::getContent)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("\n"));
        if (StrUtil.isBlank(raw)) {
            throw BizException.badRequest("还没有聊天记录，请先为 ta 导入聊天记录/文件");
        }

        // 1.5 缓存命中：聊天记录 + crush 属性未变，直接复用上一次的分析结果（秒回，不重跑）
        RelationshipResultVO cached = tryLoadCached(crush, dir, raw, stats, analysis, reports);
        if (cached != null) {
            log.info("关系分析 [{}] 命中缓存，直接返回历史结果", crushId);
            return cached;
        }

        write(html, raw);

        // 2. HTML → messages.json
        runPython("convert_weflow_html.py",
                "--input", html.toString(),
                "--contact", StrUtil.blankToDefault(crush.getName(), "对方"),
                "--output", messages.toString());

        // 3. 全量统计
        runPython("stats_analyzer.py",
                "--input", messages.toString(),
                "--output", stats.toString());

        // 4. 分层采样（全量窗口）
        runPython("build_chat_history.py",
                "--input", messages.toString(),
                "--output", history.toString());

        // 5. LLM 深度鉴定 → analysis.json（失败时降级：保留统计，跳过报告）
        RelationshipResultVO vo = new RelationshipResultVO();
        vo.setCrushId(crushId);
        vo.setContact(crush.getName());
        try {
            String analysisJson = analyzeWithLlm(crush, stats, history);
            write(analysis, analysisJson);
        } catch (BizException e) {
            log.warn("AI 深度鉴定失败，降级返回统计：{}", e.getMessage());
            vo.setErrorMessage("AI 深度鉴定暂不可用（" + e.getMessage() + "），已为你保留全量统计");
            fillStatsInto(vo, stats);
            return vo;
        }

        // 6. 生成 HTML 报告
        String reportOutput = runPython("generate_html_report.py",
                "--stats", stats.toString(),
                "--analysis", analysis.toString(),
                "--contact", StrUtil.blankToDefault(crush.getName(), "对方"),
                "--output", reports.toString());
        String reportPath = extractReportPath(reportOutput, reports);
        vo.setStats(readJson(stats));
        vo.setAnalysis(readJson(analysis));
        vo.setReportUrl(REPORT_URL_PREFIX + crushId + "/reports/" + Path.of(reportPath).getFileName());
        fillStatsInto(vo, stats);
        // 记录本次输入指纹，供下次缓存命中
        writeFingerprint(dir, fingerprintOf(raw, crush));
        return vo;
    }

    /** 尝试加载缓存结果：指纹匹配且 analysis/报告齐全时返回历史结果，否则返回 null */
    private RelationshipResultVO tryLoadCached(Crush crush, Path dir, String raw,
                                               Path stats, Path analysis, Path reports) {
        if (!Files.isRegularFile(analysis)) {
            return null;
        }
        Path meta = dir.resolve(FINGERPRINT_FILE);
        if (!Files.isRegularFile(meta)) {
            return null;
        }
        String expected = fingerprintOf(raw, crush);
        if (!expected.equals(readQuietly(meta).trim())) {
            return null;
        }
        if (!Files.isRegularFile(stats)) {
            return null;
        }
        Path report = latestHtmlReport(reports);
        if (report == null) {
            return null;
        }
        RelationshipResultVO vo = new RelationshipResultVO();
        vo.setCrushId(crush.getId());
        vo.setContact(crush.getName());
        vo.setCached(true);
        vo.setStats(readJson(stats));
        vo.setAnalysis(readJson(analysis));
        vo.setReportUrl(REPORT_URL_PREFIX + crush.getId() + "/reports/" + report.getFileName());
        fillStatsInto(vo, stats);
        return vo;
    }

    /** 基于聊天记录原文 + crush 关键属性计算输入指纹 */
    private String fingerprintOf(String raw, Crush crush) {
        String input = raw
                + "\n###" + StrUtil.blankToDefault(crush.getName(), "")
                + "\n" + StrUtil.blankToDefault(crush.getRelationshipStatus(), "")
                + "\n" + StrUtil.blankToDefault(crush.getImpression(), "");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BizException("指纹计算失败：" + e.getMessage());
        }
    }

    private void writeFingerprint(Path dir, String fingerprint) {
        write(dir.resolve(FINGERPRINT_FILE), fingerprint);
    }

    /** 取 reports 目录下最新生成的 HTML 报告，没有则返回 null */
    private Path latestHtmlReport(Path reportsDir) {
        if (!Files.isDirectory(reportsDir)) {
            return null;
        }
        try (var stream = Files.list(reportsDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".html"))
                    .max((a, b) -> {
                        try {
                            return Long.compare(
                                    Files.getLastModifiedTime(a).toMillis(),
                                    Files.getLastModifiedTime(b).toMillis());
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .orElse(null);
        } catch (IOException e) {
            log.warn("扫描报告目录失败 {}：{}", reportsDir, e.getMessage());
            return null;
        }
    }

    /** 把 stats.json 里的指数/消息数填充到 VO */
    private void fillStatsInto(RelationshipResultVO vo, Path stats) {
        JsonNode statsNode = readJson(stats);
        vo.setStats(statsNode);
        JsonNode scores = statsNode == null ? null : statsNode.get("scores");
        if (scores != null) {
            vo.setInitiative(intOf(scores, "simp_index"));
            vo.setLovedIndex(intOf(scores, "loved_index"));
            vo.setColdIndex(intOf(scores, "cold_index"));
        }
        JsonNode basic = statsNode == null ? null : statsNode.get("basic");
        if (basic != null && basic.get("total_messages") != null) {
            vo.setTotalMessages(basic.get("total_messages").asInt());
        }
    }

    /** LLM 深度鉴定：把精简分析框架 + 原版输出 schema + 统计 + 采样文本交给默认对话大模型（OpenAI 兼容直连，可控超时），输出 analysis.json */
    private String analyzeWithLlm(Crush crush, Path stats, Path history) {
        String schema = readQuietly(referencesDir().resolve("report-schema.md"));
        String system = """
                你是「她不一样」的首席分析师兼关系心理顾问，融合专业恋爱心理学框架，
                帮助用户从聊天记录中看清这段关系真正在走向哪里。
                必须只输出一个合法 JSON 对象（不要 markdown 代码块、不要任何解释文字），
                严格遵循下方 report-schema 定义的完整结构；证据不足的字段按 schema 的可空字段规则处理。

                【分析顺序】F 识人（对方的核心需求/依恋风格/防御模式）→ A 依恋类型 →
                B Sternberg 三角 → C 危险预警 → E 关系阶段 → D 军师建议 → G 行动路径。

                【5 条铁律】
                1. 无证据不诊断：所有心理学推断必须引用带时间戳的原话作为锚点。
                2. 高亮预警优先：危险预警仅当量化条件与文本条件同时满足时触发。
                3. 先叙事后框架：先描述你「看到」的画面，再引入理论名词。
                4. 防御语言是金矿：「不合适」「随便」「来者不拒」永远追问：这句话保护了什么？想让对方做什么？
                5. 证据不足留白：partner_attachment / core_fear / trauma_bonding / future_faking /
                   fatal_mistake / advancement_path / pursue_distance_loop 若无充分证据，输出
                   {"value": null, "evidence_level": "insufficient", "reason": "...", "observable_signals": [...]}
                   而非强行推断。

                ===== 输出 schema（必须严格遵循）=====
                %s
                """.formatted(schema);

        String statsText = readQuietly(stats);
        String historyText = readQuietly(history);
        if (historyText.length() > MAX_CHAT_HISTORY_CHARS) {
            historyText = historyText.substring(0, MAX_CHAT_HISTORY_CHARS) + "\n…（超长已截断）";
        }
        String user = """
                暗恋对象：%s
                与我的关系状态：%s
                印象：%s

                ===== 全量统计（stats.json）=====
                %s

                ===== 分层采样聊天记录（关键窗口）=====
                %s
                """.formatted(
                StrUtil.blankToDefault(crush.getName(), "未知"),
                StrUtil.blankToDefault(crush.getRelationshipStatus(), "未知"),
                StrUtil.blankToDefault(crush.getImpression(), "未知"),
                statsText, historyText);

        // 遍历所有 chat 供应商（默认优先）：429/失败自动降级到下一个，
        // AMD DeepSeek 免费模型并发紧张时自动切到 AMD Qwen 等备用供应商。
        List<AiProvider> providers = listChatProviders();
        List<String> errors = new ArrayList<>();
        for (AiProvider provider : providers) {
            try {
                String content = callProvider(provider, system, user);
                JsonNode parsed = parseJson(content);
                if (parsed == null || !parsed.isObject()) {
                    String snippet = content == null ? "(null)" : content.substring(0, Math.min(600, content.length()));
                    log.warn("AI 鉴定供应商 [{}] 返回非法 JSON，前 600 字符：{}", provider.getName(), snippet.replace("\n", "\\n"));
                    throw new BizException("AI 鉴定返回非法 JSON");
                }
                return objectMapper.writeValueAsString(parsed);
            } catch (BizException e) {
                errors.add(provider.getName() + "：" + e.getMessage());
                log.warn("AI 鉴定供应商 [{}] 失败，尝试下一个：{}", provider.getName(), e.getMessage());
            } catch (IOException e) {
                errors.add(provider.getName() + "：" + e.getMessage());
                log.warn("AI 鉴定供应商 [{}] 序列化失败：{}", provider.getName(), e.getMessage());
            }
        }
        throw new BizException("所有对话供应商均无法完成 AI 鉴定：" + String.join("；", errors));
    }

    /** 调用单个供应商完成 AI 鉴定；429 时短重试，仍失败则抛出供上层降级 */
    private String callProvider(AiProvider provider, String system, String user) throws BizException {
        String base = provider.getBaseUrl();
        if (base == null || base.isBlank()) {
            throw new BizException("供应商未配置 baseUrl");
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = base + "/chat/completions";

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", provider.getModel());
            body.put("temperature", provider.getTemperature() == null ? 0.6 : provider.getTemperature());
            body.put("max_tokens", 5000); // 给足输出空间，避免长 JSON 被截断导致解析失败
            Map<String, String> sysMsg = new LinkedHashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", system);
            Map<String, String> userMsg = new LinkedHashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", user);
            body.put("messages", List.of(sysMsg, userMsg));

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(240)) // 单次请求 240s：AMD 免费模型长输出较慢，给足时间再降级
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + StrUtil.blankToDefault(provider.getApiKey(), ""))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .proxy(HttpClient.Builder.NO_PROXY) // 强制直连，避免被系统/环境代理转发拖慢
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            // 429 并发/限流时有限重试（免费模型并发紧张，短暂等待后仍 429 则交给上层换供应商）
            HttpResponse<String> response = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 429 || attempt == 3) {
                    break;
                }
                log.warn("AI 鉴定接口 429（{} 第 {} 次），12s 后重试", provider.getName(), attempt);
                try {
                    Thread.sleep(12_000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new BizException("AI 鉴定请求被中断");
                }
            }

            if (response.statusCode() >= 400) {
                log.warn("AI 鉴定接口 [{}] 返回 {}：{}", provider.getName(), response.statusCode(), firstLine(response.body()));
                throw new BizException("接口返回 " + response.statusCode() + "：" + firstLine(response.body()));
            }
            JsonNode node = objectMapper.readTree(response.body());
            JsonNode content = node.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new BizException("AI 鉴定返回为空");
            }
            return content.asText();
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException("AI 鉴定请求失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("AI 鉴定请求被中断");
        }
    }

    /**
     * 所有 chat 供应商，按"AI 鉴定可用性"排序：
     * 官方 DashScope 通义（稳定、能输出长 JSON、无 reasoning 截断问题）优先 →
     * 默认供应商（AMD DeepSeek，空闲时 80s 左右完成）→ AMD Qwen（DeepSeek 高峰 429 时的备用）→ 其余。
     * 保证 AMD 免费模型高峰限流/超时时，关系分析能直接用通义兜底出结果。
     */
    private List<AiProvider> listChatProviders() {
        List<AiProvider> all = aiProviderService.list();
        List<AiProvider> stable = new ArrayList<>();
        List<AiProvider> others = new ArrayList<>();
        AiProvider defaultOne = null;
        AiProvider amdQwen = null;
        for (AiProvider p : all) {
            if (!"chat".equalsIgnoreCase(StrUtil.blankToDefault(p.getType(), "chat"))) {
                continue;
            }
            String base = StrUtil.blankToDefault(p.getBaseUrl(), "");
            // 官方 DashScope（通义千问），非 AMD 免费网关：稳定可输出长 JSON，优先用于 AI 鉴定
            if (base.contains("dashscope.aliyuncs.com") && !base.contains("developer.amd.com.cn")) {
                stable.add(p);
                continue;
            }
            if (Boolean.TRUE.equals(p.getIsDefault())) {
                defaultOne = p;
                continue;
            }
            String model = StrUtil.blankToDefault(p.getModel(), "");
            if (amdQwen == null && model.toUpperCase().contains("QWEN")) {
                amdQwen = p;
                continue;
            }
            others.add(p);
        }
        List<AiProvider> ordered = new ArrayList<>();
        ordered.addAll(stable);
        if (defaultOne != null) {
            ordered.add(defaultOne);
        }
        if (amdQwen != null) {
            ordered.add(amdQwen);
        }
        ordered.addAll(others);
        if (ordered.isEmpty()) {
            throw new BizException("未配置对话大模型供应商，无法进行 AI 深度鉴定");
        }
        return ordered;
    }

    // ---------------- 工具方法 ----------------

    private Path scriptsDir() {
        String base = System.getProperty("she.love.me.dir", "../she-love-me");
        Path scripts = Path.of(base).resolve("scripts");
        if (!Files.isDirectory(scripts)) {
            throw new BizException("she-love-me 脚本目录不存在（可在启动参数 -Dshe.love.me.dir 指定）："
                    + scripts.toAbsolutePath());
        }
        return scripts;
    }

    private Path referencesDir() {
        String base = System.getProperty("she.love.me.dir", "../she-love-me");
        return Path.of(base).resolve(".agents/skills/she-love-me/references");
    }

    private String runPython(String script, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("py");
        cmd.add(scriptsDir().resolve(script).toString());
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = p.waitFor(SCRIPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new BizException("脚本执行超时：" + script);
            }
            if (p.exitValue() != 0) {
                log.warn("脚本 {} 执行失败 exit={} out={}", script, p.exitValue(), out);
                throw new BizException("脚本执行失败（" + script + "）：" + firstLine(out));
            }
            return out;
        } catch (IOException e) {
            throw new BizException("调用脚本失败（" + script + "）：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("脚本调用被中断：" + script);
        }
    }

    private void write(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException("写入文件失败：" + e.getMessage());
        }
    }

    private String readQuietly(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("读取文件失败 {}：{}", path, e.getMessage());
            return "";
        }
    }

    private JsonNode readJson(Path path) {
        try {
            return objectMapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("读取 JSON 失败 {}：{}", path, e.getMessage());
            return null;
        }
    }

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
            return null;
        }
    }

    private Integer intOf(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asInt();
    }

    private String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String t = text.trim();
        int idx = t.indexOf('\n');
        return idx > 0 ? t.substring(0, idx) : t;
    }

    /** 从 generate_html_report.py 的 stdout JSON 中提取报告文件路径 */
    private String extractReportPath(String output, Path reportsDir) {
        try {
            JsonNode node = objectMapper.readTree(output);
            String path = node.path("path").asText("");
            if (StrUtil.isNotBlank(path)) {
                return path;
            }
        } catch (Exception ignored) {
        }
        // 回退：取 reports 目录下最新的 html
        try (var stream = Files.list(reportsDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".html"))
                    .max((a, b) -> {
                        try {
                            return Long.compare(
                                    Files.getLastModifiedTime(a).toMillis(),
                                    Files.getLastModifiedTime(b).toMillis());
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .map(Path::toString)
                    .orElseThrow(() -> new BizException("HTML 报告未生成"));
        } catch (IOException e) {
            throw new BizException("HTML 报告未生成：" + e.getMessage());
        }
    }
}
