package cn.yzfy.crushcupidserver.skill;

import lombok.Data;

/**
 * SKILL.md frontmatter 元信息
 */
@Data
public class SkillMeta {

    private String name;

    private String description;

    private String version;

    private String argumentHint;

    private boolean userInvocable;
}
