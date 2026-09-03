package cn.yzfy.crushcupidserver.config;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpRequest;

/**
 * @className McpAuthConfig
 * @description MCP HTTP 请求鉴权定制器。Spring AI 1.1.2 的 streamable-http 连接配置
 * 不支持 headers 属性，百炼 MCP 需要 {@code Authorization: Bearer <key>}，
 * 因此通过 {@link McpSyncHttpClientRequestCustomizer}（autoconfig 以 ifUnique 注入 transport）
 * 统一补齐鉴权与 Accept 头。
 * <p>
 * 鉴权 Key 与百炼平台一致，复用 DASHSCOPE_API_KEY（对应 mcp-servers.json 中的 Bearer 模板）。
 * @author 一朝风月
 * @code configuration
 * @createTime 2026-08-27
 */
@Configuration
public class McpAuthConfig {

    @Value("${DASHSCOPE_API_KEY:}")
    private String dashscopeApiKey;

    /**
     * 为所有 MCP streamableHttp 请求注入鉴权头。Key 未配置时不加头（连接会被服务端 401 拒绝，日志可见）。
     */
    @Bean
    public McpSyncHttpClientRequestCustomizer mcpAuthHeaderCustomizer() {
        return (HttpRequest.Builder builder, String method, java.net.URI uri,
                String connectionName, io.modelcontextprotocol.common.McpTransportContext ctx) -> {
            if (dashscopeApiKey != null && !dashscopeApiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + dashscopeApiKey);
            }
            builder.header("Accept", "application/json, text/event-stream");
        };
    }
}
