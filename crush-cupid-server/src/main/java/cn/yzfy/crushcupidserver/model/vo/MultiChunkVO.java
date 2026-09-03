package cn.yzfy.crushcupidserver.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @className MultiChunkVO
 * @description 多条消息流式 SSE 事件。一次对话回合里，crush 可能连发多条消息；
 * 每个 chunk 携带 index（第几条），前端按 index 切气泡。
 * <p>
 * 协议示例：
 * <pre>
 * {"index":0,"type":"text","content":"你","done":false}
 * {"index":0,"type":"text","content":"好","done":false}
 * {"index":1,"type":"sticker","content":"/api/stickers/happy1.gif","done":false}  // 表情包气泡，content 为图片 URL
 * {"index":2,"type":"text","content":"在","done":false}   // index 跳变 -> 前端开新气泡
 * {"index":2,"type":"text","content":"干嘛","done":false}
 * {"index":2,"type":"text","content":"","done":true}       // 流结束
 * </pre>
 * @author 一朝风月
 * @code vo
 * @createTime 2026-08-26
 */
@Data
@NoArgsConstructor
public class MultiChunkVO {

    /** 消息类型：普通文本 */
    public static final String TYPE_TEXT = "text";
    /** 消息类型：表情包（content 为图片 URL，前端渲染 img） */
    public static final String TYPE_STICKER = "sticker";

    /** 当前 chunk 所属的消息序号（0-based），跳变即代表新气泡 */
    private int index;

    /** 消息类型：text / sticker */
    private String type;

    /** 本次增量文本（sticker 类型时为完整图片 URL，一次性下发） */
    private String content;

    /** 语音情感（[[emotion:情绪]] 标记解析后作用于该消息；可为 null 表示未标注） */
    private String emotion;

    /** 整个流是否结束 */
    private boolean done;

    public MultiChunkVO(int index, String content, boolean done) {
        this(index, TYPE_TEXT, content, done);
    }

    public MultiChunkVO(int index, String type, String content, boolean done) {
        this.index = index;
        this.type = type;
        this.content = content;
        this.done = done;
    }
}
