package cn.yzfy.crushcupidserver.skill;

import java.util.Map;

/**
 * Prompt 模板解析策略：把模板中的占位符填充为具体内容。
 */
public interface PromptResolver {

    String resolve(String template, Map<String, String> variables);
}
