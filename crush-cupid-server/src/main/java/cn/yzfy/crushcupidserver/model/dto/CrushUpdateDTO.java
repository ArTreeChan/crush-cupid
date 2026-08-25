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
}
