package cn.yzfy.crushcupidserver.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @className MultiChunkVO
 * @description 多条消息流式 SSE 事件。一次对话回合里，crush 可能连发多条消息；
 * 每个 chunk 携带 index（第几条），前端按 index 切气泡。
 * <p>
 * 协议示例：
 * <pre>
 * {"index":0,"content":"你","done":false}
 * {"index":0,"content":"好","done":false}
 * {"index":1,"content":"在","done":false}   // index 跳变 -> 前端开新气泡
 * {"index":1,"content":"干嘛","done":false}
 * {"index":1,"content":"","done":true}       // 流结束
 * </pre>
 * @author 一朝风月
 * @code vo
 * @createTime 2026-08-26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultiChunkVO {

    /** 当前 chunk 所属的消息序号（0-based），跳变即代表新气泡 */
    private int index;

    /** 本次增量文本 */
    private String content;

    /** 整个流是否结束 */
    private boolean done;
}
