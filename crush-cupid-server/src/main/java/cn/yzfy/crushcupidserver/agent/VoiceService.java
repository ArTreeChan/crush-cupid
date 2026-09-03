package cn.yzfy.crushcupidserver.agent;

import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.config.VoiceProviderRegistry;
import cn.yzfy.crushcupidserver.exception.BizException;
import cn.yzfy.crushcupidserver.model.entity.AiProvider;
import com.alibaba.cloud.ai.dashscope.api.DashScopeAudioSpeechApi;
import com.alibaba.cloud.ai.dashscope.audio.tts.DashScopeAudioSpeechOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

/**
 * @className VoiceService
 * @description 语音合成服务（CosyVoice 系列）。
 * <p>
 * 优先使用数据库中 type=voice 且 is_default=true 的语音供应商（运行时可增删改，无需重启），
 * 没有配置语音供应商时回退 yml 里的 spring.ai.dashscope 配置。
 * <p>
 * 专属音色通过 {@link #designVoice(String, String)} 调用百炼 voice-enrollment（声音设计）创建，
 * 每个 crush 可用一段人设描述生成专属声线，返回的 voice_id 即合成时的 voice 参数。
 * @author 一朝风月
 * @code service
 * @createTime 2026-08-27
 */
@Slf4j
@Service
public class VoiceService {

    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com";
    private static final String DEFAULT_WEBSOCKET_URL = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
    /** 声音设计（voice-enrollment）接口地址 */
    private static final String VOICE_DESIGN_PATH = "/api/v1/services/audio/tts/customization";

    @Value("${spring.ai.dashscope.api-key:}")
    private String ymlApiKey;

    @Value("${spring.ai.dashscope.audio.speech.default-options.model:cosyvoice-v3-flash}")
    private String ymlDefaultModel;

    @Value("${spring.ai.dashscope.audio.speech.default-options.voice:}")
    private String ymlDefaultVoice;

    private final ObjectMapper objectMapper;
    private final VoiceProviderRegistry voiceProviderRegistry;

    /** yml 回退用的 WebSocket 语音合成 API */
    private DashScopeAudioSpeechApi ymlAudioApi;
    /** yml 回退用的声音设计 REST 客户端 */
    private RestClient ymlDesignClient;

    public VoiceService(ObjectMapper objectMapper, VoiceProviderRegistry voiceProviderRegistry) {
        this.objectMapper = objectMapper;
        this.voiceProviderRegistry = voiceProviderRegistry;
    }

    @PostConstruct
    public void init() {
        if (StrUtil.isBlank(ymlApiKey)) {
            log.warn("DASHSCOPE_API_KEY 未配置，yml 回退语音合成不可用（可在「大模型 API」页面配置语音供应商）");
            return;
        }
        try {
            this.ymlAudioApi = DashScopeAudioSpeechApi.builder()
                    .baseUrl(DEFAULT_BASE_URL)
                    .websocketUrl(DEFAULT_WEBSOCKET_URL)
                    .apiKey(new SimpleApiKey(ymlApiKey))
                    .build();
            this.ymlDesignClient = RestClient.builder()
                    .baseUrl(DEFAULT_BASE_URL + VOICE_DESIGN_PATH)
                    .defaultHeader("Authorization", "Bearer " + ymlApiKey)
                    .build();
            log.info("CosyVoice yml 回退语音合成就绪：model={}，默认音色={}", ymlDefaultModel, StrUtil.blankToDefault(ymlDefaultVoice, "未配置"));
        } catch (Exception e) {
            this.ymlAudioApi = null;
            this.ymlDesignClient = null;
            log.warn("CosyVoice yml 回退语音初始化失败：{}", e.getMessage());
        }
    }

    /**
     * 合成语音（WebSocket 非流式：发送完整文本，收集全部音频帧）。
     * 优先用数据库默认语音供应商，没有则回退 yml 配置。
     *
     * @param text  待合成文本（不能为空）
     * @param voice 音色 ID（声音设计/复刻产生的 voice_id），空则报错提示去暗恋对象页面创建
     * @param instruction 风格指令（instruction）：控制情感、语气、语速、性格，最大 100 字符，可空
     * @return mp3 字节流
     */
    public byte[] synthesize(String text, String voice, String instruction) {
        if (StrUtil.isBlank(text)) {
            throw BizException.badRequest("待合成文本不能为空");
        }

        // 1. 优先用数据库默认语音供应商的 API KEY（baseUrl / model 固定用 yml 配置）
        AiProvider provider = voiceProviderRegistry.getDefaultConfig();
        DashScopeAudioSpeechApi api = voiceProviderRegistry.getDefaultApi();

        // 2. 没有语音供应商则回退 yml（apiKey + baseUrl + model 全从 yml 读）
        if (api == null) {
            if (ymlAudioApi == null) {
                throw BizException.badRequest("语音合成不可用：未配置语音供应商（请在「大模型 API」页面新增语音大模型并填入 API KEY），且 yml 未配置 DASHSCOPE_API_KEY");
            }
            api = ymlAudioApi;
        }

        // 3. 模型名从 yml 读取（源项目配置），不从语音供应商读
        String model = ymlDefaultModel;

        // 4. 音色只来自暗恋对象的 voiceId（调用方传入），不再从供应商/yml 兜底
        if (StrUtil.isBlank(voice)) {
            throw BizException.badRequest("未指定音色：请先在「暗恋对象」页面为该对象创建专属音色（声音设计），再使用语音合成");
        }
        String resolvedVoice = voice;

        long start = System.currentTimeMillis();
        DashScopeAudioSpeechOptions.Builder optionsBuilder = DashScopeAudioSpeechOptions.builder()
                .model(model)
                .voice(resolvedVoice)
                .format("mp3");

        // 5. 风格指令（instruction）：控制情感、语气、语速、性格，仅在有值时传入
        if (StrUtil.isNotBlank(instruction)) {
            String trimmed = instruction.length() > 100 ? instruction.substring(0, 100) : instruction;
            optionsBuilder.instruction(trimmed);
        }

        DashScopeAudioSpeechOptions options = optionsBuilder.build();

        byte[] audio = api.createWebSocketTask(text, options)
                .map(this::toBytes)
                .collectList()
                .map(frames -> merge(frames))
                .block(Duration.ofSeconds(60));

        if (audio == null || audio.length == 0) {
            throw new BizException("语音合成失败：未收到音频数据");
        }
        log.info("CosyVoice 合成完成：{} 字符 -> {} 字节，耗时 {}ms，供应商={}，instruction={}",
                text.length(), audio.length, System.currentTimeMillis() - start,
                provider != null ? provider.getProviderKey() : "yml-fallback",
                StrUtil.isBlank(instruction) ? "无" : instruction);
        return audio;
    }

