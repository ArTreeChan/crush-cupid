package cn.yzfy.crushcupidserver.agent.proactive;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;

/**
 * 主动消息推送中心（SSE 常驻连接注册表）。
 * <p>
 * 前端登录后为每个正在查看/关注的 crush 建立一条常驻 SSE（{@code GET /api/push/listen?crushSlug=xx}），
 * 调度器生成新主动消息后调用 {@link #broadcast(String, String)} 把新气泡推给对应连接的页面。
 * <p>
 * 阻塞治理：每个连接的 {@code emitter.send}（同步 socket 写）单独投递到虚拟线程 {@code pushExecutor} 上执行，
 * 一个慢/卡住的连接不会阻塞广播到其它连接，也不会占用调度线程。
 *
 * @author crush-cupid
 * @code service
 * @createTime 2026-08-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProactivePushService {

    /** crushSlug -> 该 crush 当前挂着的 SSE 连接集合 */
    private final ConcurrentHashMap<String, Set<SseEmitter>> listeners = new ConcurrentHashMap<>();

    /** 推送执行器（虚拟线程），隔离每个连接的阻塞 send */
    @Qualifier("pushExecutor")
    private final Executor pushExecutor;

    /**
     * 注册一条对指定 crush 的常驻 SSE 连接。
     *
     * @param crushSlug 暗恋对象 slug
     * @param emitter   前端连接的 SseEmitter
     */
    public SseEmitter register(String crushSlug) {
        SseEmitter emitter = new SseEmitter(0L); // 0 = 不超时
        Set<SseEmitter> set = listeners.computeIfAbsent(crushSlug, k -> new CopyOnWriteArraySet<>());
        set.add(emitter);
        emitter.onCompletion(() -> remove(crushSlug, emitter));
        emitter.onTimeout(() -> remove(crushSlug, emitter));
        emitter.onError(e -> remove(crushSlug, emitter));
        return emitter;
    }

    /**
     * 向监听某 crush 的所有连接广播一条主动消息事件。
     * <p>
     * payload 为当前生成的一条（或连发的多条）主动消息文本，前端收到后重新拉取该 crush 历史即可。
     */
    public void broadcast(String crushSlug, String payload) {
        Set<SseEmitter> set = listeners.get(crushSlug);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : set) {
            pushExecutor.execute(() -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name(ProactiveProperties.EVENT_TYPE)
                            .data(payload, MediaType.TEXT_PLAIN));
                } catch (IOException | IllegalStateException e) {
                    log.debug("推送主动消息失败 crush={} err={}", crushSlug, e.getMessage());
                    remove(crushSlug, emitter);
                }
            });
        }
    }

    /** 给所有已注册连接发送心跳注释，维持连接存活性。 */
    public void heartbeatAll() {
        listeners.forEach((slug, set) -> {
            for (SseEmitter emitter : set) {
                pushExecutor.execute(() -> {
                    try {
                        emitter.send(SseEmitter.event().comment("ping"));
                    } catch (IOException | IllegalStateException e) {
                        remove(slug, emitter);
                    }
                });
            }
        });
    }

    private void remove(String crushSlug, SseEmitter emitter) {
        Set<SseEmitter> set = listeners.get(crushSlug);
        if (set == null) {
            return;
        }
        set.remove(emitter);
        if (set.isEmpty()) {
            listeners.remove(crushSlug, set);
        }
    }
}

