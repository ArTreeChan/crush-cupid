package cn.yzfy.crushcupidserver.agent;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.yzfy.crushcupidserver.config.StickerProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @className StickerService
 * @description 表情包素材服务，双源：
 * <ol>
 *   <li>本地 manifest：classpath:stickers/manifest.json（情绪 -> 文件名），图片经 /api/stickers/** 暴露</li>
 *   <li>ChineseBQB 远端仓库：classpath:stickers/chinesebqb-manifest.json 预缓存了全部主题的
 *       raw URL 列表（启动时一次性加载，运行时零 GitHub API 调用）。
 *       按情绪映射到主题，从该主题的 URL 列表随机抽一张。</li>
 * </ol>
 * {@link #randomSticker} 本地优先，远端兜底；两源都不可用返回 null。
 * @author 一朝风月
 * @code service
 * @createTime 2026-08-27
 */
@Slf4j
@Service
public class StickerService {

    /** 本地 manifest 在 classpath 的位置 */
    private static final String MANIFEST_PATH = "stickers/manifest.json";
    /** ChineseBQB 预缓存 manifest（全部主题 -> raw URL 列表） */
    private static final String CHINESEBQB_MANIFEST_PATH = "stickers/chinesebqb-manifest.json";
    /** 本地图片对外访问路径前缀（与 WebMvcConfig 的静态映射一致） */
    public static final String URL_PREFIX = "/api/stickers/";

    private final ObjectMapper objectMapper;
    private final StickerProperties properties;

    /** 本地情绪 -> 文件名列表 */
    private final Map<String, List<String>> localEmotions = new ConcurrentHashMap<>();
    /** ChineseBQB 主题 -> raw URL 列表（启动时从预缓存 manifest 一次性加载） */
    private final Map<String, List<String>> remoteTopics = new ConcurrentHashMap<>();

