package com.mercury.auth;

import com.mercury.auth.service.TokenCacheService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TokenCacheService to verify token caching functionality.
 */
@SpringBootTest
public class TokenCacheServiceTests {

    @Autowired
    private TokenCacheService tokenCacheService;

    @Autowired
    private CacheManager cacheManager;

    @Test
    public void testTokenHashing() {
        // Test that the same token produces the same hash
        String token = "test-token-123";
        String hash1 = tokenCacheService.hashToken(token);
        String hash2 = tokenCacheService.hashToken(token);
        
        assertEquals(hash1, hash2, "Same token should produce same hash");
        
        // Test that different tokens produce different hashes
        String differentToken = "test-token-456";
        String hash3 = tokenCacheService.hashToken(differentToken);
        
        assertNotEquals(hash1, hash3, "Different tokens should produce different hashes");
    }

    @Test
    public void testCacheClaims() {
        // Create test claims
        Map<String, Object> claimsMap = new HashMap<>();
        claimsMap.put("tenantId", "tenant1");
        claimsMap.put("userId", 123L);
        claimsMap.put("username", "testuser");
        Claims claims = new DefaultClaims(claimsMap);
        
        String token = "test-token-for-caching";
        String tokenHash = tokenCacheService.hashToken(token);
        
        // Initially, cache should be empty
        Claims cachedClaims = tokenCacheService.getCachedClaims(tokenHash);
        assertNull(cachedClaims, "Cache should be empty initially");
        
        // Cache the claims
        tokenCacheService.cacheClaims(tokenHash, claims);
        
        // Now the claims should be cached
        Claims retrievedClaims = tokenCacheService.getCachedClaims(tokenHash);
        assertNotNull(retrievedClaims, "Claims should be cached");
        assertEquals("tenant1", retrievedClaims.get("tenantId"));
        assertEquals(123L, ((Number) retrievedClaims.get("userId")).longValue());
        assertEquals("testuser", retrievedClaims.get("username"));
    }

    @Test
    public void testEvictToken() {
        // Create and cache test claims
        Map<String, Object> claimsMap = new HashMap<>();
        claimsMap.put("tenantId", "tenant2");
        claimsMap.put("userId", 456L);
        claimsMap.put("username", "evicttest");
        Claims claims = new DefaultClaims(claimsMap);
        
        String token = "test-token-for-eviction";
        String tokenHash = tokenCacheService.hashToken(token);
        
        // Cache the claims
        tokenCacheService.cacheClaims(tokenHash, claims);
        
        // Verify it's cached
        Claims cachedClaims = tokenCacheService.getCachedClaims(tokenHash);
        assertNotNull(cachedClaims, "Claims should be cached");
        
        // Evict the token
        tokenCacheService.evictToken(tokenHash);
        
        // Verify it's been evicted
        Claims evictedClaims = tokenCacheService.getCachedClaims(tokenHash);
        assertNull(evictedClaims, "Claims should be evicted from cache");
    }

    @Test
    public void testCacheManager() {
        // Verify that cache manager is configured correctly
        assertNotNull(cacheManager, "Cache manager should be configured");
        assertNotNull(cacheManager.getCache("tokenCache"), "tokenCache should exist");
    }
}
