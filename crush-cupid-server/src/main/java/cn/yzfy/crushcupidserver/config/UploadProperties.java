package cn.yzfy.crushcupidserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @className UploadProperties
 * @description 图片上传落盘配置：对话中发送的图片（base64）持久化到服务端磁盘目录，
 * 返回可访问 URL（/api/uploads/...）供历史回显。
 * 由 {@code CrushCupidServerApplication} 通过 {@code @EnableConfigurationProperties} 注册。
 * @author 一朝风月
 * @code config
 * @createTime 2026-08-28
 */
@Data
@ConfigurationProperties(prefix = "crush.upload")
public class UploadProperties {

    /** 对外访问 URL 前缀默认值（与 WebMvcConfig 静态映射对齐） */
    public static final String DEFAULT_URL_PREFIX = "/api/uploads";

    /** 图片落盘根目录（相对/绝对路径均可），默认运行目录下 uploads */
    private String dir = "./uploads";

    /** 对外访问 URL 前缀，默认 /api/uploads（与 WebMvcConfig 静态映射对齐） */
    private String urlPrefix = DEFAULT_URL_PREFIX;
}
