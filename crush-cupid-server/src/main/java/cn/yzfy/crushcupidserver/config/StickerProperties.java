package cn.yzfy.crushcupidserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @className StickerProperties
 * @description 表情包配置：本地 manifest（resources/stickers/）+ 远端 ChineseBQB 仓库双源。
 * <p>
 * 远端源约定：
 * <pre>
 * crush.sticker.chinesebqb:
 *   enabled: true
 *   repo-owner: zhaoolee
 *   repo-name: ChineseBQB
 *   branch: master
 *   topics:           # 情绪 -> 仓库主题目录名映射
 *     开心: 001Funny_滑稽大佬😏BQB
 *     可爱: 002CuteGirl_可爱的女孩纸👧BQB
 * </pre>
 * StickerService.pickSticker 按情绪查主题，懒加载该主题的文件列表（GitHub API）+ 缓存，
 * 随机抽一张拼 raw URL 返回。本地 manifest 优先（若配置了），远端兜底。
 * @author 一朝风月
 * @code config
 * @createTime 2026-08-27
 */
@Data
@Component
@ConfigurationProperties(prefix = "crush.sticker")
public class StickerProperties {

    /**
     * 远端 ChineseBQB 仓库配置。
     */
    private ChineseBQB chinesebqb = new ChineseBQB();

    @Data
    public static class ChineseBQB {
        /** 是否启用远端源（默认 true，拉取失败自动降级） */
        private boolean enabled = true;
        /** 仓库 owner */
        private String repoOwner = "zhaoolee";
        /** 仓库名 */
        private String repoName = "ChineseBQB";
        /** 分支 */
        private String branch = "master";
        /** 主题文件列表缓存时长（秒），默认 1 小时 */
        private long cacheTtlSeconds = 3600L;
        /** 情绪 -> 主题目录名映射（用户可按仓库实际目录调整） */
        private Map<String, String> topics = new LinkedHashMap<>();
    }
}
