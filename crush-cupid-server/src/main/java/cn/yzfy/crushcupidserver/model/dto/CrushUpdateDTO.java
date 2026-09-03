package cn.yzfy.crushcupidserver.model.dto;

import lombok.Data;

/**
 * 更新暗恋对象入参
 */
@Data
public class CrushUpdateDTO {

    private String name;

    private String mbti;

    private String zodiac;

    private String occupation;

    private String gender;

    private String knowDuration;

    private String relationshipStatus;

    private String impression;

    /** CosyVoice 专属音色 voice_id（由声音设计接口产生） */
    private String voiceId;

    /** 语音风格指令（instruction）：控制情感、语气、语速、性格 */
    private String voiceInstruction;
}
