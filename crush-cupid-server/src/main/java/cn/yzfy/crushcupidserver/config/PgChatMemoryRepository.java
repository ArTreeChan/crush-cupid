package cn.yzfy.crushcupidserver.config;

import cn.yzfy.crushcupidserver.agent.StickerSanitizer;
import cn.yzfy.crushcupidserver.model.entity.Conversation;
import cn.yzfy.crushcupidserver.service.ConversationService;
import cn.yzfy.crushcupidserver.service.CrushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @className PgChatMemoryRepository
 * @description 基于 PostgreSQL {@code conversation} 表实现的会话记忆仓库。
 * <p>
 * 复用项目既有表结构 {@code (id, crush_id, role, content, created_at)}：
 * conversationId 约定为 {@code "crush:{crushId}"}，由本类解析出 crushId 后写入 conversation 表。
 * <p>
 * 存储约定：
 * - role 存 MessageType 小写（user/assistant/system/tool）；
 * - content 存 message.getText() 纯文本（多模态 Media 暂不入库，下次重发即可）。
 * <p>
 * saveAll 采用「先按 crushId 清空 + 批量插入」的覆盖语义，符合 {@link ChatMemoryRepository} 契约。
 * @author 一朝风月
 * @code repository
 * @createTime 2026-08-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgChatMemoryRepository implements ChatMemoryRepository {

    /** conversationId 前缀，与 CupidAgent 内 {@code "crush:" + crushId} 对齐 */
    public static final String CONV_PREFIX = "crush:";

    private final ConversationService conversationService;
    private final CrushService crushService;

    @Override
    public List<String> findConversationIds() {
        // 取所有 crush id，拼成 "crush:{id}" 作为 conversationId
        return crushService.list().stream()
                .map(c -> CONV_PREFIX + c.getId())
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Long crushId = parseCrushId(conversationId);
        if (crushId == null) {
            return List.of();
        }
        // 按 created_at 升序还原对话顺序
        List<Conversation> rows = conversationService.lambdaQuery()
                .eq(Conversation::getCrushId, crushId)
                .orderByAsc(Conversation::getCreatedAt)
                .list();
        List<Message> messages = new ArrayList<>(rows.size());
        for (Conversation row : rows) {
            Message msg = toMessage(row.getRole(), row.getContent());
            if (msg != null) {
                messages.add(msg);
            }
        }
        // 历史注入 prompt 前清洗 assistant 侧表情包痕迹：
        // 把 [[sticker:URL]] / [表情包] / 裸 URL 替换为占位文本，防止 LLM 看到后模仿输出。
        // 写入侧不清洗——原样存 [[sticker:URL]]，保留 URL 供前端历史回显。
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (m instanceof AssistantMessage am) {
                String cleaned = StickerSanitizer.sanitize(am.getText());
                // 额外清洗存量脏数据里的 [表情包]（单括号，旧占位格式）和 (此处发表了一个表情包)
                if (cleaned != null) {
                    cleaned = cleaned.replace("[表情包]", StickerSanitizer.PLACEHOLDER);
                }
                if (cleaned != null && !cleaned.equals(am.getText())) {
                    messages.set(i, new AssistantMessage(cleaned));
                }
            }
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Long crushId = parseCrushId(conversationId);
        if (crushId == null) {
            log.warn("saveAll 跳过：无法解析 conversationId={}", conversationId);
            return;
        }
        // 覆盖语义：先清空该 crush 的所有历史，再批量插入新列表
        conversationService.lambdaUpdate()
                .eq(Conversation::getCrushId, crushId)
                .remove();
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<Conversation> rows = new ArrayList<>(messages.size());
        Date now = new Date();
        for (Message msg : messages) {
            Conversation row = new Conversation();
            row.setCrushId(crushId);
            row.setRole(roleCode(msg.getMessageType()));
            // 写入侧不清洗：原样存 [[sticker:URL]]，保留 URL 供前端历史回显。
            // 读取侧（findByConversationId）注入 prompt 前清洗，防止 LLM 模仿 URL。
            row.setContent(msg.getText());
            row.setCreatedAt(now);
            rows.add(row);
        }
        conversationService.saveBatch(rows);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        Long crushId = parseCrushId(conversationId);
        if (crushId == null) {
            return;
        }
        conversationService.lambdaUpdate()
                .eq(Conversation::getCrushId, crushId)
                .remove();
    }

    /** conversationId -> crushId；非 "crush:" 前缀或解析失败返回 null */
    private Long parseCrushId(String conversationId) {
        if (conversationId == null || !conversationId.startsWith(CONV_PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(conversationId.substring(CONV_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** MessageType -> 表中 role 字段小写值 */
    private String roleCode(MessageType type) {
        return switch (type) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
            case TOOL -> "tool";
        };
    }

    /** 表中 role 字段 -> Message 实例。tool 消息无对应类，跳过返回 null（不渲染） */
    private Message toMessage(String role, String content) {
        if (role == null) {
            return null;
        }
        return switch (role.toLowerCase()) {
            case "user" -> new UserMessage(content);
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            // TOOL 消息无独立 Message 子类，回读时跳过
            case "tool" -> null;
            default -> null;
        };
    }
}
