package com.mercury.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.TenantUserItem;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for UserService.searchTenantUsers - tenant admin user search by username, phone, email
 */
public class UserServiceSearchTenantUsersTests {

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

    private User createRegularUser(String tenantId, Long userId) {
        User user = new User();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setUsername("user01");
        user.setUserType(UserType.USER);
        user.setNickname("Regular User");
        user.setEmail("user@test.com");
        user.setPhone("13800138000");
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    @Test
    void searchTenantUsers_by_username_returns_matching_user() {
        String tenantId = "t1";
        Long adminUserId = 1L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);
        User regularUser = createRegularUser(tenantId, 2L);

        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(admin);
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(regularUser));

        List<TenantUserItem> result = userService.searchTenantUsers(tenantId, adminUserId, "user01", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsername()).isEqualTo("user01");
        assertThat(result.get(0).getNickname()).isEqualTo("Regular User");
        assertThat(result.get(0).getUserType()).isEqualTo(UserType.USER);
        assertThat(result.get(0).getEmail()).isEqualTo("user@test.com");
        assertThat(result.get(0).getPhone()).isEqualTo("13800138000");
    }

    @Test
    void searchTenantUsers_by_phone_returns_matching_user() {
        String tenantId = "t1";
        Long adminUserId = 1L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);
        User regularUser = createRegularUser(tenantId, 2L);

        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(admin);
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(regularUser));

        List<TenantUserItem> result = userService.searchTenantUsers(tenantId, adminUserId, null, "13800138000", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPhone()).isEqualTo("13800138000");
    }

    @Test
    void searchTenantUsers_by_email_returns_matching_user() {
        String tenantId = "t1";
        Long adminUserId = 1L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);
        User regularUser = createRegularUser(tenantId, 2L);

        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(admin);
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(regularUser));

        List<TenantUserItem> result = userService.searchTenantUsers(tenantId, adminUserId, null, null, "user@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("user@test.com");
    }

    @Test
    void searchTenantUsers_rejects_non_admin() {
        String tenantId = "t1";
        Long regularUserId = 2L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User regularUser = new User();
        regularUser.setId(regularUserId);
        regularUser.setTenantId(tenantId);
        regularUser.setUsername("user01");
        regularUser.setUserType(UserType.USER);
        regularUser.setEnabled(true);

        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(regularUser);

        assertThatThrownBy(() -> userService.searchTenantUsers(tenantId, regularUserId, "user01", null, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCodes.FORBIDDEN_OPERATION);
    }

    @Test
    void searchTenantUsers_rejects_nonexistent_user() {
        String tenantId = "t1";
        Long nonExistentUserId = 999L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> userService.searchTenantUsers(tenantId, nonExistentUserId, "user01", null, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCodes.USER_NOT_FOUND);
    }

    @Test
    void searchTenantUsers_rejects_no_search_criteria() {
        String tenantId = "t1";
        Long adminUserId = 1L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(admin);

        assertThatThrownBy(() -> userService.searchTenantUsers(tenantId, adminUserId, null, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCodes.VALIDATION_FAILED);
    }

    @Test
    void searchTenantUsers_rejects_empty_search_criteria() {
        String tenantId = "t1";
        Long adminUserId = 1L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(admin);

        assertThatThrownBy(() -> userService.searchTenantUsers(tenantId, adminUserId, "", "", ""))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCodes.VALIDATION_FAILED);
    }

    @Test
    void searchTenantUsers_returns_empty_list_when_no_match() {
        String tenantId = "t1";
        Long adminUserId = 1L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(admin);
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        List<TenantUserItem> result = userService.searchTenantUsers(tenantId, adminUserId, "nonexistent", null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void searchTenantUsers_with_multiple_criteria() {
        String tenantId = "t1";
        Long adminUserId = 1L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        User admin = createAdminUser(tenantId, adminUserId);
        User user1 = createRegularUser(tenantId, 2L);
        User user2 = new User();
        user2.setId(3L);
        user2.setTenantId(tenantId);
        user2.setUsername("user02");
        user2.setUserType(UserType.USER);
        user2.setNickname("Another User");
        user2.setEmail("user2@test.com");
        user2.setPhone("13900139000");
        user2.setEnabled(true);
        user2.setCreatedAt(LocalDateTime.now());

        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(admin);
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(user1, user2));

        List<TenantUserItem> result = userService.searchTenantUsers(tenantId, adminUserId, "user01", "13900139000", null);

        assertThat(result).hasSize(2);
    }
}
