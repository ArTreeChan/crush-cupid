package cn.yzfy.crushcupidserver.agent;

import java.util.regex.Pattern;

/**
 * @className StickerSanitizer
 * @description 表情包痕迹清洗器：把 assistant 消息里的表情包标记 / 裸图片 URL 统一替换为
 * {@code [表情包]} 占位文本。
 * <p>
 * 背景：tool 方案时期落库的 assistant 原文含 {@code [[sticker:图片URL]]} 标记或裸 URL，
 * {@code MessageChatMemoryAdvisor} 会把历史原样注入 prompt，LLM 模仿后裸输出 URL——
 * 前端按文本渲染 + 语音合成把 URL 读出来（线上事故）。本类在记忆读写两侧清洗，
 * 让 LLM 只看到「对方发过表情包」的语义占位，看不到具体 URL，从源头杜绝模仿。
 * <p>
 * 清洗范围仅 assistant 消息；user 消息的 {@code [[图片:URL]]} 上传回显标记不受影响。
 * @author 一朝风月
 * @code sanitizer
 * @createTime 2026-08-28
 */
public final class StickerSanitizer {

    /** 清洗后的占位文本：语义描述式，LLM 不会模仿照搬（不像 [表情包] 那么容易被原样输出） */
    public static final String PLACEHOLDER = "(此处发表了一个表情包)";

    /** [[sticker:...]] 标记（URL 或情绪词均可），点号不匹配换行，避免跨条误伤 */
    private static final Pattern MARKER_PATTERN = Pattern.compile("\\[\\[sticker:[^\\]]*]]");

    /**
     * 裸表情包 URL 行：整行 trim 后满足任一条件——
     * ChineseBQB raw 链接、本地 sticker 路径、以图片扩展名结尾的 http(s) 链接。
     */
    private static final Pattern URL_LINE_PATTERN = Pattern.compile(
            "^(?:https?://\\S*(?:raw\\.githubusercontent\\.com/zhaoolee/ChineseBQB|github\\.com/zhaoolee/ChineseBQB)\\S*"
                    + "|https?://\\S+\\.(?:png|jpe?g|gif|webp)(?:\\?\\S*)?"
                    + "|/api/stickers/\\S*)$",
            Pattern.CASE_INSENSITIVE);

    private StickerSanitizer() {
    }

    /**
     * 清洗 assistant 消息文本：标记与裸 URL 行替换为 {@link #PLACEHOLDER}。
     *
     * @param text LLM 原文或库中历史文本（可为 null）
     * @return 清洗后的文本；入参为 null 时返回 null
     */
    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 1. 剥离所有 [[sticker:...]] 标记（无论内含情绪词还是完整 URL）
        String cleaned = MARKER_PATTERN.matcher(text).replaceAll(PLACEHOLDER);
        // 2. 逐行检查：整行是表情包 URL 的替换为占位（处理 LLM 裸输出 URL 的情况）
        if (cleaned.indexOf("http") >= 0 || cleaned.indexOf("/api/stickers/") >= 0) {
            String[] lines = cleaned.split("\n", -1);
            StringBuilder sb = null;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (URL_LINE_PATTERN.matcher(line.trim()).matches()) {
                    if (sb == null) {
                        sb = new StringBuilder(cleaned.length());
                        for (int j = 0; j < i; j++) {
                            sb.append(lines[j]).append('\n');
                        }
                    }
                    sb.append(PLACEHOLDER);
                } else if (sb != null) {
                    sb.append(line);
                }
                if (sb != null && i < lines.length - 1) {
                    sb.append('\n');
                }
            }
            if (sb != null) {
                cleaned = sb.toString();
            }
        }
        return cleaned;
    }

    /**
     * 判断文本（trim 后）是否是一个独立的表情包 URL——前端兜底渲染用同款规则的镜像。
     *
     * @param text 待判断文本
     * @return true 表示整条文本就是表情包图片地址
     */
    public static boolean isStickerUrl(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        return !t.isEmpty() && URL_LINE_PATTERN.matcher(t).matches();
    }
}
