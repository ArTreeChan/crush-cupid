package cn.yzfy.crushcupidserver.controller;

import cn.yzfy.crushcupidserver.agent.CupidAgent;
import cn.yzfy.crushcupidserver.model.dto.ChatRequestDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话接口（SSE 流式）。
 * <p>
 * 每个 chunk 用 JSON 编码，避免换行破坏 SSE 数据行；设超时 + 背压缓冲，避免慢客户端阻塞线程。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final CupidAgent cupidAgent;
    private final ObjectMapper objectMapper;

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequestDTO dto) {
        // 5 分钟超时，客户端断开后不长时间占用连接
        SseEmitter emitter = new SseEmitter(300_000L);

        cupidAgent.chat(dto)
                // 慢客户端时缓冲 chunk，避免 emitter.send 阻塞
                .onBackpressureBuffer(1024)
                .doOnComplete(emitter::complete)
                .doOnError(emitter::completeWithError)
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(chunk)));
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError
                );
        return emitter;
    }
}
