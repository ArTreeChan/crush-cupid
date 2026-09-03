package cn.yzfy.crushcupidserver.controller;

import cn.yzfy.crushcupidserver.agent.VoiceService;
import cn.yzfy.crushcupidserver.common.Result;
import cn.yzfy.crushcupidserver.model.dto.VoiceDesignDTO;
import cn.yzfy.crushcupidserver.model.dto.VoiceRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * @className VoiceController
 * @description 语音接口：合成（文本 -> mp3 base64）与声音设计（描述 -> 专属音色 voice_id）。
 * 前端把 crush 某条 assistant 消息文本 POST 过来合成 mp3，解码后 Blob URL 播放；
 * 创建 crush 时可用人设描述调声音设计生成专属声线。
 * <p>
 * 统一用项目封装的 {@link Result}，避免裸 ResponseEntity。
 * @author 一朝风月
 * @code controller
 * @createTime 2026-08-26
 */
@RestController
@RequestMapping("/api/chat/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceService voiceService;

    /**
     * 合成语音消息。返回的 data 是 base64 编码的 mp3。
     */
    @PostMapping
    public Result<String> synthesize(@RequestBody VoiceRequestDTO dto) {
        byte[] audio = voiceService.synthesize(dto.getText(), dto.getVoice(), dto.getInstruction());
        String base64 = Base64.getEncoder().encodeToString(audio);
        return Result.ok(base64);
    }

    /**
     * 声音设计：用自然语言描述创建 CosyVoice v3.5 专属音色，返回 voice_id。
     */
    @PostMapping("/design")
    public Result<String> design(@RequestBody VoiceDesignDTO dto) {
        return Result.ok(voiceService.designVoice(dto.getVoicePrompt(), dto.getPreviewText()));
    }
}
