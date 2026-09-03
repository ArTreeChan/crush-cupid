package cn.yzfy.crushcupidserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置：放开前端跨域访问（含 SSE 需要的响应头）。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 用 allowedOriginPatterns 支持通配，避免 allowedOrigins("*") 与 credentials 冲突
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 暴露 SSE 等响应头给前端
                .exposedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
