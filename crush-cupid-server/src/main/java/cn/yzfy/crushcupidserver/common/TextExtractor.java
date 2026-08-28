package cn.yzfy.crushcupidserver.common;

import java.nio.charset.StandardCharsets;

/**
 * @className TextExtractor
 * @description 文本文件内容抽取工具：按 BOM 检测编码（UTF-8 / UTF-16LE / UTF-16BE），并清洗 NUL 字节
 * （PostgreSQL TEXT 列不支持 NUL）。供构建资料上传与聊天附件解析共用。
 * @author 一朝风月
 * @code util
 * @createTime 2026-08-27
 */
public final class TextExtractor {

    private TextExtractor() {
    }

    /**
     * 从原始字节中抽取文本内容：识别 BOM 选择字符集，无 BOM 按 UTF-8，并移除 NUL 字符。
     */
    public static String extract(byte[] bytes) {
        String content;
        if (startsWith(bytes, 0xFF, 0xFE)) {
            content = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        } else if (startsWith(bytes, 0xFE, 0xFF)) {
            content = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        } else if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
            content = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        } else {
            content = new String(bytes, StandardCharsets.UTF_8);
        }
        return sanitize(content);
    }

    /**
     * 移除 NUL 字符（0x00）。
     */
    public static String sanitize(String s) {
        if (s == null) {
            return null;
        }
        return s.replace(String.valueOf((char) 0), "");
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
