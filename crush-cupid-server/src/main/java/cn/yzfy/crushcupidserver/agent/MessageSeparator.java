package cn.yzfy.crushcupidserver.agent;

import cn.yzfy.crushcupidserver.model.vo.MultiChunkVO;

import java.util.ArrayList;
import java.util.List;

/**
 * @className MessageSeparator
 * @description 有状态的流式标记切分器。把上游 LLM 产生的纯文本 chunk 流按两类标记实时切分：
 * <ul>
 *   <li>多条消息分隔符 {@value #SEPARATOR}：切分成多条文本 {@link MultiChunkVO}，index 自增</li>
 *   <li>表情包标记 {@value #MARKER_PREFIX}情绪{@value #MARKER_SUFFIX}：切分成 sticker 类型气泡，
 *       产出后 index 自增（表情包是独立气泡，后续文本不应追加到图片 URL 上）</li>
 * </ul>
 * 两类标记均支持跨 chunk 边界（半截标记保留在缓冲区待下个 chunk 拼接判断）。
 * 内部用 {@link StringBuilder} 避免频繁字符串拼接的 GC 压力。
 * <p>
 * 单次对话回合内创建一个实例使用；线程不安全，仅 Reactor 单线消费。
 * @author 一朝风月
 * @code splitter
 * @createTime 2026-08-26
 */
public class MessageSeparator {

    /** 多条消息分隔符。LLM 在 prompt 指引下使用它来连发短消息 */
    public static final String SEPARATOR = "|||";

    /** 表情包标记前缀。LLM 输出形如 [[sticker:开心]] */
    public static final String MARKER_PREFIX = "[[sticker:";
    /** 表情包标记后缀 */
    public static final String MARKER_SUFFIX = "]]";

    /** 当前消息序号 */
    private int index = 0;

    /** 待处理缓冲区（可能含未完成的分隔符/表情包标记前缀） */
    private final StringBuilder buffer = new StringBuilder(512);

    /**
     * 接收一个上游 chunk，返回 0~N 个结构化输出。
     * 调用方应在 Reactor 链上 concatMapIterable 逐个消费。
     */
    public List<MultiChunkVO> accept(String chunk) {
        List<MultiChunkVO> out = new ArrayList<>();
        if (chunk == null || chunk.isEmpty()) {
            return out;
        }
        buffer.append(chunk);
        drain(out);
        return out;
    }

    /**
     * 上游流结束时调用，输出最后残留文本 + done 标记。
     */
    public List<MultiChunkVO> finish() {
        List<MultiChunkVO> out = new ArrayList<>();
        if (buffer.length() > 0) {
            drain(out);
            if (buffer.length() > 0) {
                // 残留文本：剥离任何半截标记语法，保留纯文本
                String remain = buffer.toString()
                        .replace(SEPARATOR, "")
                        .replace(MARKER_PREFIX, "")
                        .replace(MARKER_SUFFIX, "");
                if (!remain.isEmpty()) {
                    out.add(new MultiChunkVO(index, remain, false));
                }
                buffer.setLength(0);
            }
        }
        out.add(new MultiChunkVO(index, "", true));
        return out;
    }

