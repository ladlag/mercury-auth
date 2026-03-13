package com.mercury.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.config.BlacklistConfig;
import com.mercury.auth.dto.TokenVerifyResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.service.*;
import com.mercury.auth.store.TokenBlacklistMapper;
import com.mercury.auth.store.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests to verify that TokenVerifyResponse includes userType and nickname fields
 */
public class TokenVerifyResponseUserTypeNicknameTest {

    private static final long TWO_HOURS_IN_MILLIS = 7200000L;

    @Mock
    private JwtService jwtService;
    @Mock
    private StringRedisTemplate redisTemplate;
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
    @Mock
    private TokenCacheService tokenCacheService;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private BlacklistConfig blacklistConfig;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        blacklistConfig = new BlacklistConfig();
        blacklistConfig.setIpEnabled(true);
        blacklistConfig.setTokenEnabled(true);
        blacklistConfig.setPermanentBlacklistCacheDays(365);
        tokenService = new TokenService(jwtService, redisTemplate, userMapper, tenantService, 
            authLogService, tokenBlacklistMapper, rateLimitService, tokenCacheService, blacklistConfig);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private java.util.Date createExpirationDate() {
        long currentTimeSeconds = System.currentTimeMillis() / 1000;
        long truncatedTimeMillis = currentTimeSeconds * 1000;
        return new java.util.Date(truncatedTimeMillis + TWO_HOURS_IN_MILLIS);
    }

    @Test
    void verifyToken_returns_userType_and_nickname() {
        String tenantId = "tenant1";
        String token = "valid.jwt.token";
        Long userId = 123L;

        java.util.Date expirationDate = createExpirationDate();

        Claims claims = new DefaultClaims();
        claims.put("tenantId", tenantId);
        claims.put("userId", userId);
        claims.setExpiration(expirationDate);
        when(jwtService.parse(token)).thenReturn(claims);

        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(tokenBlacklistMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setUsername("testuser");
        user.setNickname("Test User Nickname");
        user.setUserType("TENANT_ADMIN");
        user.setEmail("test@example.com");
        user.setPhone("13800138000");
        user.setEnabled(true);
        when(userMapper.selectOne(any())).thenReturn(user);

        TokenVerifyResponse response = tokenService.verifyToken(tenantId, token);

        assertThat(response).isNotNull();
        assertThat(response.getUserName()).isEqualTo("testuser");
        assertThat(response.getNickname()).isEqualTo("Test User Nickname");
        assertThat(response.getUserType()).isEqualTo("TENANT_ADMIN");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getPhone()).isEqualTo("13800138000");
    }

    @Test
    void verifyToken_handles_null_nickname_and_default_userType() {
        String tenantId = "tenant1";
        String token = "valid.jwt.token";
        Long userId = 456L;

        java.util.Date expirationDate = createExpirationDate();

        Claims claims = new DefaultClaims();
        claims.put("tenantId", tenantId);
        claims.put("userId", userId);
        claims.setExpiration(expirationDate);
        when(jwtService.parse(token)).thenReturn(claims);

        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(tokenBlacklistMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setUsername("regularuser");
        user.setNickname(null);
        user.setUserType("USER");
        user.setEnabled(true);
        when(userMapper.selectOne(any())).thenReturn(user);

        TokenVerifyResponse response = tokenService.verifyToken(tenantId, token);

        assertThat(response).isNotNull();
        assertThat(response.getNickname()).isNull();
        assertThat(response.getUserType()).isEqualTo("USER");
    }
}
