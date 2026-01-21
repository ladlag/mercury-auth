package com.mercury.auth;

import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.service.PhoneAuthService;
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
    private PhoneAuthService phoneAuthService;

    @BeforeEach
    void setup() {
        verificationService = Mockito.mock(VerificationService.class);
        userMapper = Mockito.mock(UserMapper.class);
        jwtService = Mockito.mock(JwtService.class);
        phoneAuthService = new PhoneAuthService(verificationService, userMapper, jwtService);
    }

    @Test
    void loginPhone_valid() {
        Mockito.when(verificationService.verify("phone:t1:138", "111111")).thenReturn(true);
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
        Mockito.when(verificationService.verify("phone:t1:138", "000000")).thenReturn(false);
        assertThatThrownBy(() -> phoneAuthService.loginPhone("t1", "138", "000000")).isInstanceOf(RuntimeException.class);
    }
}