    public StickerService(ObjectMapper objectMapper, StickerProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    public void load() {
        loadLocalManifest();
        loadChineseBQBManifest();
    }

    /**
     * 加载本地 manifest（情绪 -> 本地文件名）。
     */
    private void loadLocalManifest() {
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource(MANIFEST_PATH).getInputStream());
            JsonNode node = root.path("emotions");
            node.properties().forEach(e -> {
                List<String> files = new ArrayList<>();
                e.getValue().forEach(f -> {
                    if (f.isTextual() && StrUtil.isNotBlank(f.asText())) {
                        files.add(f.asText());
                    }
                });
                localEmotions.put(e.getKey(), files);
            });
            long total = localEmotions.values().stream().mapToInt(List::size).sum();
            log.info("本地表情包 manifest 加载：{} 种情绪，共 {} 张图", localEmotions.size(), total);
        } catch (Exception e) {
            log.warn("本地表情包 manifest 加载失败：{}", e.getMessage());
        }
    }

    /**
     * 加载 ChineseBQB 预缓存 manifest（主题 -> raw URL 列表）。
     * 这个 manifest 是预先用 GitHub trees API 拉取并保存到 classpath 的，
     * 启动时一次性加载，运行时零 GitHub API 调用。
     * <p>
     * URL 转换：manifest 里的原始 URL 是 {@code raw.githubusercontent.com}，国内无法直连。
     * 加载时统一替换为 jsdelivr CDN（{@code cdn.jsdelivr.net/gh}），前端可直接加载 GIF。
     */
    private void loadChineseBQBManifest() {
        if (!properties.getChinesebqb().isEnabled()) {
            log.info("ChineseBQB 远端源已禁用");
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource(CHINESEBQB_MANIFEST_PATH).getInputStream());
            root.fields().forEachRemaining(e -> {
                List<String> urls = new ArrayList<>();
                e.getValue().forEach(u -> {
                    if (u.isTextual() && StrUtil.isNotBlank(u.asText())) {
                        urls.add(toCdnUrl(u.asText()));
                    }
                });
                if (!urls.isEmpty()) {
                    remoteTopics.put(e.getKey(), urls);
                }
            });
            long total = remoteTopics.values().stream().mapToInt(List::size).sum();
            log.info("ChineseBQB manifest 加载：{} 个主题，共 {} 张图（jsdelivr CDN，运行时零 API 调用）",
                    remoteTopics.size(), total);
        } catch (Exception e) {
            log.warn("ChineseBQB manifest 加载失败（远端源不可用）：{}", e.getMessage());
        }
    }

    /**
     * 把 GitHub raw URL 替换为 jsdelivr CDN URL，国内可直连。
     * <p>
     * 转换规则：
     * {@code https://raw.githubusercontent.com/{user}/{repo}/{branch}/{path}}
     * → {@code https://cdn.jsdelivr.net/gh/{user}/{repo}@{branch}/{path}}
     * <p>
     * 非 GitHub raw URL 原样返回。
     */
    private static String toCdnUrl(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        final String RAW_PREFIX = "https://raw.githubusercontent.com/";
        if (!rawUrl.startsWith(RAW_PREFIX)) {
            return rawUrl;
        }
        // zhaoolee/ChineseBQB/master/path... -> zhaoolee/ChineseBQB@master/path...
        String rest = rawUrl.substring(RAW_PREFIX.length());
        int firstSlash = rest.indexOf('/');
        int secondSlash = rest.indexOf('/', firstSlash + 1);
        if (firstSlash < 0 || secondSlash < 0) {
            return rawUrl;
        }
        String user = rest.substring(0, firstSlash);
        String repo = rest.substring(firstSlash + 1, secondSlash);
        String branchAndPath = rest.substring(secondSlash + 1);
        int thirdSlash = branchAndPath.indexOf('/');
        if (thirdSlash < 0) {
            return rawUrl;
        }
        String branch = branchAndPath.substring(0, thirdSlash);
        String path = branchAndPath.substring(thirdSlash + 1);
        return "https://cdn.jsdelivr.net/gh/" + user + "/" + repo + "@" + branch + "/" + path;
    }

    /**
     * 按情绪随机抽取一张表情包（本地优先，远端兜底）。
     *
     * @param emotion 情绪词（如 开心/可爱/无语）
     * @return 图片 URL（本地 /api/stickers/... 或远端 raw URL）；两源都无返回 null
     */
    public String randomSticker(String emotion) {
        // 1. 本地 manifest 优先
        List<String> files = localEmotions.get(emotion);
        if (files != null && !files.isEmpty()) {
            return URL_PREFIX + files.get(ThreadLocalRandom.current().nextInt(files.size()));
        }
        // 2. ChineseBQB 远端兜底：按情绪查主题
        String topic = properties.getChinesebqb().getTopics().get(emotion);
        if (StrUtil.isNotBlank(topic)) {
            List<String> urls = remoteTopics.get(topic);
            if (urls != null && !urls.isEmpty()) {
                return urls.get(ThreadLocalRandom.current().nextInt(urls.size()));
            }
        }
        return null;
    }

    /**
     * 按情绪抽取一张（语义同 {@link #randomSticker}，供 StickerTools 调用）。
     */
    public String pickStickerUrl(String emotion) {
        return randomSticker(emotion);
    }

    /**
     * 是否有任何可用素材（本地或远端任一可用）。
     */
    public boolean available() {
        if (localEmotions.values().stream().anyMatch(files -> !files.isEmpty())) {
            return true;
        }
        StickerProperties.ChineseBQB cfg = properties.getChinesebqb();
        if (!cfg.isEnabled()) {
            return false;
        }
        for (String topic : cfg.getTopics().values()) {
            if (remoteTopics.containsKey(topic)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 可用情绪词列表（本地非空 + 远端映射 keys），供 persona 指引枚举。
     */
    public List<String> availableEmotions() {
        List<String> list = new ArrayList<>();
        localEmotions.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .forEach(list::add);
        StickerProperties.ChineseBQB cfg = properties.getChinesebqb();
        if (cfg.isEnabled()) {
            cfg.getTopics().keySet().forEach(k -> {
                if (!list.contains(k)) {
                    list.add(k);
                }
            });
        }
        return list;
    }
}