    /**
     * 声音设计：用自然语言描述创建 v3.5-plus 专属音色。
     * 优先用数据库默认语音供应商的 apiKey/baseUrl，没有则回退 yml。
     *
     * @param voicePrompt 声音描述（如「温柔的年轻女性，语速轻快，带笑意」，≤500 字符）
     * @param previewText 预览文本（15~200 字符），空则用默认
     * @return 生成的 voice_id（用于 synthesize 的 voice 参数）
     */
    public String designVoice(String voicePrompt, String previewText) {
        AiProvider provider = voiceProviderRegistry.getDefaultConfig();
        String apiKey = provider != null ? provider.getApiKey() : null;
        String baseUrl = provider != null ? provider.getBaseUrl() : null;
        String model = provider != null ? provider.getModel() : null;

        RestClient designClient;
        if (StrUtil.isNotBlank(apiKey)) {
            String resolvedBase = StrUtil.blankToDefault(baseUrl, DEFAULT_BASE_URL);
            designClient = RestClient.builder()
                    .baseUrl(resolvedBase + VOICE_DESIGN_PATH)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .build();
        } else {
            if (ymlDesignClient == null) {
                throw BizException.badRequest("声音设计不可用：未配置语音供应商，且 yml 未配置 DASHSCOPE_API_KEY");
            }
            designClient = ymlDesignClient;
            model = ymlDefaultModel;
        }

        if (StrUtil.isBlank(voicePrompt)) {
            throw BizException.badRequest("声音描述不能为空");
        }
        String resolvedModel = StrUtil.blankToDefault(model, ymlDefaultModel);
        // 声音设计（voice_prompt 描述生成）仅 v3.5/v3 系列支持；v2 只支持上传音频的声音复刻
        if (!resolvedModel.startsWith("cosyvoice-v3")) {
            throw BizException.badRequest("当前模型 " + resolvedModel + " 不支持声音设计，请将模型切换为 cosyvoice-v3 系列后使用");
        }
        String preview = StrUtil.blankToDefault(previewText,
                "嘿，是我呀，今天有没有想我？记得按时吃饭，别总是熬夜到那么晚。");

        Map<String, Object> body = Map.of(
                "model", "voice-enrollment",
                "input", Map.of(
                        "action", "create_voice",
                        "target_model", resolvedModel,
                        "voice_prompt", voicePrompt,
                        "preview_text", preview,
                        "prefix", "crush"),
                "parameters", Map.of("sample_rate", 24000, "response_format", "mp3"));

        String resp = designClient.post()
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);
        return extractVoiceId(resp);
    }

    /** 从声音设计响应中提取 voice_id（兼容 output / data 两层包装） */
    private String extractVoiceId(String resp) {
        try {
            JsonNode root = objectMapper.readTree(resp);
            for (String wrapper : new String[]{"output", "data"}) {
                JsonNode node = root.path(wrapper).path("voice_id");
                if (!node.isMissingNode() && StrUtil.isNotBlank(node.asText())) {
                    return node.asText();
                }
            }
            // 兜底：递归找第一个 voice_id 字段
            String found = findField(root, "voice_id");
            if (found != null) {
                return found;
            }
        } catch (Exception e) {
            throw new BizException("声音设计响应解析失败：" + e.getMessage());
        }
        throw new BizException("声音设计失败：响应中无 voice_id，" + resp);
    }

    /** 递归查找指定字段的首个非空值 */
    private String findField(JsonNode node, String field) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (field.equals(e.getKey()) && e.getValue().isTextual() && StrUtil.isNotBlank(e.getValue().asText())) {
                    return e.getValue().asText();
                }
                String deep = findField(e.getValue(), field);
                if (deep != null) {
                    return deep;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String deep = findField(child, field);
                if (deep != null) {
                    return deep;
                }
            }
        }
        return null;
    }

    private byte[] toBytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private byte[] merge(java.util.List<byte[]> frames) {
        int total = frames.stream().mapToInt(f -> f.length).sum();
        byte[] merged = new byte[total];
        int pos = 0;
        for (byte[] frame : frames) {
            System.arraycopy(frame, 0, merged, pos, frame.length);
            pos += frame.length;
        }
        return merged;
    }
}
