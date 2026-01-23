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

    @Test
    void quickLoginPhone_new_user_registers_and_logs_in() {
        Mockito.when(captchaService.isRequired(Mockito.any())).thenReturn(false);
        Mockito.when(verificationService.verifyAndConsume("phone:t1:13800138000", "123456")).thenReturn(true);
        // First call returns null (user doesn't exist), subsequent calls for username check return 0
        Mockito.when(userMapper.selectOne(Mockito.any())).thenReturn(null);
        Mockito.when(userMapper.selectCount(Mockito.any())).thenReturn(0L);
        Mockito.when(jwtService.generate(Mockito.eq("t1"), Mockito.any(), Mockito.any())).thenReturn("new_token");
        Mockito.when(jwtService.getTtlSeconds()).thenReturn(7200L);
        
        AuthResponse resp = phoneAuthService.quickLoginPhone("t1", "13800138000", "123456", null, null);
        
        assertThat(resp.getAccessToken()).isEqualTo("new_token");
        assertThat(resp.getExpiresInSeconds()).isEqualTo(7200L);
        // Verify user was inserted with phone number as username
        Mockito.verify(userMapper).insert(Mockito.argThat(user -> 
            user.getTenantId().equals("t1") && 
            user.getPhone().equals("13800138000") &&
            user.getUsername().equals("13800138000")
        ));
    }

    @Test
    void quickLoginPhone_existing_user_logs_in() {
        Mockito.when(captchaService.isRequired(Mockito.any())).thenReturn(false);
        Mockito.when(verificationService.verifyAndConsume("phone:t1:13800138000", "123456")).thenReturn(true);
        User existingUser = new User();
        existingUser.setId(5L);
        existingUser.setTenantId("t1");
        existingUser.setUsername("existing_user");
        existingUser.setPhone("13800138000");
        existingUser.setEnabled(true);
        Mockito.when(userMapper.selectOne(Mockito.any())).thenReturn(existingUser);
        Mockito.when(jwtService.generate("t1", 5L, "existing_user")).thenReturn("existing_token");
        Mockito.when(jwtService.getTtlSeconds()).thenReturn(7200L);
        
        AuthResponse resp = phoneAuthService.quickLoginPhone("t1", "13800138000", "123456", null, null);
        
        assertThat(resp.getAccessToken()).isEqualTo("existing_token");
        assertThat(resp.getExpiresInSeconds()).isEqualTo(7200L);
        // Verify user was NOT inserted (existing user)
        Mockito.verify(userMapper, Mockito.never()).insert(Mockito.any());
    }

    @Test
    void quickLoginPhone_invalid_code() {
        Mockito.when(captchaService.isRequired(Mockito.any())).thenReturn(false);
        Mockito.when(verificationService.verifyAndConsume("phone:t1:13800138000", "wrong_code")).thenReturn(false);
        
        assertThatThrownBy(() -> phoneAuthService.quickLoginPhone("t1", "13800138000", "wrong_code", null, null))
            .isInstanceOf(RuntimeException.class);
        
        // Verify no user operations were performed
        Mockito.verify(userMapper, Mockito.never()).selectOne(Mockito.any());
        Mockito.verify(userMapper, Mockito.never()).insert(Mockito.any());
    }

    @Test
    void quickLoginPhone_disabled_user() {
        Mockito.when(captchaService.isRequired(Mockito.any())).thenReturn(false);
        Mockito.when(verificationService.verifyAndConsume("phone:t1:13800138000", "123456")).thenReturn(true);
        User disabledUser = new User();
        disabledUser.setId(6L);
        disabledUser.setTenantId("t1");
        disabledUser.setUsername("disabled_user");
        disabledUser.setPhone("13800138000");
        disabledUser.setEnabled(false);
        Mockito.when(userMapper.selectOne(Mockito.any())).thenReturn(disabledUser);
        
        assertThatThrownBy(() -> phoneAuthService.quickLoginPhone("t1", "13800138000", "123456", null, null))
            .isInstanceOf(RuntimeException.class);
        
        // Verify no token was generated
        Mockito.verify(jwtService, Mockito.never()).generate(Mockito.any(), Mockito.any(), Mockito.any());
    }
}
