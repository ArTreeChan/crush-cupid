package cn.yzfy.crushApp.model;

import java.util.List;

/** 自定义 LLM 供应商。capabilities: vision=看图, audio=听语音（文本是基本能力）。type: chat=对话大模型, voice=语音大模型 */
public class AiProvider {
    public Long id;
    public String name;
    public String providerKey;
    public String baseUrl;
    public String apiKey;
    public String model;
    public Double temperature;
    public Double topP;
    public Integer maxTokens;
    public List<String> capabilities;
    /** 供应商类型：chat=对话大模型 / voice=语音大模型，默认 chat */
    public String type;
    public Boolean isDefault;

    public boolean has(String cap) {
        return capabilities != null && capabilities.contains(cap);
    }
}