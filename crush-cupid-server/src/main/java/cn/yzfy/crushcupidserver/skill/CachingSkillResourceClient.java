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
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(path);
        if (entry != null && entry.expireAt() > now) {
            return entry.content();
        }
        // 原子加载：compute 对同一 path 是原子的，命中失效时只允许一个线程拉远端，
        // 其余线程阻塞等待拿到新值，避免并发重复请求远程。
        CacheEntry fresh = cache.compute(path, (k, old) -> {
            if (old != null && old.expireAt() > now) {
                return old;
            }
            return new CacheEntry(delegate.fetch(path), now + ttlMillis);
        });
        return fresh.content();
    }

    private record CacheEntry(String content, long expireAt) {
    }
}
