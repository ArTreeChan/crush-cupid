package cn.yzfy.crushcupidserver.agent;

import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.exception.BizException;
import com.alibaba.cloud.ai.dashscope.audio.tts.DashScopeAudioSpeechModel;
import com.alibaba.cloud.ai.dashscope.audio.tts.DashScopeAudioSpeechOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * @className VoiceService
 * @description 语音合成服务。基于 Alibaba DashScope CosyVoice，把 crush 的文本回复合成专属声线语音。
 * <p>
 * 调用 Spring AI 标准 {@link org.springframework.ai.audio.tts.TextToSpeechModel} 抽象，
 * 实际实现是 {@link DashScopeAudioSpeechModel}（由 Alibaba autoconfigure 注册）。
 * @author 一朝风月
 * @code service
 * @createTime 2026-08-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceService {

    private final ObjectProvider<DashScopeAudioSpeechModel> speechModelProvider;

    /**
     * 合成语音。
     *
     * @param text  待合成文本（不能为空）
     * @param voice 声线 ID，可空——空则用 yml 默认声线
     * @return mp3 字节流
     */
    public byte[] synthesize(String text, String voice) {
        if (StrUtil.isBlank(text)) {
            throw BizException.badRequest("待合成文本不能为空");
        }
        DashScopeAudioSpeechModel model = speechModelProvider.getIfAvailable();
        if (model == null) {
            throw BizException.badRequest("CosyVoice 不可用：未配置 spring.ai.dashscope.audio.speech.*");
        }

        TextToSpeechPrompt prompt = StrUtil.isBlank(voice)
                ? new TextToSpeechPrompt(text)
                : new TextToSpeechPrompt(text, DashScopeAudioSpeechOptions.builder().voice(voice).build());

        TextToSpeechResponse response = model.call(prompt);
        return response.getResult().getOutput();
    }
}
