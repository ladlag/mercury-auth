package com.mercury.auth;

import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.service.AuthLogService;
import com.mercury.auth.service.PhoneAuthService;
import com.mercury.auth.service.RateLimitService;
import com.mercury.auth.service.VerificationService;
import com.mercury.auth.store.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PhoneAuthServiceTests {

    private VerificationService verificationService;
    private UserMapper userMapper;
    private JwtService jwtService;
    private RateLimitService rateLimitService;
    private AuthLogService authLogService;
    private PhoneAuthService phoneAuthService;

    @BeforeEach
    void setup() {
        verificationService = Mockito.mock(VerificationService.class);
        userMapper = Mockito.mock(UserMapper.class);
        jwtService = Mockito.mock(JwtService.class);
        rateLimitService = Mockito.mock(RateLimitService.class);
        authLogService = Mockito.mock(AuthLogService.class);
        phoneAuthService = new PhoneAuthService(verificationService, userMapper, jwtService, rateLimitService, authLogService);
    }

    @Test
    void loginPhone_valid() {
        Mockito.when(verificationService.verifyAndConsume("phone:t1:138", "111111")).thenReturn(true);
        User u = new User();
        u.setId(3L);
        u.setTenantId("t1");
        u.setUsername("u3");
        u.setEnabled(true);
        Mockito.when(userMapper.selectOne(Mockito.any())).thenReturn(u);
        Mockito.when(jwtService.generate("t1", 3L, "u3")).thenReturn("tkn3");
        Mockito.when(jwtService.getTtlSeconds()).thenReturn(30L);
        AuthResponse resp = phoneAuthService.loginPhone("t1", "138", "111111");
        assertThat(resp.getAccessToken()).isEqualTo("tkn3");
    }

    @Test
    void loginPhone_invalid_code() {
        Mockito.when(verificationService.verifyAndConsume("phone:t1:138", "000000")).thenReturn(false);
        assertThatThrownBy(() -> phoneAuthService.loginPhone("t1", "138", "000000")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void sendPhoneCode_returns_ok() {
        Mockito.when(verificationService.generateCode()).thenReturn("123456");
        Mockito.when(userMapper.selectCount(Mockito.any())).thenReturn(0L);
        String result = phoneAuthService.sendPhoneCode("t1", "138", AuthRequests.VerificationPurpose.REGISTER);
        assertThat(result).isEqualTo("OK");
        Mockito.verify(verificationService).storeCode(Mockito.eq("phone:t1:138"), Mockito.eq("123456"), Mockito.any());
    }

    @Test
    void sendPhoneCode_register_duplicate_phone() {
        Mockito.when(userMapper.selectCount(Mockito.any())).thenReturn(1L);
        assertThatThrownBy(() -> phoneAuthService.sendPhoneCode("t1", "138", AuthRequests.VerificationPurpose.REGISTER))
                .isInstanceOf(RuntimeException.class);
    }
}
