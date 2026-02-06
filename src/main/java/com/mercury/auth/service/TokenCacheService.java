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
     * Improves performance by avoiding repeated JWT parsing and validation.
     * Note: Logs are still recorded for each verification request for audit purposes.
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
     * Evict all cached tokens for a specific user.
     * This should be called when a user's status changes (e.g., disabled).
     * 
     * Note: Since Caffeine doesn't support querying by user, we clear the entire cache.
     * This is a tradeoff between performance and security. The cache will rebuild quickly.
     * 
     * WARNING: In high-traffic systems, frequent user status changes can cause memory spikes.
     * Consider rate-limiting user status updates or implementing user-specific eviction.
     */
    @Caching(evict = {
        @CacheEvict(value = TOKEN_CACHE_NAME, allEntries = true),
        @CacheEvict(value = TOKEN_VERIFY_CACHE_NAME, allEntries = true)
    })
    public void evictAllForUserStatusChange(String tenantId, Long userId) {
        // Manual eviction for non-Spring cache managers
        Cache claimsCache = cacheManager.getCache(TOKEN_CACHE_NAME);
        if (claimsCache != null) {
            claimsCache.clear();
        }
        Cache verifyCache = cacheManager.getCache(TOKEN_VERIFY_CACHE_NAME);
        if (verifyCache != null) {
            verifyCache.clear();
        }
        logger.warn("All token caches cleared due to user status change: tenantId={} userId={} - " +
                   "This may cause temporary performance impact in high-traffic systems", tenantId, userId);
    }

    /**
     * Evict all cached tokens for a specific tenant.
     * This should be called when a tenant's status changes (e.g., disabled).
     * 
     * Note: Since Caffeine doesn't support querying by tenant, we clear the entire cache.
     * This is a tradeoff between performance and security. The cache will rebuild quickly.
     * 
     * WARNING: In high-traffic systems, frequent tenant status changes can cause memory spikes.
     * Consider rate-limiting tenant status updates or implementing tenant-specific eviction.
     */
    @Caching(evict = {
        @CacheEvict(value = TOKEN_CACHE_NAME, allEntries = true),
        @CacheEvict(value = TOKEN_VERIFY_CACHE_NAME, allEntries = true)
    })
    public void evictAllForTenantStatusChange(String tenantId) {
        // Manual eviction for non-Spring cache managers
        Cache claimsCache = cacheManager.getCache(TOKEN_CACHE_NAME);
        if (claimsCache != null) {
            claimsCache.clear();
        }
        Cache verifyCache = cacheManager.getCache(TOKEN_VERIFY_CACHE_NAME);
        if (verifyCache != null) {
            verifyCache.clear();
        }
        logger.warn("All token caches cleared due to tenant status change: tenantId={} - " +
                   "This may cause temporary performance impact in high-traffic systems", tenantId);
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
