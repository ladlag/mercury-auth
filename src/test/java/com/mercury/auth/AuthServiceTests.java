package com.mercury.auth;

import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.entity.Tenant;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.service.AuthService;
import com.mercury.auth.service.AuthLogService;
import com.mercury.auth.service.CaptchaService;
import com.mercury.auth.service.RateLimitService;
import com.mercury.auth.service.TenantService;
import com.mercury.auth.service.TokenService;
import com.mercury.auth.service.VerificationService;
import com.mercury.auth.store.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AuthServiceTests {

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private VerificationService verificationService;
    private RateLimitService rateLimitService;
    private TokenService tokenService;
    private TenantService tenantService;
    private AuthLogService authLogService;
    private CaptchaService captchaService;
    private AuthService authService;

    @BeforeEach
    void setup() {
        userMapper = Mockito.mock(UserMapper.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        jwtService = Mockito.mock(JwtService.class);
        verificationService = Mockito.mock(VerificationService.class);
        rateLimitService = Mockito.mock(RateLimitService.class);
        tokenService = Mockito.mock(TokenService.class);
        tenantService = Mockito.mock(TenantService.class);
        authLogService = Mockito.mock(AuthLogService.class);
        captchaService = Mockito.mock(CaptchaService.class);
        authService = new AuthService(userMapper, passwordEncoder, jwtService, verificationService, rateLimitService, tokenService, tenantService, authLogService, captchaService);
    }

    @Test
    void registerPassword_rejects_mismatch() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        AuthRequests.PasswordRegister req = new AuthRequests.PasswordRegister();
        req.setTenantId("t1");
        req.setUsername("u1");
        req.setPassword("pass1234");
        req.setConfirmPassword("pass12345");
        assertThatThrownBy(() -> authService.registerPassword(req)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void loginPassword_success() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        Mockito.when(captchaService.isRequired(Mockito.any())).thenReturn(false);
        AuthRequests.PasswordLogin req = new AuthRequests.PasswordLogin();
        req.setTenantId("t1");
        req.setUsername("u1");
        req.setPassword("p");
        User u = new User();
        u.setId(1L);
        u.setTenantId("t1");
        u.setUsername("u1");
        u.setPasswordHash("hash");
        u.setEnabled(true);
        Mockito.when(userMapper.selectOne(Mockito.any())).thenReturn(u);
        Mockito.when(passwordEncoder.matches("p", "hash")).thenReturn(true);
        Mockito.when(jwtService.generate("t1", 1L, "u1")).thenReturn("token");
        Mockito.when(jwtService.getTtlSeconds()).thenReturn(10L);
        AuthResponse resp = authService.loginPassword(req);
        assertThat(resp.getAccessToken()).isEqualTo("token");
        assertThat(resp.getExpiresInSeconds()).isEqualTo(10L);
    }

    @Test
    void sendEmailCode_register_checks_duplicate() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        AuthRequests.SendEmailCode req = new AuthRequests.SendEmailCode();
        req.setTenantId("t1");
        req.setEmail("a@b.com");
        req.setPurpose(AuthRequests.VerificationPurpose.REGISTER);
        Mockito.when(userMapper.selectCount(Mockito.any())).thenReturn(1L);
        assertThatThrownBy(() -> authService.sendEmailCode(req)).isInstanceOf(RuntimeException.class);
    }
}
