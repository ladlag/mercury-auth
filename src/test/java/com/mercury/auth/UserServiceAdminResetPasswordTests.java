package com.mercury.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.AdminResetPasswordResponse;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.UserType;
import com.mercury.auth.entity.Tenant;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.service.*;
import com.mercury.auth.store.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for UserService.adminResetPassword - tenant admin reset user password
 */
public class UserServiceAdminResetPasswordTests {

    private UserMapper userMapper;
    private TenantService tenantService;
    private AuthLogService authLogService;
    private PasswordEncoder passwordEncoder;
    private TokenCacheService tokenCacheService;
    private TenantUserCountService tenantUserCountService;
    private UserService userService;

    @BeforeEach
    void setup() {
        userMapper = Mockito.mock(UserMapper.class);
        tenantService = Mockito.mock(TenantService.class);
        authLogService = Mockito.mock(AuthLogService.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        tokenCacheService = Mockito.mock(TokenCacheService.class);
        tenantUserCountService = Mockito.mock(TenantUserCountService.class);
        userService = new UserService(userMapper, tenantService, authLogService,
            passwordEncoder, tokenCacheService, tenantUserCountService);
    }

    private User createAdminUser(String tenantId, Long userId) {
        User admin = new User();
        admin.setId(userId);
        admin.setTenantId(tenantId);
        admin.setUsername("admin01");
        admin.setUserType(UserType.TENANT_ADMIN);
        admin.setNickname("Admin");
        admin.setEnabled(true);
        return admin;
    }

    private User createRegularUser(String tenantId, Long userId, String username) {
        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setUserType(UserType.USER);
        user.setNickname("Regular User");
        user.setEnabled(true);
        return user;
    }

    private AuthRequests.AdminResetPassword createRequest(String tenantId, String username) {
        AuthRequests.AdminResetPassword req = new AuthRequests.AdminResetPassword();
        req.setTenantId(tenantId);
        req.setUsername(username);
        return req;
    }

    @Test
    void adminResetPassword_success() {
        String tenantId = "t1";
        Long adminUserId = 1L;
        Long targetUserId = 2L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);
        User targetUser = createRegularUser(tenantId, targetUserId, "user01");

        when(userMapper.selectOne(any(QueryWrapper.class)))
            .thenReturn(admin)
            .thenReturn(targetUser);
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_hash");

        AuthRequests.AdminResetPassword req = createRequest(tenantId, "user01");
        AdminResetPasswordResponse response = userService.adminResetPassword(req, adminUserId);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(targetUserId);
        assertThat(response.getUsername()).isEqualTo("user01");
        assertThat(response.getNewPassword()).isNotNull();
        assertThat(response.getNewPassword()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(response.getNewPassword()).hasSizeLessThanOrEqualTo(20);
        assertThat(response.getTenantId()).isEqualTo(tenantId);
        assertThat(response.getNickname()).isEqualTo("Regular User");
        assertThat(response.getUserType()).isEqualTo(UserType.USER);

        verify(userMapper).updateById(any(User.class));
        verify(passwordEncoder).encode(anyString());
    }

    @Test
    void adminResetPassword_rejects_non_admin() {
        String tenantId = "t1";
        Long regularUserId = 2L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User regularUser = createRegularUser(tenantId, regularUserId, "user01");
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(regularUser);

        AuthRequests.AdminResetPassword req = createRequest(tenantId, "user02");

        assertThatThrownBy(() -> userService.adminResetPassword(req, regularUserId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCodes.FORBIDDEN_OPERATION);

        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void adminResetPassword_rejects_nonexistent_requesting_user() {
        String tenantId = "t1";
        Long nonExistentUserId = 999L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        AuthRequests.AdminResetPassword req = createRequest(tenantId, "user01");

        assertThatThrownBy(() -> userService.adminResetPassword(req, nonExistentUserId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCodes.USER_NOT_FOUND);
    }

    @Test
    void adminResetPassword_rejects_nonexistent_target_user() {
        String tenantId = "t1";
        Long adminUserId = 1L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);

        when(userMapper.selectOne(any(QueryWrapper.class)))
            .thenReturn(admin)
            .thenReturn(null);

        AuthRequests.AdminResetPassword req = createRequest(tenantId, "nonexistent");

        assertThatThrownBy(() -> userService.adminResetPassword(req, adminUserId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCodes.USER_NOT_FOUND);

        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void adminResetPassword_generates_valid_password() {
        String tenantId = "t1";
        Long adminUserId = 1L;
        Long targetUserId = 2L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);
        User targetUser = createRegularUser(tenantId, targetUserId, "user01");

        when(userMapper.selectOne(any(QueryWrapper.class)))
            .thenReturn(admin)
            .thenReturn(targetUser);
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_hash");

        AuthRequests.AdminResetPassword req = createRequest(tenantId, "user01");
        AdminResetPasswordResponse response = userService.adminResetPassword(req, adminUserId);

        String password = response.getNewPassword();

        // Length should be 12
        assertThat(password).hasSize(12);

        // Should contain at least one uppercase
        assertThat(password).matches(".*[A-Z].*");

        // Should contain at least one lowercase
        assertThat(password).matches(".*[a-z].*");

        // Should contain at least one digit
        assertThat(password).matches(".*[0-9].*");

        // Should contain at least one special character
        assertThat(password).matches(".*[!@#$%^&*].*");

        // Should match the password validation pattern (no whitespace)
        assertThat(password).matches("^[a-zA-Z0-9\\p{Punct}]+$");
    }

    @Test
    void adminResetPassword_produces_different_passwords() {
        String tenantId = "t1";
        Long adminUserId = 1L;
        Long targetUserId = 2L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);
        User targetUser = createRegularUser(tenantId, targetUserId, "user01");

        when(userMapper.selectOne(any(QueryWrapper.class)))
            .thenReturn(admin, targetUser, admin, targetUser);
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_hash");

        AuthRequests.AdminResetPassword req = createRequest(tenantId, "user01");
        String password1 = userService.adminResetPassword(req, adminUserId).getNewPassword();
        String password2 = userService.adminResetPassword(req, adminUserId).getNewPassword();
        // While there's a tiny chance these could be equal, it's astronomically unlikely
        assertThat(password1).isNotEqualTo(password2);
    }
}
