package cn.yzfy.crushcupidserver.agent.tool;

import cn.yzfy.crushcupidserver.agent.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * @className OcrTools
 * @description OCR 工具封装：把阿里云百炼 MCP「通用OCR文字识别」暴露为 Agent 可调用的
 * {@link Tool}，同时供文件上传流程（CrushSourceController）直接复用 OcrService。
 * <p>
 * 降级策略：OCR 未配置时工具返回提示文本而非抛异常，避免 LLM 工具调用链路 500。
 * @author 一朝风月
 * @code tool
 * @createTime 2026-08-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrTools {

    private final OcrService ocrService;

    /**
     * 识别图片中的文字（支持 base64 / 公网 URL）。
     */
    @Tool(description = "识别图片中的文字内容（OCR）。传入图片二进制的 base64 编码或公网可访问的图片 URL，返回识别出的文字。适用于聊天截图、照片等场景")
    public String recognizeImageText(
            @ToolParam(description = "图片二进制数据的 base64 编码 / 图片 url") String image) {
        if (!ocrService.available()) {
            return "OCR 能力未配置（spring.ai.mcp.client.streamable-http.connections 未配置百炼 MCP 端点），无法识别图片文字";
        }
        try {
            return ocrService.recognize(image);
        } catch (Exception e) {
            log.warn("OCR 工具调用失败：{}", e.getMessage());
            return "OCR 识别失败：" + e.getMessage();
        }
    }
}
