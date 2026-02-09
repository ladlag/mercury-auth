package com.mercury.auth.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for token validation performance optimization.
 * Uses Caffeine as the cache implementation for high-performance in-memory caching.
 * 
 * Supports independent TTL configuration for different cache types:
 * - Token caches: Short TTL (default 10 minutes) for security
 * - Tenant status cache: Longer TTL (default 30 minutes) for performance
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${security.token-cache.max-size:10000}")
    private long maxCacheSize;

    @Value("${security.token-cache.expire-after-write-seconds:600}")
    private long tokenExpireAfterWriteSeconds;

    @Value("${security.tenant-cache.max-size:1000}")
    private long tenantCacheMaxSize;

    @Value("${security.tenant-cache.expire-after-write-seconds:1800}")
    private long tenantExpireAfterWriteSeconds;

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        
        // Token-related caches with shorter TTL
        Cache tokenCache = buildCache("tokenCache", maxCacheSize, tokenExpireAfterWriteSeconds);
        Cache tokenVerifyCache = buildCache("tokenVerifyCache", maxCacheSize, tokenExpireAfterWriteSeconds);
        Cache userStatusCache = buildCache("userStatusCache", maxCacheSize, tokenExpireAfterWriteSeconds);
        
        // Tenant status cache with longer TTL for better performance
        Cache tenantStatusCache = buildCache("tenantStatusCache", tenantCacheMaxSize, tenantExpireAfterWriteSeconds);
        
        cacheManager.setCaches(Arrays.asList(
                tokenCache,
                tokenVerifyCache,
                userStatusCache,
                tenantStatusCache
        ));
        
        return cacheManager;
    }

    private Cache buildCache(String name, long maximumSize, long expireAfterWriteSeconds) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireAfterWriteSeconds, TimeUnit.SECONDS)
                .recordStats()  // Enable cache statistics for monitoring
                .build());
    }
}
