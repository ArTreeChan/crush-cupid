package cn.yzfy.crushcupidserver.config;

import cn.yzfy.crushcupidserver.agent.proactive.ProactiveProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

/**
 * @className ThreadPoolsConfig
 * @description 全局线程池统一配置。所有异步执行器集中在此定义，便于监控与调优。
 * <p>
 * 设计原则：
 * <ol>
 *   <li><b>命名线程</b>：所有虚拟线程带前缀（sse- / ai- / proactive- / push-），线程 dump 可读</li>
 *   <li><b>信号量限流</b>：SSE 与主动消息各有并发上限，超过拒绝而非排队（快速失败）</li>
 *   <li><b>虚拟线程</b>：JDK21 虚拟线程在阻塞 IO 上挂起零成本，天然适合 Spring AI 阻塞式调用</li>
 *   <li><b>隔离</b>：SSE 订阅、AI 调度、主动消息、推送各自独立池，互不拖累</li>
 * </ol>
 *
 * @author 一朝风月
 * @code config
 * @createTime 2026-08-27
 */
@Slf4j
@Configuration
public class ThreadPoolsConfig {

    /** SSE 并发上限（同时进行的流式对话数） */
    public static final int SSE_MAX_CONCURRENT = 200;
    /** SSE 单次超时（ms） */
    public static final long SSE_TIMEOUT = 300_000L;
    /** LLM 流式调用超时（分钟），超时后自动取消订阅释放资源 */
    public static final java.time.Duration LLM_TIMEOUT = java.time.Duration.ofMinutes(5);
    /** backpressure 缓冲上限 */
    public static final int BACKPRESSURE_BUFFER = 4096;

    private volatile ExecutorService sseExecutor;
    private volatile ExecutorService aiExecutor;
    private volatile ExecutorService proactiveExecutor;
    private volatile ExecutorService pushExecutor;

    /**
     * SSE 流式订阅专用虚拟线程池。每个流式对话一虚拟线程，承载 Flux 订阅 + emitter.send。
     * 配合 {@link #sseLimiter} 信号量限制并发上限。
     */
    @Bean("sseExecutor")
    public ExecutorService sseExecutor() {
        if (sseExecutor == null) {
            ThreadFactory tf = Thread.ofVirtual().name("sse-", 0).factory();
            sseExecutor = Executors.newThreadPerTaskExecutor(tf);
            log.info("SSE 执行器就绪：虚拟线程池，并发上限={}", SSE_MAX_CONCURRENT);
        }
        return sseExecutor;
    }

    /**
     * AI/DB 调度专用虚拟线程调度器（替代 Reactor 默认 boundedElastic）。
     * 用于 CupidAgent 的 Mono.fromCallable（DB 查询 + ChatClient 构造）与
     * streamMulti 的 publishOn（切分 + emitter.send 前置处理）。
     */
    @Bean("aiScheduler")
    public Scheduler aiScheduler() {
        if (aiExecutor == null) {
            ThreadFactory tf = Thread.ofVirtual().name("ai-", 0).factory();
            aiExecutor = Executors.newThreadPerTaskExecutor(tf);
            log.info("AI 调度器就绪：虚拟线程池");
        }
        return Schedulers.fromExecutorService(aiExecutor, "ai-scheduler");
    }

    /**
     * 主动消息调度执行器：每任务一虚拟线程，承载「decision → proactiveSilent → 推送」全流程。
     */
    @Bean("proactiveExecutor")
    public Executor proactiveExecutor() {
        if (proactiveExecutor == null) {
            ThreadFactory tf = Thread.ofVirtual().name("proactive-", 0).factory();
            proactiveExecutor = Executors.newThreadPerTaskExecutor(tf);
        }
        return proactiveExecutor;
    }

    /** 推送执行器（独立虚拟线程池）：隔离每个 SSE 连接的阻塞 send，避免互相拖累。 */
    @Bean("pushExecutor")
    public Executor pushExecutor() {
        if (pushExecutor == null) {
            ThreadFactory tf = Thread.ofVirtual().name("push-", 0).factory();
            pushExecutor = Executors.newThreadPerTaskExecutor(tf);
        }
        return pushExecutor;
    }

    /**
     * SSE 并发限流信号量（公平模式）。
     * 超过上限的请求立即返回 503，不排队等待——快速失败优于无限排队。
     */
    @Bean("sseLimiter")
    public Semaphore sseLimiter() {
        return new Semaphore(SSE_MAX_CONCURRENT, true);
    }

    /**
     * 主动消息并发限流信号量，默认取 {@link ProactiveProperties#getMaxConcurrent()}。
     * 标记 @Primary：当其他组件按类型注入 Semaphore 且未指定 @Qualifier 时（如
     * ProactiveSchedulerService 的 @RequiredArgsConstructor），默认拿到此 bean；
     * ChatController 已显式 @Qualifier("sseLimiter")，不受 @Primary 影响。
     */
    @Bean("proactiveLimiter")
    @Primary
    public Semaphore proactiveLimiter(ProactiveProperties properties) {
        return new Semaphore(Math.max(1, properties.getMaxConcurrent()), true);
    }

    @PreDestroy
    public void shutdown() {
        shutdownQuietly(sseExecutor, "sseExecutor");
        shutdownQuietly(aiExecutor, "aiExecutor");
        shutdownQuietly(proactiveExecutor, "proactiveExecutor");
        shutdownQuietly(pushExecutor, "pushExecutor");
    }

    private void shutdownQuietly(ExecutorService pool, String name) {
        if (pool != null) {
            pool.shutdown();
            log.info("{} 已关闭", name);
        }
    }
}
