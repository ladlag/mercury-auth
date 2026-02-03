package com.mercury.auth.service;

import com.mercury.auth.dto.TokenVerifyResponse;
import com.mercury.auth.util.TokenHashUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
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
    private static final String TOKEN_VERIFY_CACHE_NAME = "tokenVerifyCache";
    
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
                logger.debug("Token cache hit for hash: {}", getSafeHashPrefix(tokenHash));
                return (Claims) wrapper.get();
            }
        }
        logger.debug("Token cache miss for hash: {}", getSafeHashPrefix(tokenHash));
        return null;
    }

    /**
     * Cache validated token claims for future requests.
     */
    public void cacheClaims(String tokenHash, Claims claims) {
        Cache cache = cacheManager.getCache(TOKEN_CACHE_NAME);
        if (cache != null) {
            cache.put(tokenHash, claims);
            logger.debug("Token claims cached for hash: {}", getSafeHashPrefix(tokenHash));
        }
    }

    /**
     * Get cached token verify response if available.
     * Returns null if not in cache, requiring full verification.
     */
    public TokenVerifyResponse getCachedVerifyResponse(String tokenHash) {
        Cache cache = cacheManager.getCache(TOKEN_VERIFY_CACHE_NAME);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(tokenHash);
            if (wrapper != null) {
                logger.debug("Token verify cache hit for hash: {}", getSafeHashPrefix(tokenHash));
                return (TokenVerifyResponse) wrapper.get();
            }
        }
        logger.debug("Token verify cache miss for hash: {}", getSafeHashPrefix(tokenHash));
        return null;
    }

    /**
     * Cache validated token verify response for future requests.
     * This prevents duplicate VERIFY_TOKEN logs when the same token is verified multiple times.
     */
    public void cacheVerifyResponse(String tokenHash, TokenVerifyResponse response) {
        Cache cache = cacheManager.getCache(TOKEN_VERIFY_CACHE_NAME);
        if (cache != null) {
            cache.put(tokenHash, response);
            logger.debug("Token verify response cached for hash: {}", getSafeHashPrefix(tokenHash));
        }
    }

    /**
     * Evict token from cache when it's blacklisted.
     * This ensures blacklisted tokens are immediately invalidated.
     * 
     * Note: This method uses both @CacheEvict annotation (for Spring-managed caches) 
     * and manual eviction (for test scenarios with simple cache managers).
     */
    @Caching(evict = {
        @CacheEvict(value = TOKEN_CACHE_NAME, key = "#tokenHash"),
        @CacheEvict(value = TOKEN_VERIFY_CACHE_NAME, key = "#tokenHash")
    })
    public void evictToken(String tokenHash) {
        // Manual eviction ensures compatibility with non-Spring cache managers (e.g., in tests)
        Cache claimsCache = cacheManager.getCache(TOKEN_CACHE_NAME);
        if (claimsCache != null) {
            claimsCache.evict(tokenHash);
        }
        Cache verifyCache = cacheManager.getCache(TOKEN_VERIFY_CACHE_NAME);
        if (verifyCache != null) {
            verifyCache.evict(tokenHash);
        }
        logger.debug("Token evicted from all caches: {}", getSafeHashPrefix(tokenHash));
    }

    /**
     * Hash token to create a cache key.
     * Delegates to TokenHashUtil for consistent hashing across the application.
     */
    public String hashToken(String token) {
        return TokenHashUtil.hashToken(token);
    }

    /**
     * Get a safe prefix of the hash for logging (first 10 characters).
     * This avoids logging full hashes which could be a security concern.
     */
    private String getSafeHashPrefix(String hash) {
        return hash.substring(0, Math.min(10, hash.length()));
    }
}
