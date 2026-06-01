package com.dcb.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 */
@Configuration
public class AsyncConfig {

    /** 分片计算线程池 */
    @Bean("calcTaskExecutor")
    @Primary
    public Executor calcTaskExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        int coreSize = Math.max(cores, 4);
        int maxSize = coreSize * 2;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);          // 核心线程数
        executor.setMaxPoolSize(maxSize);             // 最大线程数
        executor.setQueueCapacity(100);               // 队列容量
        executor.setKeepAliveSeconds(60);             // 空闲线程存活时间
        executor.setThreadNamePrefix("calc-");        // 线程名前缀
        executor.setRejectedExecutionHandler(         // 拒绝策略：由调用线程执行（兜底）
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
