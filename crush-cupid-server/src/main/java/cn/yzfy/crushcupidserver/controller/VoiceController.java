package cn.yzfy.crushcupidserver.controller;

import cn.yzfy.crushcupidserver.agent.VoiceService;
import cn.yzfy.crushcupidserver.common.Result;
import cn.yzfy.crushcupidserver.model.dto.VoiceRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * @className VoiceController
 * @description 语音合成接口。前端把 crush 某条 assistant 消息文本 POST 过来，
 * 走 CosyVoice 合成 mp3，结果用 base64 包到 {@link Result} 返回，前端解码后 Blob URL 播放。
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
        byte[] audio = voiceService.synthesize(dto.getText(), dto.getVoice());
        String base64 = Base64.getEncoder().encodeToString(audio);
        return Result.ok(base64);
    }
}
