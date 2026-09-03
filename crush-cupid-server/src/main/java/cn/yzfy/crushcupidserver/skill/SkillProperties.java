package cn.yzfy.crushcupidserver.skill;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * crush.skill 配置项
 */
@Data
@Component
@ConfigurationProperties(prefix = "crush.skill")
public class SkillProperties {

    /** GitHub raw 基础地址 */
    private String baseUrl = "https://raw.githubusercontent.com/xiaoheizi8/crush-skills/refs/heads/main/SKILL.md";

    /** 本地缓存过期时间（秒） */
    private long cacheTtl = 3600;
}
