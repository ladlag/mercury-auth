package com.mercury.auth;

import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.service.AuthService;
import com.mercury.auth.service.VerificationService;
import com.mercury.auth.store.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class VerificationFlowTests {

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private StringRedisTemplate redisTemplate;
    private VerificationService verificationService;
    private AuthService authService;

    @BeforeEach
    void setup() {
        userMapper = Mockito.mock(UserMapper.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        jwtService = Mockito.mock(JwtService.class);
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        verificationService = new VerificationService(redisTemplate);
        authService = new AuthService(userMapper, passwordEncoder, jwtService, verificationService);
    }

    @Test
    void sendEmailCode_stores_code() {
        ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        AuthRequests.SendEmailCode req = new AuthRequests.SendEmailCode();
        req.setTenantId("t1");
        req.setEmail("a@b.com");
        String code = authService.sendEmailCode(req);
        Mockito.verify(valueOps).set(Mockito.eq("email:t1:a@b.com"), Mockito.eq(code), Mockito.any(Duration.class));
    }

    @Test
    void loginEmail_valid_code_generates_token() {
        ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        Mockito.when(valueOps.get("email:t1:a@b.com")).thenReturn("123456");
        AuthRequests.EmailLogin req = new AuthRequests.EmailLogin();
        req.setTenantId("t1");
        req.setEmail("a@b.com");
        req.setCode("123456");
        User u = new User();
        u.setId(2L);
        u.setTenantId("t1");
        u.setUsername("u1");
        u.setEnabled(true);
        Mockito.when(userMapper.selectOne(Mockito.any())).thenReturn(u);
        Mockito.when(jwtService.generate("t1", 2L, "u1")).thenReturn("token2");
        Mockito.when(jwtService.getTtlSeconds()).thenReturn(20L);
        AuthResponse resp = authService.loginEmail(req);
        assertThat(resp.getAccessToken()).isEqualTo("token2");
        assertThat(resp.getExpiresInSeconds()).isEqualTo(20L);
    }

    @Test
    void loginEmail_invalid_code_throws() {
        ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        Mockito.when(valueOps.get("email:t1:a@b.com")).thenReturn("123456");
        AuthRequests.EmailLogin req = new AuthRequests.EmailLogin();
        req.setTenantId("t1");
        req.setEmail("a@b.com");
        req.setCode("000000");
        assertThatThrownBy(() -> authService.loginEmail(req)).isInstanceOf(IllegalArgumentException.class);
    }
}
