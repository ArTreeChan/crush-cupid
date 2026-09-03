package cn.yzfy.crushcupidserver.config;

import cn.hutool.core.io.FileUtil;
import cn.yzfy.crushcupidserver.agent.StickerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @className WebMvcConfig
 * @description Web MVC 配置：表情包静态资源映射。
 * 把 /api/stickers/** 映射到 classpath:stickers/，前端 img 直连（走 /api 前缀复用现有代理），
 * 后端不做图片代理转发。
 * @author 一朝风月
 * @code config
 * @createTime 2026-08-27
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 表情包：classpath:stickers/ 下的静态资源（前端 img 直连）
        registry.addResourceHandler(StickerService.URL_PREFIX + "**")
                .addResourceLocations("classpath:/stickers/");
        // 对话图片：磁盘上传目录（/api/uploads/** -> file:./uploads/），历史回显用。
        // pattern 必须是 "前缀/**"（带斜杠）：拼成 "前缀**" 会因 AntPathMatcher 按段匹配
        // 无法命中多级子路径（如 /api/uploads/20260828/x.jpg），导致所有上传图片 404
        String prefix = uploadProperties.getUrlPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = UploadProperties.DEFAULT_URL_PREFIX;
        }
        String handler = prefix.endsWith("/") ? prefix + "**" : prefix + "/**";
        String location = "file:" + FileUtil.getAbsolutePath(uploadProperties.getDir()).replace('\\', '/');
        if (!location.endsWith("/")) {
            location += "/";
        }
        log.info("注册静态资源映射：handler={} -> location={} (dir={}, urlPrefix={})",
                handler, location, uploadProperties.getDir(), prefix);
        registry.addResourceHandler(handler).addResourceLocations(location);
        // 关系分析 HTML 报告：/api/uploads/relationship/** -> file:D:/uploads/relationship/
        registry.addResourceHandler("/api/uploads/relationship/**")
                .addResourceLocations("file:D:/uploads/relationship/");
    }
}
