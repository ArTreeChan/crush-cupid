package cn.yzfy.crushcupidserver.agent.tool;

import cn.yzfy.crushcupidserver.agent.MessageSeparator;
import cn.yzfy.crushcupidserver.agent.StickerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * @className StickerTools
 * @description 表情包工具：暴露给 LLM 的 {@link Tool}。
 * <p>
 * 设计：LLM 在聊天/主动消息中决定何时发表情包时，调用 {@link #pickSticker} 工具，
 * 工具按情绪从素材库（本地 manifest + ChineseBQB 远端仓库）随机抽取一张，返回
 * {@code [[sticker:图片URL]]} 标记字符串。LLM 应把这个标记原样放进消息文本里
 * （前后用 {@link MessageSeparator#SEPARATOR} 分隔，作为独立的一条消息）。
 * {@link cn.yzfy.crushcupidserver.agent.MessageSeparator} 会识别标记产出 sticker 类型气泡，
 * 前端按图片 URL 渲染独立表情包气泡。
 * <p>
 * 视觉模型介入点：多模态供应商收到图片后视觉理解 + 思考回复，自主决定是否调本工具发表情包。
 * @author 一朝风月
 * @code tool
 * @createTime 2026-08-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StickerTools {

    private final StickerService stickerService;

    /**
     * 随机选一张表情包。
     *
     * @param emotion 情绪词，如 开心/可爱/无语/生气/委屈/吃瓜/疑惑/尴尬
     * @param context 当下情境简述（如「对方发了张搞笑图」「被冷落了」），用于日志追踪，不影响选图
     * @return {@code [[sticker:图片URL]]} 标记字符串；无可用素材时返回提示
     */
    @Tool(description = "发送表情包。调用前先思考：结合 TA 的性格（外向爱斗图可每条消息都带，内敛则情绪高点才发）与当下情境，判断这条消息适不适合配表情包、配什么情绪。调用后把返回的 [[sticker:...]] 标记原样放进对应那条消息的文本里（可附在消息末尾，也可作为独立一条消息用 ||| 分隔），标记必须一字不改。可选情绪见 listStickerEmotions。")
    public String pickSticker(
            @ToolParam(description = "表情包要传达的情绪词，如 开心/可爱/无语/生气/委屈/吃瓜/疑惑/尴尬") String emotion,
            @ToolParam(description = "当下情境简述，如 对方发了张搞笑图、被冷落了、想撒娇") String context) {
        String url = stickerService.pickStickerUrl(emotion);
        if (url == null) {
            return "（暂无可用表情包，请直接用文字回复）";
        }
        log.info("pickSticker 调用：emotion={} context={} -> {}", emotion, context, url);
        // 返回标记字符串：[[sticker:URL]]
        // MessageSeparator 会识别这个标记产出 sticker 类型气泡
        return MessageSeparator.MARKER_PREFIX + url + MessageSeparator.MARKER_SUFFIX;
    }

    /**
     * 列出可用的情绪词（LLM 调 pickSticker 时从中选）。
     */
    @Tool(description = "列出当前可用的表情包情绪词，调用 pickSticker 时从中选择")
    public String listStickerEmotions() {
        return String.join(" / ", stickerService.availableEmotions());
    }
}
