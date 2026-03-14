package com.mercury.auth;

import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.UserType;
import com.mercury.auth.entity.User;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.service.*;
import com.mercury.auth.store.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify that the nickname field is properly received during
 * password registration, correctly stored on the User entity, and
 * returned after token verification.
 */
public class PasswordRegisterNicknameTest {

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private VerificationService verificationService;
    private RateLimitService rateLimitService;
    private TenantService tenantService;
    private AuthLogService authLogService;
    private CaptchaService captchaService;
    private PasswordEncryptionService passwordEncryptionService;
    private UserService userService;
    private PasswordAuthService passwordAuthService;

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
        passwordEncryptionService = Mockito.mock(PasswordEncryptionService.class);
        userService = Mockito.mock(UserService.class);

        // Mock password encryption service to return input as-is (no encryption)
        try {
            Mockito.doAnswer(invocation -> invocation.getArgument(1))
                    .when(passwordEncryptionService).processPassword(Mockito.any(), Mockito.any());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        passwordAuthService = new PasswordAuthService(userMapper, passwordEncoder, jwtService,
            verificationService, rateLimitService, tenantService, authLogService, captchaService, passwordEncryptionService, userService);
    }

    @Test
    void registerPassword_stores_nickname_on_user_entity() {
        Mockito.doReturn(new com.mercury.auth.entity.Tenant()).when(tenantService).requireEnabled("t1");
        Mockito.when(userMapper.selectCount(Mockito.any())).thenReturn(0L);
        Mockito.when(passwordEncoder.encode(Mockito.anyString())).thenReturn("hashed");
        Mockito.when(userMapper.insert(Mockito.any())).thenReturn(1);

        AuthRequests.PasswordRegister req = new AuthRequests.PasswordRegister();
        req.setTenantId("t1");
        req.setUsername("testuser");
        req.setNickname("Test Nickname");
        req.setPassword("password123");
        req.setConfirmPassword("password123");

        User result = passwordAuthService.registerPassword(req);

        // Capture the User entity passed to userMapper.insert
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        Mockito.verify(userMapper).insert(captor.capture());

        User inserted = captor.getValue();
        assertThat(inserted.getUsername()).isEqualTo("testuser");
        assertThat(inserted.getNickname()).isEqualTo("Test Nickname");
        assertThat(inserted.getTenantId()).isEqualTo("t1");
        assertThat(inserted.getUserType()).isEqualTo(UserType.USER);
    }

    @Test
    void registerPassword_stores_null_nickname_when_not_provided() {
        Mockito.doReturn(new com.mercury.auth.entity.Tenant()).when(tenantService).requireEnabled("t1");
        Mockito.when(userMapper.selectCount(Mockito.any())).thenReturn(0L);
        Mockito.when(passwordEncoder.encode(Mockito.anyString())).thenReturn("hashed");
        Mockito.when(userMapper.insert(Mockito.any())).thenReturn(1);

        AuthRequests.PasswordRegister req = new AuthRequests.PasswordRegister();
        req.setTenantId("t1");
        req.setUsername("testuser2");
        req.setPassword("password123");
        req.setConfirmPassword("password123");
        // nickname is NOT set

        User result = passwordAuthService.registerPassword(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        Mockito.verify(userMapper).insert(captor.capture());

        User inserted = captor.getValue();
        assertThat(inserted.getUsername()).isEqualTo("testuser2");
        assertThat(inserted.getNickname()).isNull();
    }
}
