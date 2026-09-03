package cn.yzfy.crushcupidserver.model.vo;

import lombok.Data;

/**
 * Skill 元信息出参
 */
@Data
public class SkillMetaVO {

    private String name;

    private String description;

    private String version;

    private String argumentHint;

    private boolean userInvocable;
}
