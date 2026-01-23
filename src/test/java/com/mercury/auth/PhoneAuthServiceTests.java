package com.mercury.auth;

import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.service.AuthLogService;
import com.mercury.auth.service.CaptchaService;
import com.mercury.auth.service.PhoneAuthService;
import com.mercury.auth.service.RateLimitService;
import com.mercury.auth.service.SmsService;
import com.mercury.auth.service.TenantService;
import com.mercury.auth.service.VerificationService;
import com.mercury.auth.store.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PhoneAuthServiceTests {

    private VerificationService verificationService;
    private SmsService smsService;
    private UserMapper userMapper;
    private JwtService jwtService;
    private RateLimitService rateLimitService;
    private TenantService tenantService;
    private AuthLogService authLogService;
    private CaptchaService captchaService;
    private PhoneAuthService phoneAuthService;

    @BeforeEach
    void setup() {
        verificationService = Mockito.mock(VerificationService.class);
        smsService = Mockito.mock(SmsService.class);
        userMapper = Mockito.mock(UserMapper.class);
        jwtService = Mockito.mock(JwtService.class);
        rateLimitService = Mockito.mock(RateLimitService.class);
        tenantService = Mockito.mock(TenantService.class);
        authLogService = Mockito.mock(AuthLogService.class);
        captchaService = Mockito.mock(CaptchaService.class);
        phoneAuthService = new PhoneAuthService(verificationService, smsService, userMapper, jwtService, rateLimitService, tenantService, authLogService, captchaService);
    }

    @Test
    void loginPhone_valid() {
        Mockito.when(captchaService.isRequired(Mockito.any())).thenReturn(false);
        Mockito.when(verificationService.verifyAndConsume("phone:t1:138", "111111")).thenReturn(true);
        User u = new User();
        u.setId(3L);
        u.setTenantId("t1");
        u.setUsername("u3");
        u.setEnabled(true);
        Mockito.when(userMapper.selectOne(Mockito.any())).thenReturn(u);
        Mockito.when(jwtService.generate("t1", 3L, "u3")).thenReturn("tkn3");
        Mockito.when(jwtService.getTtlSeconds()).thenReturn(30L);
        AuthResponse resp = phoneAuthService.loginPhone("t1", "138", "111111", null, null);
        assertThat(resp.getAccessToken()).isEqualTo("tkn3");
    }

    @Test
    void loginPhone_invalid_code() {
        Mockito.when(captchaService.isRequired(Mockito.any())).thenReturn(false);
        Mockito.when(verificationService.verifyAndConsume("phone:t1:138", "000000")).thenReturn(false);
        assertThatThrownBy(() -> phoneAuthService.loginPhone("t1", "138", "000000", null, null)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void sendPhoneCode_returns_ok() {
        Mockito.when(verificationService.generateCode()).thenReturn("123456");
        Mockito.when(userMapper.selectCount(Mockito.any())).thenReturn(0L);
        User result = phoneAuthService.sendPhoneCode("t1", "138", AuthRequests.VerificationPurpose.REGISTER);
        assertThat(result).isNull(); // For REGISTER purpose, no user exists yet
        Mockito.verify(verificationService).storeCode(Mockito.eq("phone:t1:138"), Mockito.eq("123456"), Mockito.any());
        // Verify SMS is sent
        Mockito.verify(smsService).sendVerificationCode(Mockito.eq("138"), Mockito.eq("123456"));
    }

    @Test
    void sendPhoneCode_register_duplicate_phone() {
        Mockito.when(userMapper.selectCount(Mockito.any())).thenReturn(1L);
        // New behavior: returns null instead of throwing exception to prevent account enumeration
        User result = phoneAuthService.sendPhoneCode("t1", "138", AuthRequests.VerificationPurpose.REGISTER);
        assertThat(result).isNull();
        // Verify that code is NOT stored when phone already exists
        Mockito.verify(verificationService, Mockito.never()).storeCode(Mockito.any(), Mockito.any(), Mockito.any());
        // Verify that SMS is NOT sent when phone already exists
        Mockito.verify(smsService, Mockito.never()).sendVerificationCode(Mockito.any(), Mockito.any());
    }
}
