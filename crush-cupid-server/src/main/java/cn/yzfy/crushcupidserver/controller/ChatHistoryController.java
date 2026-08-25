package cn.yzfy.crushcupidserver.controller;

import cn.hutool.core.util.StrUtil;
import cn.yzfy.crushcupidserver.common.Result;
import cn.yzfy.crushcupidserver.exception.BizException;
import cn.yzfy.crushcupidserver.model.entity.Crush;
import cn.yzfy.crushcupidserver.model.entity.Conversation;
import cn.yzfy.crushcupidserver.model.service.ConversationService;
import cn.yzfy.crushcupidserver.model.service.CrushService;
import cn.yzfy.crushcupidserver.model.vo.ChatHistoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @className ChatHistoryController
 * @description 对话历史查询接口。前端进入对话页时按 crushSlug 加载本地 PG 中已落库的对话记录，
 * 让 crush 跨刷新/重启仍能延续上下文。
 * @author 一朝风月
 * @code controller
 * @createTime 2026-08-26
 */
@RestController
@RequestMapping("/api/chat/history")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final CrushService crushService;
    private final ConversationService conversationService;

    /**
     * 按 crushSlug 拉历史消息列表。
     */
    @GetMapping
    public Result<List<ChatHistoryVO>> history(@RequestParam String crushSlug) {
        if (StrUtil.isBlank(crushSlug)) {
            throw BizException.badRequest("crushSlug 不能为空");
        }
        Crush crush = crushService.getBySlug(crushSlug);
        if (crush == null) {
            throw BizException.notFound("未找到暗恋对象：" + crushSlug);
        }
        List<Conversation> rows = conversationService.lambdaQuery()
                .eq(Conversation::getCrushId, crush.getId())
                .orderByAsc(Conversation::getCreatedAt)
                .list();
        List<ChatHistoryVO> list = rows.stream().map(ChatHistoryVO::of).toList();
        return Result.ok(list);
    }
}
