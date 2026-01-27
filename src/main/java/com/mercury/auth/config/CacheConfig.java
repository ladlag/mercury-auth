package com.mercury.auth.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for token validation performance optimization.
 * Uses Caffeine as the cache implementation for high-performance in-memory caching.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${security.token-cache.max-size:10000}")
    private long maxCacheSize;

    @Value("${security.token-cache.expire-after-write-seconds:300}")
    private long expireAfterWriteSeconds;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("tokenCache");
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }

    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .maximumSize(maxCacheSize)
                .expireAfterWrite(expireAfterWriteSeconds, TimeUnit.SECONDS)
                .recordStats(); // Enable cache statistics for monitoring
    }
}
