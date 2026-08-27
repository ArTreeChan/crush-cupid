package cn.yzfy.crushcupidserver.config;

import cn.yzfy.crushcupidserver.agent.proactive.ProactiveProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * 主动消息调度并发执行器配置。
 * <p>
 * 阻塞治理核心一：把「LLM 决策 + 生成 + 落库 + 推送」这些阻塞调用从 {@code @Scheduled}
 * 的单线程里摘出来，放到虚拟线程上并行执行。虚拟线程（JDK21）挂起在阻塞 IO 上几乎零成本，
 * 不会像平台线程那样耗光调度线程池，天然适合 Spring AI {@code ChatClient} 的阻塞式调用。
 * <p>
 * 同时用 {@link Semaphore} 限制瞬时并发数，避免大量到期 crush 同时打爆 LLM 供应商 / 数据库。
 *
 * @author crush-cupid
 * @code config
 * @createTime 2026-08-27
 */
@Configuration
public class ProactiveExecutorConfig {

    /**
     * 虚拟线程执行器：每任务一虚拟线程，承载调度里每个 crush 的
     * 「decision → proactiveSilent → 推送」全流程（含阻塞 LLM / JDBC 调用）。
     * 仅需 JDK21（{@code Executors.newVirtualThreadPerTaskExecutor()}）。
     */
    @Bean("proactiveExecutor")
    public Executor proactiveExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /** 推送执行器（独立虚拟线程池）：隔离每个 SSE 连接的阻塞 send，避免互相拖累。 */
    @Bean("pushExecutor")
    public Executor pushExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 信号量限流：限制同时运行的主动任务数，默认取 {@link ProactiveProperties#getMaxConcurrent()}。
     * 虚拟线程在 Semaphore 上阻塞几乎零成本，配合扫描「提交即返回」实现可控并发而不丢任务。
     */
    @Bean("proactiveLimiter")
    public Semaphore proactiveLimiter(ProactiveProperties properties) {
        return new Semaphore(Math.max(1, properties.getMaxConcurrent()));
    }
}
