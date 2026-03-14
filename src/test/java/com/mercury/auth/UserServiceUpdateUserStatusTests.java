package com.mercury.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for UserService.updateUserStatus - tenant admin user enable/disable
 */
public class UserServiceUpdateUserStatusTests {

    private UserMapper userMapper;
    private TenantService tenantService;
    private AuthLogService authLogService;
    private TokenCacheService tokenCacheService;
    private TenantUserCountService tenantUserCountService;
    private UserService userService;

    @BeforeEach
    void setup() {
        userMapper = Mockito.mock(UserMapper.class);
        tenantService = Mockito.mock(TenantService.class);
        authLogService = Mockito.mock(AuthLogService.class);
        tokenCacheService = Mockito.mock(TokenCacheService.class);
        tenantUserCountService = Mockito.mock(TenantUserCountService.class);
        userService = new UserService(userMapper, tenantService, authLogService,
            tokenCacheService, tenantUserCountService);
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

    private AuthRequests.UserStatusUpdate createRequest(String tenantId, String username, boolean enabled) {
        AuthRequests.UserStatusUpdate req = new AuthRequests.UserStatusUpdate();
        req.setTenantId(tenantId);
        req.setUsername(username);
        req.setEnabled(enabled);
        return req;
    }

    @Test
    void updateUserStatus_admin_disables_user_successfully() {
        String tenantId = "t1";
        Long adminUserId = 1L;
        Long targetUserId = 2L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);
        User targetUser = createRegularUser(tenantId, targetUserId, "user01");

        // First selectOne returns admin (for admin check), second returns target user
        when(userMapper.selectOne(any(QueryWrapper.class)))
            .thenReturn(admin)
            .thenReturn(targetUser);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        AuthRequests.UserStatusUpdate req = createRequest(tenantId, "user01", false);
        User result = userService.updateUserStatus(req, adminUserId);

        assertThat(result.getEnabled()).isFalse();
        verify(userMapper).updateById(any(User.class));
        verify(tokenCacheService).evictAllForUserStatusChange(tenantId, targetUserId);
    }

    @Test
    void updateUserStatus_admin_enables_user_successfully() {
        String tenantId = "t1";
        Long adminUserId = 1L;
        Long targetUserId = 2L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);
        User targetUser = createRegularUser(tenantId, targetUserId, "user01");
        targetUser.setEnabled(false);

        when(userMapper.selectOne(any(QueryWrapper.class)))
            .thenReturn(admin)
            .thenReturn(targetUser);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        AuthRequests.UserStatusUpdate req = createRequest(tenantId, "user01", true);
        User result = userService.updateUserStatus(req, adminUserId);

        assertThat(result.getEnabled()).isTrue();
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    void updateUserStatus_rejects_non_admin() {
        String tenantId = "t1";
        Long regularUserId = 2L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User regularUser = createRegularUser(tenantId, regularUserId, "user01");
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(regularUser);

        AuthRequests.UserStatusUpdate req = createRequest(tenantId, "user02", false);

        assertThatThrownBy(() -> userService.updateUserStatus(req, regularUserId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCodes.FORBIDDEN_OPERATION);
    }

    @Test
    void updateUserStatus_rejects_nonexistent_requesting_user() {
        String tenantId = "t1";
        Long nonExistentUserId = 999L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        AuthRequests.UserStatusUpdate req = createRequest(tenantId, "user01", false);

        assertThatThrownBy(() -> userService.updateUserStatus(req, nonExistentUserId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCodes.USER_NOT_FOUND);
    }

    @Test
    void updateUserStatus_rejects_nonexistent_target_user() {
        String tenantId = "t1";
        Long adminUserId = 1L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);

        // First call returns admin, second call returns null (target not found)
        when(userMapper.selectOne(any(QueryWrapper.class)))
            .thenReturn(admin)
            .thenReturn(null);

        AuthRequests.UserStatusUpdate req = createRequest(tenantId, "nonexistent", false);

        assertThatThrownBy(() -> userService.updateUserStatus(req, adminUserId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCodes.USER_NOT_FOUND);
    }

    @Test
    void updateUserStatus_admin_cannot_disable_self() {
        String tenantId = "t1";
        Long adminUserId = 1L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);

        // First selectOne returns admin (for admin check), second returns admin again (target is self)
        when(userMapper.selectOne(any(QueryWrapper.class)))
            .thenReturn(admin)
            .thenReturn(admin);

        AuthRequests.UserStatusUpdate req = createRequest(tenantId, "admin01", false);

        assertThatThrownBy(() -> userService.updateUserStatus(req, adminUserId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCodes.FORBIDDEN_OPERATION);

        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void updateUserStatus_admin_can_enable_self() {
        String tenantId = "t1";
        Long adminUserId = 1L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);

        when(userMapper.selectOne(any(QueryWrapper.class)))
            .thenReturn(admin)
            .thenReturn(admin);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        // Enabling self is allowed (only disabling self is blocked)
        AuthRequests.UserStatusUpdate req = createRequest(tenantId, "admin01", true);
        User result = userService.updateUserStatus(req, adminUserId);

        assertThat(result.getEnabled()).isTrue();
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    void updateUserStatus_evicts_token_cache() {
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

        AuthRequests.UserStatusUpdate req = createRequest(tenantId, "user01", false);
        userService.updateUserStatus(req, adminUserId);

        verify(tokenCacheService).evictAllForUserStatusChange(tenantId, targetUserId);
    }
}
