package cn.yzfy.crushcupidserver.controller;

import cn.yzfy.crushcupidserver.agent.CupidAgent;
import cn.yzfy.crushcupidserver.model.dto.ChatRequestDTO;
import cn.yzfy.crushcupidserver.model.dto.ProactiveRequestDTO;
import cn.yzfy.crushcupidserver.model.vo.MultiChunkVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @className ChatController
 * @description 对话接口（SSE 流式）。每个 chunk 以 {@link MultiChunkVO} JSON 编码下发到 data 行；
 * 前端按 index 切气泡，支持 crush 一次连发多条短消息。
 * <p>
 * 异步处理：用 {@link CompletableFuture} 在后台线程订阅 Flux，请求线程立即返回 {@link SseEmitter}，
 * 不阻塞 Servlet 容器线程；同时记录 TTFB（首字节时间）与总耗时便于定位慢调用。
 * @author crush-cupid
 * @code controller
 * @createTime 2026-08-26
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final CupidAgent cupidAgent;
    private final ObjectMapper objectMapper;

    /**
     * 用户主动对话（流式多条消息）。
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequestDTO dto) {
        return stream(cupidAgent.chat(dto), "/api/chat " + safeSlug(dto.getCrushSlug()));
    }

    /**
     * crush 主动发起对话（流式多条消息）。用户进入对话页或点击「等 ta 主动找我」时调用。
     */
    @PostMapping(value = "/proactive", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter proactive(@RequestBody ProactiveRequestDTO dto) {
        return stream(cupidAgent.proactive(dto), "/api/chat/proactive " + safeSlug(dto.getCrushSlug()));
    }

    /**
     * 把 MultiChunkVO 流接入 SseEmitter。
     * <p>
     * 用 CompletableFuture 异步订阅，请求线程立即返回 emitter；记录首字节 TTFB 与总耗时。
     */
    private SseEmitter stream(Flux<MultiChunkVO> flux, String tag) {
        long start = System.currentTimeMillis();
        SseEmitter emitter = new SseEmitter(300_000L);
        AtomicBoolean firstSent = new AtomicBoolean(false);

        CompletableFuture.runAsync(() -> flux
                .onBackpressureBuffer(1024)
                .doOnNext(c -> {
                    // 首 chunk 到达，记录 TTFB（用户从请求到看到第一个字的耗时）
                    if (firstSent.compareAndSet(false, true)) {
                        log.info("SSE [{}] 首字节 TTFB={}ms", tag, System.currentTimeMillis() - start);
                    }
                })
                .doOnComplete(() -> {
                    log.info("SSE [{}] 完成，总耗时={}ms", tag, System.currentTimeMillis() - start);
                    emitter.complete();
                })
                .doOnError(e -> {
                    log.error("SSE [{}] 异常，总耗时={}ms", tag, System.currentTimeMillis() - start, e);
                    emitter.completeWithError(e);
                })
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .data(objectMapper.writeValueAsString(chunk)));
                            } catch (Exception e) {
                                log.error("SSE [{}] send 失败，总耗时={}ms err={}", tag, System.currentTimeMillis() - start, e.getMessage());
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError
                )
        ).exceptionally(ex -> {
            log.error("SSE [{}] 订阅编排异常，总耗时={}ms", tag, System.currentTimeMillis() - start, ex);
            emitter.completeWithError(ex);
            return null;
        });

        return emitter;
    }

    /** slug 截断 + 防空，避免日志里出现 null 或超长 slug */
    private String safeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return "slug=?";
        }
        return "slug=" + (slug.length() > 32 ? slug.substring(0, 32) + "..." : slug);
    }
}
