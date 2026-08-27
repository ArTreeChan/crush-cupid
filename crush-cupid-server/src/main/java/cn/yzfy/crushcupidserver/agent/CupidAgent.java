package cn.yzfy.crushcupidserver.agent;

import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.agent.advisor.MemoryAdvisor;
import cn.yzfy.crushcupidserver.agent.advisor.PersonaAdvisor;
import cn.yzfy.crushcupidserver.config.ChatClientProvider;
import cn.yzfy.crushcupidserver.exception.BizException;
import cn.yzfy.crushcupidserver.model.dto.ChatMedia;
import cn.yzfy.crushcupidserver.model.dto.ChatRequestDTO;
import cn.yzfy.crushcupidserver.model.dto.ProactiveRequestDTO;
import cn.yzfy.crushcupidserver.model.entity.Crush;
import cn.yzfy.crushcupidserver.model.service.CrushService;
import cn.yzfy.crushcupidserver.model.vo.MultiChunkVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

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
     * 对话主入口，返回结构化多条消息流。LLM 用 {@value MessageSeparator#SEPARATOR} 分隔多条短消息，
     * 本方法用 {@link MessageSeparator} 流式切分成 {@link MultiChunkVO}，前端按 index 切气泡。
     * <p>
     * 同步阻塞处理：参数校验同步做（轻量，便于直接返回 HTTP 400）；DB 查询 + ChatClient 构造 +
     * UserMessage 拼装等重 IO 用 {@code Mono.fromCallable + subscribeOn(boundedElastic)} 移到弹性线程池，
     * 请求线程立即返回 Flux，不阻塞 Servlet 线程。
     */
    public Flux<MultiChunkVO> chat(ChatRequestDTO dto) {
        if (StrUtil.isBlank(dto.getCrushSlug())) {
            throw BizException.badRequest("缺少 crushSlug");
        }
        boolean hasText = StrUtil.isNotBlank(dto.getMessage());
        boolean hasMedia = dto.getMedia() != null && !dto.getMedia().isEmpty();
        if (!hasText && !hasMedia) {
            throw BizException.badRequest("消息与 media 至少有一个非空");
        }
        String provider = dto.getProvider();
        String crushSlug = dto.getCrushSlug();

        // 预处理（DB 查询 / ChatClient 获取 / 消息构造）异步化到 boundedElastic，不阻塞请求线程
        return Mono.fromCallable(() -> {
                    Crush crush = crushService.getBySlug(crushSlug);
                    if (crush == null) {
                        throw BizException.notFound("未找到暗恋对象：" + crushSlug);
                    }
                    if (hasMedia) {
                        chatClientProvider.ensureMultimodal(provider);
                    }
                    ChatClient chatClient = chatClientProvider.get(provider);
                    UserMessage userMessage = buildUserMessage(dto, hasMedia);
                    return new ChatContext(crush, chatClient, userMessage);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(ctx -> streamMulti(ctx.chatClient(), ctx.userMessage(), ctx.crush()));
    }

    /**
     * 主动消息入口：crush 主动发起连发多条短消息，模拟真人微信「不聊天时主动找你」。
     * 内部以元指令作为 user 消息触发模型主动发言，与 {@link #chat} 共用 persona/memory/分隔符协议。
     * <p>
     * 同样把预处理异步化到 boundedElastic，请求线程立即返回 Flux。
     */
    public Flux<MultiChunkVO> proactive(ProactiveRequestDTO dto) {
        if (StrUtil.isBlank(dto.getCrushSlug())) {
            throw BizException.badRequest("缺少 crushSlug");
        }
        String provider = dto.getProvider();
        String crushSlug = dto.getCrushSlug();
        String contextHint = dto.getContextHint();

        return Mono.fromCallable(() -> {
                    Crush crush = crushService.getBySlug(crushSlug);
                    if (crush == null) {
                        throw BizException.notFound("未找到暗恋对象：" + crushSlug);
                    }
                    ChatClient chatClient = chatClientProvider.get(provider);
                    UserMessage userMessage = new UserMessage(buildProactivePrompt(crush, contextHint));
                    return new ChatContext(crush, chatClient, userMessage);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(ctx -> streamMulti(ctx.chatClient(), ctx.userMessage(), ctx.crush()));
    }

    /**
     * 共用的流式调用 + 多条消息切分。绑定 persona/memory advisor 与会话记忆。
     * <p>
     * 切分阶段用 {@code publishOn(boundedElastic)} 让下游 chunk 投递到弹性线程，
     * 避免 emitter.send 的同步 socket 写阻塞 LLM 流式响应线程。
     */
    private Flux<MultiChunkVO> streamMulti(ChatClient chatClient, UserMessage userMessage, Crush crush) {
        String conversationId = "crush:" + crush.getId();
        String persona = buildPersona(crush);
        String memory = buildMemory(crush);

        Flux<String> raw = chatClient.prompt()
                .messages(userMessage)
                .advisors(a -> a
                        .advisors(personaAdvisor, memoryAdvisor)
                        .param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param(PersonaAdvisor.CONTEXT_KEY, persona)
                        .param(MemoryAdvisor.CONTEXT_KEY, memory))
                .stream()
                .content();

        // 用 Flux.defer 保证每次订阅都新建一个有状态的切分器
        // publishOn 把切分 + 后续 emitter.send 移到弹性线程，不阻塞 LLM 流式响应线程
        return Flux.defer(() -> {
                    MessageSeparator splitter = new MessageSeparator();
                    return raw
                            .concatMapIterable(splitter::accept)
                            .concatWith(Flux.fromStream(splitter.finish().stream()));
                })
                .publishOn(Schedulers.boundedElastic());
    }

    /**
     * 异步预处理结果容器：把 crush / chatClient / userMessage 打包传给 streamMulti。
     */
    private record ChatContext(Crush crush, ChatClient chatClient, UserMessage userMessage) {
    }

    /**
     * 静默生成主动消息并落库（供定时调度器使用）。
     * <p>
     * 复用 {@link #proactive(ProactiveRequestDTO)} 的 persona/memory/分隔符协议，但改用
     * 非流式 {@code .call()}：MessageChatMemoryAdvisor 会在 after 阶段把生成的 assistant
     * 消息写入 conversation 表，前端无需长连接即可于下次加载历史时看到。
     *
     * @param crush       目标暗恋对象
     * @param contextHint 场景暗示（可选，如「下雨天」「你刚发了条朋友圈」）
     * @return LLM 生成的原始回复文本（含 {@link MessageSeparator#SEPARATOR} 分隔的多条短消息）
     */
    public String proactiveSilent(Crush crush, String contextHint) {
        String conversationId = "crush:" + crush.getId();
        UserMessage userMessage = new UserMessage(buildProactivePrompt(crush, contextHint, true));
        ChatClient chatClient = chatClientProvider.getDefault();
        return chatClient.prompt()
                .messages(userMessage)
                .advisors(a -> a
                        .advisors(personaAdvisor, memoryAdvisor)
                        .param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param(PersonaAdvisor.CONTEXT_KEY, buildPersona(crush))
                        .param(MemoryAdvisor.CONTEXT_KEY, buildMemory(crush)))
                .call()
                .content();
    }

    /**
     * 构造主动消息触发 prompt：注入时间、关系阶段、用户暗示，要求连发多条。
     */
    private String buildProactivePrompt(Crush crush, String contextHint) {
        return buildProactivePrompt(crush, contextHint, false);
    }

    /**
     * 构造主动消息触发 prompt。silent=true 时额外告知此刻无需用户在场（后台守护触发），
     * 让 LLM 说真实自然的话而不显得「被点醒」。
     */
    private String buildProactivePrompt(Crush crush, String contextHint, boolean silent) {
        StringBuilder sb = new StringBuilder();
        sb.append("【系统元指令】现在不是用户在和你说话，而是请你主动找用户聊天。\n");
        if (silent) {
            sb.append("此刻是自然生活的某个时刻，你想起 ta 了，主动开口说点什么。\n");
        }
        sb.append("当前时间：").append(LocalDate.now()).append(" ")
                .append(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
                .append("（请据此判断时段：早晨/午休/深夜等，调整说话的语气与内容）\n");
        if (StrUtil.isNotBlank(contextHint)) {
            sb.append("场景暗示：").append(contextHint).append("\n");
        }
        sb.append("根据你们的关系阶段、性格、记忆，自然地说点什么。\n");
        sb.append("可以选的方向：分享日常 / 撒娇 / 关心 / 抱怨 / 求关注 / 借故搭话 / 突然想起一件事。\n");
        sb.append("像真人微信一样连发多条短消息，用 ").append(MessageSeparator.SEPARATOR).append(" 分隔。\n");
        sb.append("不要解释、不要带括号动作描述、不要说\"我是 AI\"。\n");
        return sb.toString();
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

    public String buildPersona(Crush c) {
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
        appendMultiMessageGuide(sb);
        return sb.toString();
    }

    /**
     * 追加多条消息沟通风格指引：让模型像真人微信一样连发短消息，用 {@value MessageSeparator#SEPARATOR} 分隔。
     */
    private void appendMultiMessageGuide(StringBuilder sb) {
        sb.append("## 沟通风格\n");
        sb.append("像真人微信聊天一样连发多条短消息，每条都很短（几个字到一句话），不要写成一大段。\n");
        sb.append("用 ").append(MessageSeparator.SEPARATOR)
                .append(" 分隔不同条消息。例如：在吗？").append(MessageSeparator.SEPARATOR)
                .append("刚看到一个东西超像你").append(MessageSeparator.SEPARATOR)
                .append("哈哈哈哈你猜是啥\n");
        sb.append("不要带括号动作描述、不要解释、不要说\"我是 AI\"。\n");
    }

    public String buildMemory(Crush c) {
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
