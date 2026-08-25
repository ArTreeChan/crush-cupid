package cn.yzfy.crushcupidserver.controller;

import cn.yzfy.crushcupidserver.agent.CupidAgent;
import cn.yzfy.crushcupidserver.model.dto.ChatRequestDTO;
import cn.yzfy.crushcupidserver.model.dto.ProactiveRequestDTO;
import cn.yzfy.crushcupidserver.model.vo.MultiChunkVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * @className ChatController
 * @description 对话接口（SSE 流式）。每个 chunk 以 {@link MultiChunkVO} JSON 编码下发到 data 行；
 * 前端按 index 切气泡，支持 crush 一次连发多条短消息。
 * <p>
 * 设超时 + 背压缓冲，避免慢客户端阻塞线程。
 * @author crush-cupid
 * @code controller
 * @createTime 2026-08-26
 */
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
        return stream(cupidAgent.chat(dto));
    }

    /**
     * crush 主动发起对话（流式多条消息）。用户进入对话页或点击「等 ta 主动找我」时调用。
     */
    @PostMapping(value = "/proactive", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter proactive(@RequestBody ProactiveRequestDTO dto) {
        return stream(cupidAgent.proactive(dto));
    }

    /**
     * 把 MultiChunkVO 流接入 SseEmitter，5 分钟超时，背压缓冲 1024。
     */
    private SseEmitter stream(reactor.core.publisher.Flux<MultiChunkVO> flux) {
        SseEmitter emitter = new SseEmitter(300_000L);
        flux
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
