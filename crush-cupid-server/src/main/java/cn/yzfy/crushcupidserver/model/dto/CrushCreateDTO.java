package cn.yzfy.crushcupidserver.model.dto;

import lombok.Data;

/**
 * 创建暗恋对象入参
 */
@Data
public class CrushCreateDTO {

    private String name;

    private String slug;

    private String mbti;

    private String zodiac;

    private String occupation;

    private String gender;

    private String knowDuration;

    private String relationshipStatus;

    private String impression;
}
