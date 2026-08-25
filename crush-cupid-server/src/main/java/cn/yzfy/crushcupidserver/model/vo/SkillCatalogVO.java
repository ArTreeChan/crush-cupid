package cn.yzfy.crushcupidserver.model.vo;

import lombok.Data;

import java.util.List;

/**
 * Skill 目录出参
 */
@Data
public class SkillCatalogVO {

    private SkillMetaVO skill;

    private List<String> prompts;
}
