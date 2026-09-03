package cn.yzfy.crushcupidserver.agent.memory;

import cn.yzfy.crushcupidserver.model.entity.Conversation;
import cn.yzfy.crushcupidserver.service.ConversationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 基于 PostgreSQL(conversation 表) 的会话记忆。
 * conversationId 约定为 "crush:{id}"。
 */
@Component
public class PostgresChatMemory implements ChatMemory {

    private static final String PREFIX = "crush:";

    private final ConversationService conversationService;

    public PostgresChatMemory(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        Long crushId = parseCrushId(conversationId);
        List<Conversation> rows = messages.stream()
                .map(m -> toEntity(crushId, m))
                .filter(Objects::nonNull)
                .toList();
        if (!rows.isEmpty()) {
            conversationService.saveBatch(rows);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        Long crushId = parseCrushId(conversationId);
        List<Conversation> rows = conversationService.list(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getCrushId, crushId)
                        .orderByAsc(Conversation::getId));
        return rows.stream().map(this::toMessage).filter(Objects::nonNull).toList();
    }

    @Override
    public void clear(String conversationId) {
        Long crushId = parseCrushId(conversationId);
        conversationService.remove(new LambdaQueryWrapper<Conversation>().eq(Conversation::getCrushId, crushId));
    }

    private Long parseCrushId(String conversationId) {
        if (conversationId == null || !conversationId.startsWith(PREFIX)) {
            throw new IllegalArgumentException("非法 conversationId: " + conversationId);
        }
        return Long.parseLong(conversationId.substring(PREFIX.length()));
    }

    private Conversation toEntity(Long crushId, Message message) {
        String role;
        if (message.getMessageType() == MessageType.USER) {
            role = "user";
        } else if (message.getMessageType() == MessageType.ASSISTANT) {
            role = "assistant";
        } else {
            return null;
        }
        Conversation conversation = new Conversation();
        conversation.setCrushId(crushId);
        conversation.setRole(role);
        conversation.setContent(message.getText());
        conversation.setCreatedAt(new Date());
        return conversation;
    }

    private Message toMessage(Conversation conversation) {
        if (conversation.getContent() == null) {
            return null;
        }
        if ("user".equalsIgnoreCase(conversation.getRole())) {
            return new UserMessage(conversation.getContent());
        }
        if ("assistant".equalsIgnoreCase(conversation.getRole())) {
            return new AssistantMessage(conversation.getContent());
        }
        return null;
    }
}
