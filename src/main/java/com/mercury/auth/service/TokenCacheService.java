package com.mercury.auth.service;

import com.mercury.auth.dto.TokenVerifyResponse;
import com.mercury.auth.entity.Tenant;
import com.mercury.auth.entity.User;
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
    private static final String TENANT_STATUS_CACHE_NAME = "tenantStatusCache";
    private static final String USER_STATUS_CACHE_NAME = "userStatusCache";
    
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
     * Get cached tenant status if available.
     */
    public Tenant getCachedTenant(String tenantId) {
        Cache cache = cacheManager.getCache(TENANT_STATUS_CACHE_NAME);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(buildTenantStatusKey(tenantId));
            if (wrapper != null) {
                logger.debug("Tenant status cache hit for tenantId={}", tenantId);
                return (Tenant) wrapper.get();
            }
        }
        return null;
    }

    /**
     * Cache enabled tenant status for future requests.
     */
    public void cacheTenant(String tenantId, Tenant tenant) {
        Cache cache = cacheManager.getCache(TENANT_STATUS_CACHE_NAME);
        if (cache != null) {
            cache.put(buildTenantStatusKey(tenantId), tenant);
            logger.debug("Tenant status cached for tenantId={}", tenantId);
        }
    }

    /**
     * Get cached user status if available.
     */
    public User getCachedUser(String tenantId, Long userId) {
        Cache cache = cacheManager.getCache(USER_STATUS_CACHE_NAME);
        if (cache != null) {
            String key = buildUserStatusKey(tenantId, userId);
            Cache.ValueWrapper wrapper = cache.get(key);
            if (wrapper != null) {
                logger.debug("User status cache hit for tenantId={} userId={}", tenantId, userId);
                return (User) wrapper.get();
            }
        }
        return null;
    }

    /**
     * Cache enabled user status for future requests.
     */
    public void cacheUser(String tenantId, Long userId, User user) {
        Cache cache = cacheManager.getCache(USER_STATUS_CACHE_NAME);
        if (cache != null) {
            cache.put(buildUserStatusKey(tenantId, userId), user);
            logger.debug("User status cached for tenantId={} userId={}", tenantId, userId);
        }
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
        Cache userStatusCache = cacheManager.getCache(USER_STATUS_CACHE_NAME);
        if (userStatusCache != null) {
            userStatusCache.evict(buildUserStatusKey(tenantId, userId));
        }
        logger.warn("All token caches cleared due to user status change: tenantId={} userId={}", tenantId, userId);
    }

    /**
     * Evict all cached tokens for a specific tenant.
     * This should be called when a tenant's status changes (e.g., disabled).
     * 
     * Note: Since Caffeine doesn't support querying by tenant, we clear the entire cache.
     * This is a tradeoff between performance and security. The cache will rebuild quickly.
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
        Cache tenantStatusCache = cacheManager.getCache(TENANT_STATUS_CACHE_NAME);
        if (tenantStatusCache != null) {
            tenantStatusCache.evict(buildTenantStatusKey(tenantId));
        }
        logger.warn("All token caches cleared due to tenant status change: tenantId={}", tenantId);
    }

    /**
     * Evict cached tenant status entry.
     */
    public void evictTenantStatus(String tenantId) {
        Cache cache = cacheManager.getCache(TENANT_STATUS_CACHE_NAME);
        if (cache != null) {
            cache.evict(buildTenantStatusKey(tenantId));
        }
    }

    /**
     * Evict cached user status entry.
     */
    public void evictUserStatus(String tenantId, Long userId) {
        Cache cache = cacheManager.getCache(USER_STATUS_CACHE_NAME);
        if (cache != null) {
            cache.evict(buildUserStatusKey(tenantId, userId));
        }
    }

    /**
     * Evict user from cache (alias for evictUserStatus for clarity in token revocation context).
     */
    public void evictUser(String tenantId, Long userId) {
        evictUserStatus(tenantId, userId);
        logger.debug("User evicted from cache: tenantId={} userId={}", tenantId, userId);
    }

    /**
     * Evict tenant from cache (alias for evictTenantStatus for clarity in token revocation context).
     */
    public void evictTenant(String tenantId) {
        evictTenantStatus(tenantId);
        logger.debug("Tenant evicted from cache: tenantId={}", tenantId);
    }

    /**
     * Hash token to create a cache key.
     * Delegates to TokenHashUtil for consistent hashing across the application.
     */
    public String hashToken(String token) {
        return TokenHashUtil.hashToken(token);
    }

    /**
     * Returns a safe hash prefix for logging (up to the first 10 characters).
     * Intended for components (e.g., JwtAuthenticationFilter) that need a short identifier without logging full hashes.
     */
    public String getSafeHashPrefix(String hash) {
        if (hash == null || hash.isEmpty()) {
            return "(empty)";
        }
        return hash.substring(0, Math.min(10, hash.length()));
    }

    private String buildUserStatusKey(String tenantId, Long userId) {
        return encodeTenantId(tenantId) + ":" + userId;
    }

    private String buildTenantStatusKey(String tenantId) {
        return encodeTenantId(tenantId);
    }

    private String encodeTenantId(String tenantId) {
        // Base64 encoding avoids delimiter collisions in cache keys if tenant IDs contain separator characters.
        return tenantId == null ? "(unknown)" :
            Base64.getUrlEncoder().withoutPadding().encodeToString(tenantId.getBytes(StandardCharsets.UTF_8));
    }
}
