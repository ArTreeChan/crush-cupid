package cn.yzfy.crushcupidserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @className OcrProperties
 * @description OCR 调用参数（{@code crush.ocr.*}）。
 * <p>
 * MCP 连接本身由 Spring AI 管理（{@code spring.ai.mcp.client.streamable-http.connections.*}，
 * 对应项目内 mcp-servers.json 所描述的百炼端点），本属性只控制业务侧开关与工具名。
 * @author 一朝风月
 * @code properties
 * @createTime 2026-08-27
 */
@Data
@ConfigurationProperties(prefix = "crush.ocr")
public class OcrProperties {

    /** 总开关：false 时强制禁用 OCR（即使 MCP 客户端已注册），上传回退默认解析 */
    private boolean enabled = true;

    /** 百炼「通用OCR文字识别」的 MCP 工具名 */
    private String toolName = "通用识别GPU";
}
