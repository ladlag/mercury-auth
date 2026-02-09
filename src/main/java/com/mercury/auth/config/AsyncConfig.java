package com.mercury.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for non-blocking operations like audit logging
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Thread pool executor for async audit logging
     * Configured with appropriate pool sizes and queue capacity for high-throughput scenarios
     */
    @Bean(name = "auditLogExecutor")
    public Executor auditLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);  // Minimum threads
        executor.setMaxPoolSize(5);   // Maximum threads for burst traffic
        executor.setQueueCapacity(1000);  // Queue size to buffer logs during spikes
        executor.setThreadNamePrefix("audit-log-");
        executor.setWaitForTasksToCompleteOnShutdown(true);  // Ensure logs are written on shutdown
        executor.setAwaitTerminationSeconds(60);  // Wait up to 60s for pending logs
        executor.initialize();
        return executor;
    }
}
