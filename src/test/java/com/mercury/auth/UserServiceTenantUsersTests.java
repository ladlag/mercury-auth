package com.mercury.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.AuthAction;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for UserService tenant user list and userType/nickname fields
 */
public class UserServiceTenantUsersTests {

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
            Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class),
            tokenCacheService, tenantUserCountService);
    }

    @Test
    void listTenantUsers_returns_users_for_admin() {
        String tenantId = "t1";
        Long adminUserId = 1L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        // Admin user
        User admin = new User();
        admin.setId(adminUserId);
        admin.setTenantId(tenantId);
        admin.setUsername("admin01");
        admin.setUserType(UserType.TENANT_ADMIN);
        admin.setNickname("Admin");
        admin.setEnabled(true);

        // Regular user
        User regularUser = new User();
        regularUser.setId(2L);
        regularUser.setTenantId(tenantId);
        regularUser.setUsername("user01");
        regularUser.setUserType(UserType.USER);
        regularUser.setNickname("Regular User");
        regularUser.setEmail("user@test.com");
        regularUser.setPhone("13800138000");
        regularUser.setEnabled(true);
        regularUser.setCreatedAt(LocalDateTime.now());

        // Mock admin lookup (first selectOne call)
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(admin);
        // Mock user list
        when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(admin, regularUser));

        List<TenantUserItem> result = userService.listTenantUsers(tenantId, adminUserId, 1, 20);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUsername()).isEqualTo("admin01");
        assertThat(result.get(0).getUserType()).isEqualTo(UserType.TENANT_ADMIN);
        assertThat(result.get(0).getNickname()).isEqualTo("Admin");
        assertThat(result.get(1).getUsername()).isEqualTo("user01");
        assertThat(result.get(1).getUserType()).isEqualTo(UserType.USER);
        assertThat(result.get(1).getEmail()).isEqualTo("user@test.com");
        assertThat(result.get(1).getPhone()).isEqualTo("13800138000");
    }

    @Test
    void listTenantUsers_rejects_non_admin() {
        String tenantId = "t1";
        Long regularUserId = 2L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);

        // Regular user trying to list
        User regularUser = new User();
        regularUser.setId(regularUserId);
        regularUser.setTenantId(tenantId);
        regularUser.setUsername("user01");
        regularUser.setUserType(UserType.USER);
        regularUser.setEnabled(true);

        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(regularUser);

        assertThatThrownBy(() -> userService.listTenantUsers(tenantId, regularUserId, 1, 20))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCodes.FORBIDDEN_OPERATION);
    }

    @Test
    void listTenantUsers_rejects_nonexistent_user() {
        String tenantId = "t1";
        Long nonExistentUserId = 999L;

        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> userService.listTenantUsers(tenantId, nonExistentUserId, 1, 20))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCodes.USER_NOT_FOUND);
    }

    @Test
    void user_entity_has_userType_and_nickname() {
        User user = new User();
        user.setUserType(UserType.TENANT_ADMIN);
        user.setNickname("Test Nickname");
        
        assertThat(user.getUserType()).isEqualTo(UserType.TENANT_ADMIN);
        assertThat(user.getNickname()).isEqualTo("Test Nickname");
    }

    @Test
    void user_entity_userType_defaults_handled() {
        User user = new User();
        // Without setting userType and nickname, they should be null (default in entity)
        assertThat(user.getUserType()).isNull();
        assertThat(user.getNickname()).isNull();
    }
}
