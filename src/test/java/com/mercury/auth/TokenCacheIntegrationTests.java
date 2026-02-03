package com.mercury.auth;

import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.service.*;
import com.mercury.auth.store.TokenBlacklistMapper;
import com.mercury.auth.store.UserMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration tests for token caching with blacklist operations.
 * Verifies that tokens are properly cached and evicted on logout/refresh.
 */
public class TokenCacheIntegrationTests {

    private TokenService tokenService;
    private TokenCacheService tokenCacheService;
    private JwtService jwtService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private UserMapper userMapper;

    @Mock
    private TenantService tenantService;

    @Mock
    private AuthLogService authLogService;

    @Mock
    private TokenBlacklistMapper tokenBlacklistMapper;

    @Mock
    private RateLimitService rateLimitService;

    private org.springframework.cache.concurrent.ConcurrentMapCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Create real JwtService for token generation
        jwtService = new JwtService();
        org.springframework.test.util.ReflectionTestUtils.setField(jwtService, "secret", "test-secret-key-for-integration-tests-min-32-bytes");
        org.springframework.test.util.ReflectionTestUtils.setField(jwtService, "ttlSeconds", 7200L);
        jwtService.init();

        // Create real cache manager with both caches
        cacheManager = new org.springframework.cache.concurrent.ConcurrentMapCacheManager("tokenCache", "tokenVerifyCache");
        
        // Create real TokenCacheService with cache manager
        tokenCacheService = new TokenCacheService(cacheManager);

        // Create TokenService with real and mock dependencies
        tokenService = new TokenService(jwtService, redisTemplate, userMapper, tenantService, 
                                       authLogService, tokenBlacklistMapper, rateLimitService, tokenCacheService);

