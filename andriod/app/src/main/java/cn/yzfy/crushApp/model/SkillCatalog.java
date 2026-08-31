package cn.yzfy.crushApp.model;

import java.util.List;

/** Skill 目录 */
public class SkillCatalog {
    public SkillMeta skill;
    public List<String> prompts;

    public static class SkillMeta {
        public String name;
        public String description;
        public String version;
        public String argumentHint;
        public boolean userInvocable;
    }
}