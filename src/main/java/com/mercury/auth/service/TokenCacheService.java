package com.mercury.auth.service;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
     */
    @CacheEvict(value = TOKEN_CACHE_NAME, key = "#tokenHash")
    public void evictToken(String tokenHash) {
        logger.debug("Token evicted from cache: {}", tokenHash.substring(0, Math.min(10, tokenHash.length())));
    }

    /**
     * Hash token to create a cache key.
     * Uses SHA-256 for consistent hashing.
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
