package cn.yzfy.crushcupidserver.model.dto;

import lombok.Data;

/**
 * @className VoiceRequestDTO
 * @description 语音合成请求入参。把 crush 的文本回复合成 CosyVoice 语音消息。
 * @author 一朝风月
 * @code dto
 * @createTime 2026-08-26
 */
@Data
public class VoiceRequestDTO {

    /** 待合成文本（一般是 crush 某条 assistant 消息内容） */
    private String text;

    /** 声线 ID，可空；空走 yml 默认（longxiaochun 等）。 */
    private String voice;
}
