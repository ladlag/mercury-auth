package com.mercury.auth;

import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.dto.TokenVerifyResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.service.AuthLogService;
import com.mercury.auth.service.TenantService;
import com.mercury.auth.service.TokenService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Test to verify that TokenVerifyResponse returns all user information
 * including email and phone.
 */
public class TokenVerifyResponseTest {

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
    private ValueOperations<String, String> valueOperations;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tokenService = new TokenService(jwtService, redisTemplate, userMapper, tenantService, authLogService, tokenBlacklistMapper);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void verifyToken_returns_complete_user_information() {
        // Setup
        String tenantId = "tenant1";
        String token = "valid.jwt.token";
        Long userId = 123L;
        String username = "testuser";
        String email = "test@example.com";
        String phone = "13800138000";

        // Mock JWT parsing
        Claims claims = new DefaultClaims();
        claims.put("tenantId", tenantId);
        claims.put("userId", userId);
        when(jwtService.parse(token)).thenReturn(claims);

        // Mock token not blacklisted
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // Mock tenant enabled
        // tenantService.requireEnabled() will be called but doesn't throw

        // Mock user lookup
        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(phone);
        user.setEnabled(true);
        when(userMapper.selectOne(any())).thenReturn(user);

        // Execute
        TokenVerifyResponse response = tokenService.verifyToken(tenantId, token);

        // Verify - all user information should be returned
        assertThat(response).isNotNull();
        assertThat(response.getTenantId()).isEqualTo(tenantId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getUsername()).isEqualTo(username);
        assertThat(response.getEmail()).isEqualTo(email);
        assertThat(response.getPhone()).isEqualTo(phone);
    }

    @Test
    void verifyToken_handles_null_email_and_phone() {
        // Setup - user without email and phone
        String tenantId = "tenant1";
        String token = "valid.jwt.token";
        Long userId = 456L;
        String username = "userNoContact";

        // Mock JWT parsing
        Claims claims = new DefaultClaims();
        claims.put("tenantId", tenantId);
        claims.put("userId", userId);
        when(jwtService.parse(token)).thenReturn(claims);

        // Mock token not blacklisted
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // Mock user lookup - user with no email/phone
        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setEmail(null);
        user.setPhone(null);
        user.setEnabled(true);
        when(userMapper.selectOne(any())).thenReturn(user);

        // Execute
        TokenVerifyResponse response = tokenService.verifyToken(tenantId, token);

        // Verify - null values should be handled properly
        assertThat(response).isNotNull();
        assertThat(response.getTenantId()).isEqualTo(tenantId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getUsername()).isEqualTo(username);
        assertThat(response.getEmail()).isNull();
        assertThat(response.getPhone()).isNull();
    }
}
