package cn.yzfy.crushcupidserver.agent;

import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.agent.advisor.MemoryAdvisor;
import cn.yzfy.crushcupidserver.agent.advisor.PersonaAdvisor;
import cn.yzfy.crushcupidserver.config.ChatClientProvider;
import cn.yzfy.crushcupidserver.exception.BizException;
import cn.yzfy.crushcupidserver.model.dto.ChatMedia;
import cn.yzfy.crushcupidserver.model.dto.ChatRequestDTO;
import cn.yzfy.crushcupidserver.model.entity.Crush;
import cn.yzfy.crushcupidserver.model.service.CrushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * @className CupidAgent
 * @description 智能 agent 门面（Facade）：对外只暴露 chat，屏蔽工具注册、advisor、记忆、
 * 供应商路由与多模态拼装细节。
 * <p>
 * 路由：按请求级 {@code provider} 选择 ChatClient；缺省走默认供应商（如 deepseek）。
 * 多模态：若请求带 {@link ChatMedia}，拼装为带 media 的 {@link UserMessage} 发送给模型；
 * 同时校验目标供应商是否声明支持多模态（vision/audio）。
 * @author 一朝风月
 * @code facade
 * @createTime 2026-08-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CupidAgent {

    private final ChatClientProvider chatClientProvider;
    private final CrushService crushService;
    private final PersonaAdvisor personaAdvisor;
    private final MemoryAdvisor memoryAdvisor;

    /**
     * 对话主入口，返回流式文本。
     */
    public Flux<String> chat(ChatRequestDTO dto) {
        if (StrUtil.isBlank(dto.getCrushSlug())) {
            throw BizException.badRequest("缺少 crushSlug");
        }
        boolean hasText = StrUtil.isNotBlank(dto.getMessage());
        boolean hasMedia = dto.getMedia() != null && !dto.getMedia().isEmpty();
        if (!hasText && !hasMedia) {
            throw BizException.badRequest("消息与 media 至少有一个非空");
        }

        Crush crush = crushService.getBySlug(dto.getCrushSlug());
        if (crush == null) {
            throw BizException.notFound("未找到暗恋对象：" + dto.getCrushSlug());
        }

        // 多模态请求时校验目标供应商
        if (hasMedia) {
            chatClientProvider.ensureMultimodal(dto.getProvider());
        }

        String conversationId = "crush:" + crush.getId();
        String persona = buildPersona(crush);
        String memory = buildMemory(crush);

        // 按 provider 路由 ChatClient
        ChatClient chatClient = chatClientProvider.get(dto.getProvider());

        // 构造（多模态）用户消息
        UserMessage userMessage = buildUserMessage(dto, hasMedia);

        return chatClient.prompt()
                .messages(userMessage)
                .advisors(a -> a
                        .advisors(personaAdvisor, memoryAdvisor)
                        .param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param(PersonaAdvisor.CONTEXT_KEY, persona)
                        .param(MemoryAdvisor.CONTEXT_KEY, memory))
                .stream()
                .content();
    }

    /**
     * 构造用户消息：纯文本走简单 UserMessage，多模态则附加 Media 列表。
     */
    private UserMessage buildUserMessage(ChatRequestDTO dto, boolean hasMedia) {
        String text = StrUtil.blankToDefault(dto.getMessage(), "");
        if (!hasMedia) {
            return new UserMessage(text);
        }
        List<Media> medias = new ArrayList<>();
        for (ChatMedia m : dto.getMedia()) {
            medias.add(toMedia(m));
        }
        return UserMessage.builder()
                .text(text)
                .media(medias)
                .build();
    }

    /**
     * 将 DTO 的 {@link ChatMedia} 转为 Spring AI 的 {@link Media}。
     */
    private Media toMedia(ChatMedia m) {
        if (StrUtil.isBlank(m.getType()) || StrUtil.isBlank(m.getData())) {
            throw BizException.badRequest("ChatMedia 的 type/data 不能为空");
        }
        MimeType mime = StrUtil.isNotBlank(m.getMimeType())
                ? MimeType.valueOf(m.getMimeType())
                : inferMimeType(m.getType());
        try {
            switch (m.getType()) {
                case ChatMedia.TYPE_IMAGE_URL:
                case ChatMedia.TYPE_AUDIO_URL:
                    // URL 形态：用公开构造器 new Media(MimeType, URI)
                    return new Media(mime, URI.create(m.getData()));
                case ChatMedia.TYPE_IMAGE_BASE64:
                case ChatMedia.TYPE_AUDIO_BASE64:
                    // base64 形态：用 Builder 接收解码后的 byte[]
                    return Media.builder()
                            .mimeType(mime)
                            .data((Object) Base64.getDecoder().decode(m.getData()))
                            .build();
                default:
                    throw BizException.badRequest("不支持的 ChatMedia type：" + m.getType());
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("解析多模态数据失败：" + e.getMessage());
        }
    }

    /**
     * 按 type 推断默认 MIME 类型。
     */
    private MimeType inferMimeType(String type) {
        return switch (type) {
            case ChatMedia.TYPE_IMAGE_URL, ChatMedia.TYPE_IMAGE_BASE64 -> MimeType.valueOf("image/png");
            case ChatMedia.TYPE_AUDIO_URL, ChatMedia.TYPE_AUDIO_BASE64 -> MimeType.valueOf("audio/wav");
            default -> MimeType.valueOf("application/octet-stream");
        };
    }

    private String buildPersona(Crush c) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是").append(c.getName()).append("，不是 AI 助手。用 ta 的方式说话、用 ta 的逻辑思考。\n");
        if (notBlank(c.getMbti())) sb.append("MBTI：").append(c.getMbti()).append("\n");
        if (notBlank(c.getZodiac())) sb.append("星座：").append(c.getZodiac()).append("\n");
        if (notBlank(c.getRelationshipStatus())) sb.append("与用户关系：").append(c.getRelationshipStatus()).append("\n");
        if (notBlank(c.getImpression())) sb.append("用户对你的印象：").append(c.getImpression()).append("\n");
        appendLayer(sb, "Layer 0 硬规则", c.getPersonaLayer0());
        appendLayer(sb, "Layer 1 身份", c.getPersonaLayer1());
        appendLayer(sb, "Layer 2 说话风格", c.getPersonaLayer2());
        appendLayer(sb, "Layer 3 情感模式", c.getPersonaLayer3());
        appendLayer(sb, "Layer 4 关系行为", c.getPersonaLayer4());
        return sb.toString();
    }

    private String buildMemory(Crush c) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        if (notBlank(c.getMemoryOverview())) { sb.append("## 关系记忆\n").append(c.getMemoryOverview()).append("\n"); any = true; }
        if (notBlank(c.getMemoryTimeline())) { sb.append("## 时间线\n").append(c.getMemoryTimeline()).append("\n"); any = true; }
        if (notBlank(c.getMemorySweet())) { sb.append("## 甜蜜瞬间\n").append(c.getMemorySweet()).append("\n"); any = true; }
        if (notBlank(c.getMemoryInteraction())) { sb.append("## 互动模式\n").append(c.getMemoryInteraction()).append("\n"); any = true; }
        return any ? sb.toString() : "";
    }

    private void appendLayer(StringBuilder sb, String title, String content) {
        if (notBlank(content)) {
            sb.append("## ").append(title).append("\n").append(content).append("\n");
        }
    }

    private boolean notBlank(String s) {
        return StrUtil.isNotBlank(s);
    }
}