    /**
     * 反复消费缓冲区：只要出现完整的分隔符或表情包标记就输出对应 chunk。
     */
    private void drain(List<MultiChunkVO> out) {
        while (buffer.length() > 0) {
            int sepIdx = indexOf(buffer, SEPARATOR);
            int markStart = indexOf(buffer, MARKER_PREFIX);

            // 都没有：按安全长度输出普通文本（保留可能半截的标记前缀）
            if (markStart < 0 && sepIdx < 0) {
                int safeLen = safeEmitLength();
                if (safeLen > 0) {
                    out.add(new MultiChunkVO(index, substring(buffer, 0, safeLen), false));
                    buffer.delete(0, safeLen);
                }
                return;
            }

            // 取更早出现的那个 token 处理
            boolean sepFirst = sepIdx >= 0 && (markStart < 0 || sepIdx < markStart);
            if (sepFirst) {
                if (sepIdx > 0) {
                    out.add(new MultiChunkVO(index, substring(buffer, 0, sepIdx), false));
                }
                index++;
                buffer.delete(0, sepIdx + SEPARATOR.length());
                continue;
            }

            // 表情包标记：标记前的文本先输出，标记输出为 sticker chunk
            int markEnd = indexOf(buffer, MARKER_SUFFIX, markStart + MARKER_PREFIX.length());
            if (markEnd < 0) {
                // 标记未闭合（情绪词可能跨 chunk）：从标记起点起全部缓冲，等待后续 chunk
                emitBefore(out, markStart);
                return;
            }
            emitBefore(out, markStart);
            // emitBefore 已删除 buffer[0, markStart)，markEnd 仍是相对「原 buffer」的索引，
            // 直接沿用会多切 markStart 个字符：情绪词会带上 "]]" 后缀（URL 则带上 "]]" 变坏链），
            // 且 delete 会把标记后的文本一并误删。这里统一平移 markStart 得到相对当前 buffer 的终点。
            int relEnd = markEnd - markStart;
            String emotion = substring(buffer, MARKER_PREFIX.length(), relEnd).trim();
            if (!emotion.isEmpty()) {
                // 表情包永远独占一个新气泡：LLM 常省略 ||| 直接输出「文本[[sticker:..]]」，
                // 若沿用当前 index，前端会把同 index 的文本气泡覆盖成图片——必须翻页
                out.add(new MultiChunkVO(++index, MultiChunkVO.TYPE_STICKER, emotion, false));
            }
            // 后续文本再开新气泡（紧跟表情包的文本也不会追加到图片 URL 上）
            index++;
            buffer.delete(0, relEnd + MARKER_SUFFIX.length());
        }
    }

    /** 输出缓冲区 [0, end) 的文本并截掉（非空才输出） */
    private void emitBefore(List<MultiChunkVO> out, int end) {
        if (end > 0) {
            out.add(new MultiChunkVO(index, substring(buffer, 0, end), false));
            buffer.delete(0, end);
        }
    }

    /**
     * 计算 buffer 末尾不构成任何标记前缀的最大安全输出长度。
     * 保留可能半截的分隔符 / 表情包标记前缀，等下个 chunk 拼接判断。
     */
    private int safeEmitLength() {
        int len = buffer.length();
        int limit = len;

        // 未闭合的表情包标记：从最后一个 "[[sticker:" 起整段缓冲
        int markStart = lastIndexOf(buffer, MARKER_PREFIX);
        if (markStart >= 0 && indexOf(buffer, MARKER_SUFFIX, markStart + MARKER_PREFIX.length()) < 0) {
            limit = Math.min(limit, markStart);
        }

        // 末尾半截的 "[[sticker:" 前缀（如 "["、"[[s"）
        int maxK = Math.min(MARKER_PREFIX.length() - 1, len);
        for (int k = maxK; k > 0; k--) {
            if (MARKER_PREFIX.startsWith(substring(buffer, len - k, len))) {
                limit = Math.min(limit, len - k);
                break;
            }
        }

        // 末尾半截的分隔符前缀（如 "|"、"||"）
        int sepLen = SEPARATOR.length();
        maxK = Math.min(sepLen - 1, len);
        for (int k = maxK; k > 0; k--) {
            if (SEPARATOR.startsWith(substring(buffer, len - k, len))) {
                limit = Math.min(limit, len - k);
                break;
            }
        }
        return limit;
    }

    // ---- StringBuilder 辅助方法，避免 toString() 产生中间 String ----

    private static int indexOf(StringBuilder sb, String str) {
        return indexOf(sb, str, 0);
    }

    private static int indexOf(StringBuilder sb, String str, int from) {
        int max = sb.length() - str.length();
        for (int i = from; i <= max; i++) {
            boolean match = true;
            for (int j = 0; j < str.length(); j++) {
                if (sb.charAt(i + j) != str.charAt(j)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexOf(StringBuilder sb, String str) {
        int max = sb.length() - str.length();
        for (int i = max; i >= 0; i--) {
            boolean match = true;
            for (int j = 0; j < str.length(); j++) {
                if (sb.charAt(i + j) != str.charAt(j)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    private static String substring(StringBuilder sb, int start, int end) {
        return sb.substring(start, end);
    }
}
