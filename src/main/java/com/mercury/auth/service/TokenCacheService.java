package com.mercury.auth.service;

import com.mercury.auth.util.TokenHashUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

/**
 * Service for caching validated JWT token claims to improve performance.
 * Reduces the need to parse and validate the same token multiple times.
 */
@Service
@RequiredArgsConstructor
public class TokenCacheService {

    private static final Logger logger = LoggerFactory.getLogger(TokenCacheService.class);
    private static final String TOKEN_CACHE_NAME = "tokenCache";
    
    private final CacheManager cacheManager;

    /**
     * Get cached token claims if available.
     * Returns null if not in cache, requiring full validation.
     */
    public Claims getCachedClaims(String tokenHash) {
        Cache cache = cacheManager.getCache(TOKEN_CACHE_NAME);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(tokenHash);
            if (wrapper != null) {
                logger.debug("Token cache hit for hash: {}", tokenHash.substring(0, Math.min(10, tokenHash.length())));
                return (Claims) wrapper.get();
            }
        }
        logger.debug("Token cache miss for hash: {}", tokenHash.substring(0, Math.min(10, tokenHash.length())));
        return null;
    }

    /**
     * Cache validated token claims for future requests.
     */
    public void cacheClaims(String tokenHash, Claims claims) {
        Cache cache = cacheManager.getCache(TOKEN_CACHE_NAME);
        if (cache != null) {
            cache.put(tokenHash, claims);
            logger.debug("Token claims cached for hash: {}", tokenHash.substring(0, Math.min(10, tokenHash.length())));
        }
    }

    /**
     * Evict token from cache when it's blacklisted.
     * This ensures blacklisted tokens are immediately invalidated.
     * 
     * Note: This method uses both @CacheEvict annotation (for Spring-managed caches) 
     * and manual eviction (for test scenarios with simple cache managers).
     */
    @CacheEvict(value = TOKEN_CACHE_NAME, key = "#tokenHash")
    public void evictToken(String tokenHash) {
        // Manual eviction ensures compatibility with non-Spring cache managers (e.g., in tests)
        Cache cache = cacheManager.getCache(TOKEN_CACHE_NAME);
        if (cache != null) {
            cache.evict(tokenHash);
        }
        logger.debug("Token evicted from cache: {}", tokenHash.substring(0, Math.min(10, tokenHash.length())));
    }

    /**
     * Hash token to create a cache key.
     * Delegates to TokenHashUtil for consistent hashing across the application.
     */
    public String hashToken(String token) {
        return TokenHashUtil.hashToken(token);
    }
}
