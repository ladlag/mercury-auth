package com.mercury.auth;

import com.mercury.auth.dto.TokenVerifyResponse;
import com.mercury.auth.entity.Tenant;
import com.mercury.auth.entity.User;
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
    public void testCacheVerifyResponse() {
        // Create test verify response
        TokenVerifyResponse response = TokenVerifyResponse.builder()
                .tenantId("tenant1")
                .userId(123L)
                .userName("testuser")
                .email("test@example.com")
                .phone("1234567890")
                .expiresAt(System.currentTimeMillis() + 3600000)
                .build();
        
        String token = "test-token-for-verify-caching";
        String tokenHash = tokenCacheService.hashToken(token);
        
        // Initially, cache should be empty
        TokenVerifyResponse cachedResponse = tokenCacheService.getCachedVerifyResponse(tokenHash);
        assertNull(cachedResponse, "Verify cache should be empty initially");
        
        // Cache the verify response
        tokenCacheService.cacheVerifyResponse(tokenHash, response);
        
        // Now the response should be cached
        TokenVerifyResponse retrievedResponse = tokenCacheService.getCachedVerifyResponse(tokenHash);
        assertNotNull(retrievedResponse, "Verify response should be cached");
        assertEquals("tenant1", retrievedResponse.getTenantId());
        assertEquals(123L, retrievedResponse.getUserId());
        assertEquals("testuser", retrievedResponse.getUserName());
        assertEquals("test@example.com", retrievedResponse.getEmail());
        assertEquals("1234567890", retrievedResponse.getPhone());
    }

    @Test
    public void testCacheTenantStatus() {
        Tenant tenant = new Tenant();
        tenant.setTenantId("tenant-cache");
        tenant.setName("Cached Tenant");
        tenant.setEnabled(true);

        Tenant cached = tokenCacheService.getCachedTenant(tenant.getTenantId());
        assertNull(cached, "Tenant cache should be empty initially");

        tokenCacheService.cacheTenant(tenant.getTenantId(), tenant);

        Tenant retrieved = tokenCacheService.getCachedTenant(tenant.getTenantId());
        assertNotNull(retrieved, "Tenant should be cached");
        assertEquals(tenant.getTenantId(), retrieved.getTenantId());
        assertTrue(retrieved.getEnabled());
    }

    @Test
    public void testCacheUserStatus() {
        User user = new User();
        user.setTenantId("tenant-cache");
        user.setId(321L);
        user.setUsername("cache-user");
        user.setEnabled(true);

        User cached = tokenCacheService.getCachedUser(user.getTenantId(), user.getId());
        assertNull(cached, "User cache should be empty initially");

        tokenCacheService.cacheUser(user.getTenantId(), user.getId(), user);

        User retrieved = tokenCacheService.getCachedUser(user.getTenantId(), user.getId());
        assertNotNull(retrieved, "User should be cached");
        assertEquals(user.getUsername(), retrieved.getUsername());
        assertTrue(retrieved.getEnabled());
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
    public void testEvictTokenWithVerifyResponse() {
        // Create and cache both claims and verify response
        Map<String, Object> claimsMap = new HashMap<>();
        claimsMap.put("tenantId", "tenant3");
        claimsMap.put("userId", 789L);
        claimsMap.put("username", "evictboth");
        Claims claims = new DefaultClaims(claimsMap);
        
        TokenVerifyResponse response = TokenVerifyResponse.builder()
                .tenantId("tenant3")
                .userId(789L)
                .userName("evictboth")
                .email("evict@example.com")
                .phone("9876543210")
                .expiresAt(System.currentTimeMillis() + 3600000)
                .build();
        
        String token = "test-token-for-both-eviction";
        String tokenHash = tokenCacheService.hashToken(token);
        
        // Cache both
        tokenCacheService.cacheClaims(tokenHash, claims);
        tokenCacheService.cacheVerifyResponse(tokenHash, response);
        
        // Verify both are cached
        Claims cachedClaims = tokenCacheService.getCachedClaims(tokenHash);
        assertNotNull(cachedClaims, "Claims should be cached");
        TokenVerifyResponse cachedResponse = tokenCacheService.getCachedVerifyResponse(tokenHash);
        assertNotNull(cachedResponse, "Verify response should be cached");
        
        // Evict the token
        tokenCacheService.evictToken(tokenHash);
        
        // Verify both are evicted
        Claims evictedClaims = tokenCacheService.getCachedClaims(tokenHash);
        assertNull(evictedClaims, "Claims should be evicted from cache");
        TokenVerifyResponse evictedResponse = tokenCacheService.getCachedVerifyResponse(tokenHash);
        assertNull(evictedResponse, "Verify response should be evicted from cache");
    }

    @Test
    public void testCacheManager() {
        // Verify that cache manager is configured correctly
        assertNotNull(cacheManager, "Cache manager should be configured");
        assertNotNull(cacheManager.getCache("tokenCache"), "tokenCache should exist");
        assertNotNull(cacheManager.getCache("tokenVerifyCache"), "tokenVerifyCache should exist");
        assertNotNull(cacheManager.getCache("tenantStatusCache"), "tenantStatusCache should exist");
        assertNotNull(cacheManager.getCache("userStatusCache"), "userStatusCache should exist");
    }
}
