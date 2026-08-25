package cn.yzfy.crushcupidserver.skill;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存装饰器：在远程客户端外层加一层 TTL 内存缓存，不侵入远程实现。
 */
@Component
public class CachingSkillResourceClient implements SkillResourceClient {

    private final SkillResourceClient delegate;

    private final long ttlMillis;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public CachingSkillResourceClient(GitHubRawSkillClient delegate, SkillProperties properties) {
        this.delegate = delegate;
        this.ttlMillis = properties.getCacheTtl() * 1000;
    }

    @Override
    public String fetch(String path) {
        CacheEntry entry = cache.get(path);
        if (entry != null && entry.expireAt() > System.currentTimeMillis()) {
            return entry.content();
        }
        String content = delegate.fetch(path);
        cache.put(path, new CacheEntry(content, System.currentTimeMillis() + ttlMillis));
        return content;
    }

    private record CacheEntry(String content, long expireAt) {
    }
}
