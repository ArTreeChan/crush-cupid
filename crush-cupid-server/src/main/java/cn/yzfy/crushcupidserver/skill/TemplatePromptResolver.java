package cn.yzfy.crushcupidserver.skill;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 基于 {placeholder} 占位符的模板解析策略。
 */
@Component
public class TemplatePromptResolver implements PromptResolver {

    @Override
    public String resolve(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
