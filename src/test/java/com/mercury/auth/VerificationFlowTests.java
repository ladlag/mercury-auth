package com.mercury.auth;

import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.entity.Tenant;
import com.mercury.auth.entity.User;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.service.PasswordAuthService;
import com.mercury.auth.service.EmailAuthService;
import com.mercury.auth.service.AuthLogService;
import com.mercury.auth.service.CaptchaService;
import com.mercury.auth.service.PasswordEncryptionService;
import com.mercury.auth.service.RateLimitService;
import com.mercury.auth.service.TenantService;
import com.mercury.auth.service.TokenService;
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
    private RateLimitService rateLimitService;
    private TokenService tokenService;
    private TenantService tenantService;
    private AuthLogService authLogService;
    private CaptchaService captchaService;
    private PasswordAuthService passwordAuthService;
    private EmailAuthService emailAuthService;

    @BeforeEach
    void setup() {
        userMapper = Mockito.mock(UserMapper.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        jwtService = Mockito.mock(JwtService.class);
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        verificationService = Mockito.mock(VerificationService.class);
        rateLimitService = Mockito.mock(RateLimitService.class);
        tokenService = Mockito.mock(TokenService.class);
        tenantService = Mockito.mock(TenantService.class);
        authLogService = Mockito.mock(AuthLogService.class);
        captchaService = Mockito.mock(CaptchaService.class);
        PasswordEncryptionService passwordEncryptionService = Mockito.mock(PasswordEncryptionService.class);
        
        // Mock password encryption service to return input as-is (no encryption)
        try {
            Mockito.doAnswer(invocation -> invocation.getArgument(1))
                    .when(passwordEncryptionService).processPassword(Mockito.any(), Mockito.any());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        passwordAuthService = new PasswordAuthService(userMapper, passwordEncoder, jwtService, verificationService, rateLimitService, tenantService, authLogService, captchaService, passwordEncryptionService);
        emailAuthService = new EmailAuthService(userMapper, passwordEncoder, jwtService, verificationService, rateLimitService, tenantService, authLogService, captchaService, passwordEncryptionService);
    }

    @Test
    void sendEmailCode_stores_code() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        AuthRequests.SendEmailCode req = new AuthRequests.SendEmailCode();
        req.setTenantId("t1");
        req.setEmail("a@b.com");
        req.setPurpose(AuthRequests.VerificationPurpose.REGISTER);
        Mockito.when(userMapper.selectCount(Mockito.any())).thenReturn(0L);
        Mockito.when(verificationService.generateCode()).thenReturn("123456");
        Mockito.when(verificationService.defaultTtl()).thenReturn(Duration.ofMinutes(10));
        User result = emailAuthService.sendEmailCode(req);
        Mockito.verify(verificationService).storeCode(Mockito.eq("email:t1:a@b.com"), Mockito.eq("123456"), Mockito.any());
        Mockito.verify(verificationService).sendEmailCode("a@b.com", "123456");
        assertThat(result).isNull(); // For REGISTER purpose, no user exists yet
    }

    @Test
    void loginEmail_valid_code_generates_token() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        Mockito.when(verificationService.verifyAndConsume("email:t1:a@b.com", "123456")).thenReturn(true);
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
        AuthResponse resp = emailAuthService.loginEmail(req);
        assertThat(resp.getAccessToken()).isEqualTo("token2");
        assertThat(resp.getExpiresInSeconds()).isEqualTo(20L);
    }

    @Test
    void loginEmail_invalid_code_throws() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        Mockito.when(verificationService.verifyAndConsume("email:t1:a@b.com", "000000")).thenReturn(false);
        AuthRequests.EmailLogin req = new AuthRequests.EmailLogin();
        req.setTenantId("t1");
        req.setEmail("a@b.com");
        req.setCode("000000");
        assertThatThrownBy(() -> emailAuthService.loginEmail(req)).isInstanceOf(RuntimeException.class);
    }
}
