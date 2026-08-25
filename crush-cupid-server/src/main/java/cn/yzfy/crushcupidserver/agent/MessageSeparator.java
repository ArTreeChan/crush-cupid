package cn.yzfy.crushcupidserver.agent;

import cn.yzfy.crushcupidserver.model.vo.MultiChunkVO;

import java.util.ArrayList;
import java.util.List;

/**
 * @className MessageSeparator
 * @description 有状态的流式分隔符切分器。把上游 LLM 产生的纯文本 chunk 流按分隔符
 * （{@value #SEPARATOR}）实时切分成多条 {@link MultiChunkVO}，支持分隔符跨 chunk 的边界场景。
 * <p>
 * 单次对话回合内创建一个实例使用；线程不安全，仅 Reactor 单线消费。
 * @author 一朝风月
 * @code splitter
 * @createTime 2026-08-26
 */
public class MessageSeparator {

    /** 多条消息分隔符。LLM 在 prompt 指引下使用它来连发短消息 */
    public static final String SEPARATOR = "|||";

    /** 当前消息序号 */
    private int index = 0;

    /** 待处理缓冲区（可能含未完成的分隔符前缀） */
    private String buffer = "";

    /**
     * 接收一个上游 chunk，返回 0~N 个结构化输出。
     * 调用方应在 Reactor 链上 concatMapIterable 逐个消费。
     */
    public List<MultiChunkVO> accept(String chunk) {
        List<MultiChunkVO> out = new ArrayList<>();
        if (chunk == null || chunk.isEmpty()) {
            return out;
        }
        buffer += chunk;

        int idx;
        // 反复切分：buffer 内只要出现完整分隔符，就把前面的文本作为当前 index 输出，并 index++
        while ((idx = buffer.indexOf(SEPARATOR)) >= 0) {
            String text = buffer.substring(0, idx);
            if (!text.isEmpty()) {
                out.add(new MultiChunkVO(index, text, false));
            }
            index++;
            buffer = buffer.substring(idx + SEPARATOR.length());
        }
        // 剩余 buffer 末尾可能是分隔符的前缀（如 "||"），不能立即输出，否则会把分隔符字符误当前文
        int safeLen = safeEmitLength(buffer);
        if (safeLen > 0) {
            out.add(new MultiChunkVO(index, buffer.substring(0, safeLen), false));
            buffer = buffer.substring(safeLen);
        }
        return out;
    }

    /**
     * 上游流结束时调用，输出最后残留文本 + done 标记。
     */
    public List<MultiChunkVO> finish() {
        List<MultiChunkVO> out = new ArrayList<>();
        if (!buffer.isEmpty()) {
            // 收尾时 buffer 里残留的若是分隔符前缀字符，无意义，直接丢弃
            String remain = buffer.replace(SEPARATOR, "");
            if (!remain.isEmpty()) {
                out.add(new MultiChunkVO(index, remain, false));
            }
        }
        out.add(new MultiChunkVO(index, "", true));
        return out;
    }

    /**
     * 计算 s 末尾不构成分隔符前缀的最大安全输出长度。
     * 即：s[len-safeLen, len) 是 SEPARATOR 的某个前缀（保留这部分待下个 chunk 拼接判断）。
     */
    private int safeEmitLength(String s) {
        int sepLen = SEPARATOR.length();
        int maxK = Math.min(sepLen - 1, s.length());
        for (int k = maxK; k > 0; k--) {
            if (SEPARATOR.startsWith(s.substring(s.length() - k))) {
                return s.length() - k;
            }
        }
        return s.length();
    }
}
