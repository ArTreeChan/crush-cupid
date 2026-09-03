package cn.yzfy.crushcupidserver.skill;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;

/**
 * GitHub raw 适配器：把远程 skill 资源拉成原始文本。
 * <p>
 * 兼容多种 base-url 写法，统一归一化成「raw 目录地址」：
 * <ul>
 *   <li>https://github.com/{owner}/{repo}/tree/{branch}</li>
 *   <li>https://raw.githubusercontent.com/{owner}/{repo}/{branch}</li>
 *   <li>https://raw.githubusercontent.com/{owner}/{repo}/refs/heads/{branch}</li>
 *   <li>…/SKILL.md（直接指向文件的完整地址，会自动去掉文件名）</li>
 * </ul>
 */
@Component
public class GitHubRawSkillClient implements SkillResourceClient {

    private final RestClient restClient;

    private final String baseUrl;

    public GitHubRawSkillClient(RestClient.Builder builder, SkillProperties properties) {
        this.baseUrl = normalizeBaseUrl(properties.getBaseUrl());
        this.restClient = builder.build();
    }

    @Override
    public String fetch(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        return restClient.get().uri(URI.create(baseUrl + "/" + p)).retrieve().body(String.class);
    }

    /**
     * 归一化成 raw 目录地址（不含末尾文件名、不含 web 视图段）。
     */
    private static String normalizeBaseUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return url;
        }
        String u = url.trim().replaceAll("/+$", "");

        if (u.startsWith("https://github.com/")) {
            u = "https://raw.githubusercontent.com/" + u.substring("https://github.com/".length());
        }

        // 去掉末尾的具体文件名（如 SKILL.md / prompts/xxx.md）
        if (u.matches(".*/[^/]+\\.(md|py|txt|json|yaml|yml)$")) {
            u = u.substring(0, u.lastIndexOf('/'));
        }

        // raw 地址里出现 /tree/{branch} 段时，去掉 "tree"
        u = u.replace("/tree/", "/");

        return u;
    }
}
