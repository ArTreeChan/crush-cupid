package cn.yzfy.crushcupidserver.config;

import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.model.entity.AiProvider;
import cn.yzfy.crushcupidserver.service.AiProviderService;
import com.alibaba.cloud.ai.dashscope.api.DashScopeAudioSpeechApi;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @className VoiceProviderRegistry
 * @description 语音大模型供应商注册中心（对标 {@link ChatModelRegistry}）。
 * 从数据库读取 type=voice 的供应商，动态构建 DashScopeAudioSpeechApi，
 * 支持运行时增删改即时生效，无需改配置 / 重启。
 * <p>
 * 默认语音供应商：数据库中 is_default=true 的语音供应商；没有则回退 yml 配置。
 * @author crush-cupid
 * @code registry
 * @createTime 2026-09-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceProviderRegistry {

    private final AiProviderService aiProviderService;

    /** 供应商代号 -> DashScopeAudioSpeechApi 实例 */
    @Getter
    private final Map<String, DashScopeAudioSpeechApi> apis = new LinkedHashMap<>();

    /** 供应商代号 -> 供应商配置（供业务侧查询 model/voice 等元信息） */
    @Getter
    private final Map<String, AiProvider> configs = new LinkedHashMap<>();

    /** 运行期默认语音供应商代号（DB is_default） */
    @Getter
    private String defaultKey;

    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * 重建语音供应商注册表：从数据库读取 type=voice 的供应商，构建对应 API 实例。
     * 增删改语音供应商后调用，即时生效。
     */
    public synchronized void reload() {
        apis.clear();
        configs.clear();

        List<AiProvider> voiceProviders = aiProviderService.lambdaQuery()
                .eq(AiProvider::getType, "voice")
                .list();

        String dbDefault = null;
        for (AiProvider p : voiceProviders) {
            try {
                // 语音大模型只配置 API KEY；baseUrl / model 固定用源项目 yml 的地址
                String apiKey = p.getApiKey();
                if (StrUtil.isBlank(apiKey)) {
                    log.warn("语音供应商 [{}] 未配置 API KEY，跳过注册", p.getProviderKey());
                    continue;
                }
                String baseUrl = "https://dashscope.aliyuncs.com";
                String wsUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";

                DashScopeAudioSpeechApi api = DashScopeAudioSpeechApi.builder()
                        .baseUrl(baseUrl)
                        .websocketUrl(wsUrl)
                        .apiKey(new SimpleApiKey(apiKey))
                        .build();

                apis.put(p.getProviderKey(), api);
                configs.put(p.getProviderKey(), p);
                log.info("注册语音供应商 [{}] -> apiKey 已配置", p.getProviderKey());
                if (Boolean.TRUE.equals(p.getIsDefault())) {
                    dbDefault = p.getProviderKey();
                }
            } catch (Exception e) {
                log.warn("语音供应商 [{}] 注册失败，跳过：{}", p.getProviderKey(), e.getMessage());
            }
        }

        defaultKey = dbDefault;
        log.info("VoiceProviderRegistry 重建完成，共 {} 个语音供应商，默认 = [{}]", apis.size(), defaultKey);
    }

    /** 获取默认语音供应商的 API 实例，没有则返回 null */
    public DashScopeAudioSpeechApi getDefaultApi() {
        if (defaultKey == null) return null;
        return apis.get(defaultKey);
    }

    /** 获取默认语音供应商的配置，没有则返回 null */
    public AiProvider getDefaultConfig() {
        if (defaultKey == null) return null;
        return configs.get(defaultKey);
    }

    /** 按代号获取语音 API，不存在返回 null */
    public DashScopeAudioSpeechApi get(String providerKey) {
        return apis.get(providerKey);
    }

    /** 是否存在任何语音供应商 */
    public boolean hasAny() {
        return !apis.isEmpty();
    }
}
