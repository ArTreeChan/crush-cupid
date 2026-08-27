package cn.yzfy.crushcupidserver.model.dto;

import lombok.Data;

/**
 * @className VoiceDesignDTO
 * @description 声音设计请求入参：用自然语言描述为 crush 创建 CosyVoice v3.5 专属音色。
 * @author 一朝风月
 * @code dto
 * @createTime 2026-08-27
 */
@Data
public class VoiceDesignDTO {

    /** 声音描述（≤500 字符），如「温柔的年轻女性，语速轻快，说话带笑意，有点小傲娇」 */
    private String voicePrompt;

    /** 预览文本（15~200 字符），空则用内置默认 */
    private String previewText;
}