        // Mock Redis operations
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
    }

    @Test
    public void testTokenCacheEvictionOnLogout() {
        // Setup: Create a test user
        String tenantId = "tenant1";
        Long userId = 123L;
        String username = "testuser";

        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setEmail("test@example.com");
        user.setEnabled(true);
        when(userMapper.selectOne(any())).thenReturn(user);

        // Generate a JWT token
        String token = jwtService.generate(tenantId, userId, username);
        String tokenHash = tokenCacheService.hashToken(token);

        // Parse the token to cache it
        Claims claims = jwtService.parse(token);
        tokenCacheService.cacheClaims(tokenHash, claims);

        // Verify token is cached
        Claims cachedClaims = tokenCacheService.getCachedClaims(tokenHash);
        assertNotNull(cachedClaims, "Token should be cached before logout");

        // Logout (blacklist the token)
        AuthRequests.TokenLogout logoutRequest = new AuthRequests.TokenLogout();
        logoutRequest.setTenantId(tenantId);
        logoutRequest.setToken(token);
        
        User loggedOutUser = tokenService.logout(logoutRequest);

        // Verify user was returned
        assertNotNull(loggedOutUser);
        assertEquals(username, loggedOutUser.getUsername());

        // Verify token was evicted from cache
        Claims evictedClaims = tokenCacheService.getCachedClaims(tokenHash);
        assertNull(evictedClaims, "Token should be evicted from cache after logout");
    }

    @Test
    public void testTokenCacheEvictionOnRefresh() {
        // Setup: Create a test user
        String tenantId = "tenant2";
        Long userId = 456L;
        String username = "refreshuser";

        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setEmail("refresh@example.com");
        user.setEnabled(true);
        when(userMapper.selectOne(any())).thenReturn(user);

        // Generate a JWT token
        String oldToken = jwtService.generate(tenantId, userId, username);
        String oldTokenHash = tokenCacheService.hashToken(oldToken);

        // Parse the token to cache it
        Claims oldClaims = jwtService.parse(oldToken);
        tokenCacheService.cacheClaims(oldTokenHash, oldClaims);

        // Verify old token is cached
        Claims cachedOldClaims = tokenCacheService.getCachedClaims(oldTokenHash);
        assertNotNull(cachedOldClaims, "Old token should be cached before refresh");

        // Refresh the token
        AuthRequests.TokenRefresh refreshRequest = new AuthRequests.TokenRefresh();
        refreshRequest.setTenantId(tenantId);
        refreshRequest.setToken(oldToken);
        
        AuthResponse refreshResponse = tokenService.refreshToken(refreshRequest);

        // Verify new token was generated
        assertNotNull(refreshResponse);
        assertNotNull(refreshResponse.getAccessToken());
        assertNotEquals(oldToken, refreshResponse.getAccessToken(), "New token should be different");

        // Verify old token was evicted from cache
        Claims evictedClaims = tokenCacheService.getCachedClaims(oldTokenHash);
        assertNull(evictedClaims, "Old token should be evicted from cache after refresh");

        // Verify new token can be cached
        String newToken = refreshResponse.getAccessToken();
        String newTokenHash = tokenCacheService.hashToken(newToken);
        Claims newClaims = jwtService.parse(newToken);
        tokenCacheService.cacheClaims(newTokenHash, newClaims);

        Claims cachedNewClaims = tokenCacheService.getCachedClaims(newTokenHash);
        assertNotNull(cachedNewClaims, "New token should be cacheable");
    }

    @Test
    public void testTokenVerifyResponseCaching() {
        // Setup: Create a test user
        String tenantId = "tenant3";
        Long userId = 789L;
        String username = "verifycacheuser";

        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setEmail("verifycache@example.com");
        user.setPhone("1234567890");
        user.setEnabled(true);
        when(userMapper.selectOne(any())).thenReturn(user);

        // Generate a JWT token
        String token = jwtService.generate(tenantId, userId, username);
        String tokenHash = tokenCacheService.hashToken(token);

        // Verify the verify response is not cached initially
        com.mercury.auth.dto.TokenVerifyResponse cachedResponse = tokenCacheService.getCachedVerifyResponse(tokenHash);
        assertNull(cachedResponse, "Verify response should not be cached initially");

        // Call verifyToken - this should cache the response
        com.mercury.auth.dto.AuthRequests.TokenVerify verifyRequest = new com.mercury.auth.dto.AuthRequests.TokenVerify();
        verifyRequest.setTenantId(tenantId);
        verifyRequest.setToken(token);
        
        com.mercury.auth.dto.TokenVerifyResponse firstResponse = tokenService.verifyToken(verifyRequest);

        // Verify the response is correct
        assertNotNull(firstResponse);
        assertEquals(tenantId, firstResponse.getTenantId());
        assertEquals(userId, firstResponse.getUserId());
        assertEquals(username, firstResponse.getUserName());

        // Verify the response is now cached
        com.mercury.auth.dto.TokenVerifyResponse cachedAfterFirst = tokenCacheService.getCachedVerifyResponse(tokenHash);
        assertNotNull(cachedAfterFirst, "Verify response should be cached after first call");
        assertEquals(tenantId, cachedAfterFirst.getTenantId());
        assertEquals(userId, cachedAfterFirst.getUserId());

        // Call verifyToken again - this should use the cached response
        // We can verify this by checking that the userMapper is not called again
        reset(userMapper); // Reset mock to clear previous interactions
        
        com.mercury.auth.dto.TokenVerifyResponse secondResponse = tokenService.verifyToken(verifyRequest);
        
        // Verify the response is the same
        assertNotNull(secondResponse);
        assertEquals(firstResponse.getTenantId(), secondResponse.getTenantId());
        assertEquals(firstResponse.getUserId(), secondResponse.getUserId());
        assertEquals(firstResponse.getUserName(), secondResponse.getUserName());
        
        // Verify that userMapper was NOT called (because response was cached)
        verify(userMapper, never()).selectOne(any());
    }

    @Test
    public void testTokenVerifyResponseEvictionOnLogout() {
        // Setup: Create a test user
        String tenantId = "tenant4";
        Long userId = 999L;
        String username = "verifyevictuser";

        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setEmail("verifyevict@example.com");
        user.setPhone("9876543210");
        user.setEnabled(true);
        when(userMapper.selectOne(any())).thenReturn(user);

        // Generate a JWT token
        String token = jwtService.generate(tenantId, userId, username);
        String tokenHash = tokenCacheService.hashToken(token);

        // Call verifyToken to cache the response
        com.mercury.auth.dto.AuthRequests.TokenVerify verifyRequest = new com.mercury.auth.dto.AuthRequests.TokenVerify();
        verifyRequest.setTenantId(tenantId);
        verifyRequest.setToken(token);
        
        com.mercury.auth.dto.TokenVerifyResponse response = tokenService.verifyToken(verifyRequest);
        assertNotNull(response);

        // Verify the response is cached
        com.mercury.auth.dto.TokenVerifyResponse cached = tokenCacheService.getCachedVerifyResponse(tokenHash);
        assertNotNull(cached, "Verify response should be cached");

        // Logout (blacklist the token)
        com.mercury.auth.dto.AuthRequests.TokenLogout logoutRequest = new com.mercury.auth.dto.AuthRequests.TokenLogout();
        logoutRequest.setTenantId(tenantId);
        logoutRequest.setToken(token);
        
        User loggedOutUser = tokenService.logout(logoutRequest);
        assertNotNull(loggedOutUser);

        // Verify the verify response was evicted
        com.mercury.auth.dto.TokenVerifyResponse evicted = tokenCacheService.getCachedVerifyResponse(tokenHash);
        assertNull(evicted, "Verify response should be evicted after logout");
    }
}
