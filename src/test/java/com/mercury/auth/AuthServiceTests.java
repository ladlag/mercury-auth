package com.mercury.auth;

import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.entity.Tenant;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.service.*;
import com.mercury.auth.store.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AuthServiceTests {

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private VerificationService verificationService;
    private RateLimitService rateLimitService;
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
        verificationService = Mockito.mock(VerificationService.class);
        rateLimitService = Mockito.mock(RateLimitService.class);
        tenantService = Mockito.mock(TenantService.class);
        authLogService = Mockito.mock(AuthLogService.class);
        captchaService = Mockito.mock(CaptchaService.class);
        passwordAuthService = new PasswordAuthService(userMapper, passwordEncoder, jwtService, 
            verificationService, rateLimitService, tenantService, authLogService, captchaService);
        emailAuthService = new EmailAuthService(userMapper, passwordEncoder, jwtService, 
            verificationService, rateLimitService, tenantService, authLogService, captchaService);
    }

    @Test
    void registerPassword_rejects_mismatch() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        AuthRequests.PasswordRegister req = new AuthRequests.PasswordRegister();
        req.setTenantId("t1");
        req.setUsername("u1");
        req.setPassword("pass1234");
        req.setConfirmPassword("pass12345");
        assertThatThrownBy(() -> passwordAuthService.registerPassword(req)).isInstanceOf(RuntimeException.class);
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
        AuthResponse resp = passwordAuthService.loginPassword(req);
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
        // New behavior: returns null instead of throwing exception to prevent account enumeration
        User result = emailAuthService.sendEmailCode(req);
        assertThat(result).isNull();
    }

    @Test
    void forgotPassword_sends_code_to_email() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        
        User user = new User();
        user.setId(1L);
        user.setTenantId("t1");
        user.setEmail("user@test.com");
        user.setUsername("testuser");
        
        Mockito.when(userMapper.selectOne(Mockito.any())).thenReturn(user);
        Mockito.when(verificationService.generateCode()).thenReturn("123456");
        Mockito.when(verificationService.defaultTtl()).thenReturn(Duration.ofMinutes(10));
        
        AuthRequests.ForgotPassword req = new AuthRequests.ForgotPassword();
        req.setTenantId("t1");
        req.setEmail("user@test.com");
        
        User result = passwordAuthService.forgotPassword(req);
        
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("user@test.com");
        
        // Verify code was stored and email was sent
        Mockito.verify(verificationService).storeCode(Mockito.anyString(), Mockito.eq("123456"), Mockito.eq(Duration.ofMinutes(10)));
        Mockito.verify(verificationService).sendEmailCode(Mockito.eq("user@test.com"), Mockito.eq("123456"));
    }

    @Test
    void forgotPassword_throws_for_nonexistent_user() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        Mockito.when(userMapper.selectOne(Mockito.any())).thenReturn(null);
        
        AuthRequests.ForgotPassword req = new AuthRequests.ForgotPassword();
        req.setTenantId("t1");
        req.setEmail("nonexistent@test.com");
        
        assertThatThrownBy(() -> passwordAuthService.forgotPassword(req))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void resetPassword_updates_password_with_valid_code() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        
        User user = new User();
        user.setId(1L);
        user.setTenantId("t1");
        user.setEmail("user@test.com");
        user.setUsername("testuser");
        user.setPasswordHash("oldHash");
        
        Mockito.when(userMapper.selectOne(Mockito.any())).thenReturn(user);
        Mockito.when(verificationService.verifyAndConsume(Mockito.anyString(), Mockito.eq("123456"))).thenReturn(true);
        Mockito.when(passwordEncoder.encode("newPassword123")).thenReturn("newHash");
        
        AuthRequests.ResetPassword req = new AuthRequests.ResetPassword();
        req.setTenantId("t1");
        req.setEmail("user@test.com");
        req.setCode("123456");
        req.setNewPassword("newPassword123");
        req.setConfirmPassword("newPassword123");
        
        User result = passwordAuthService.resetPassword(req);
        
        assertThat(result).isNotNull();
        assertThat(result.getPasswordHash()).isEqualTo("newHash");
        
        // Verify password was updated in database
        Mockito.verify(userMapper).updateById(user);
    }

    @Test
    void resetPassword_rejects_password_mismatch() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        
        AuthRequests.ResetPassword req = new AuthRequests.ResetPassword();
        req.setTenantId("t1");
        req.setEmail("user@test.com");
        req.setCode("123456");
        req.setNewPassword("newPassword123");
        req.setConfirmPassword("differentPassword123");
        
        assertThatThrownBy(() -> passwordAuthService.resetPassword(req))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void resetPassword_rejects_invalid_code() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        Mockito.when(verificationService.verifyAndConsume(Mockito.anyString(), Mockito.eq("wrongcode"))).thenReturn(false);
        
        AuthRequests.ResetPassword req = new AuthRequests.ResetPassword();
        req.setTenantId("t1");
        req.setEmail("user@test.com");
        req.setCode("wrongcode");
        req.setNewPassword("newPassword123");
        req.setConfirmPassword("newPassword123");
        
        assertThatThrownBy(() -> passwordAuthService.resetPassword(req))
            .isInstanceOf(RuntimeException.class);
    }
}
