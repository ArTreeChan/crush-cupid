package cn.yzfy.crushcupidserver.skill;

/**
 * Skill 资源统一抽象（Adapter 目标接口）。
 * 把「GitHub raw / 本地文件 / 其他源」适配成统一的取资源接口。
 */
public interface SkillResourceClient {

    /**
     * 拉取指定路径的资源原始文本。
     *
     * @param path 相对路径，如 SKILL.md、prompts/persona_builder.md
     */
    String fetch(String path);
}
