package com.mercury.auth;

import com.mercury.auth.dto.PermissionRequests;
import com.mercury.auth.entity.Permission;
import com.mercury.auth.entity.Tenant;
import com.mercury.auth.entity.UserGroup;
import com.mercury.auth.entity.UserGroupPermission;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.service.PermissionService;
import com.mercury.auth.service.TenantService;
import com.mercury.auth.service.UserGroupService;
import com.mercury.auth.store.PermissionMapper;
import com.mercury.auth.store.UserGroupPermissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

public class PermissionServiceTests {

    private PermissionMapper permissionMapper;
    private UserGroupPermissionMapper userGroupPermissionMapper;
    private UserGroupService userGroupService;
    private TenantService tenantService;
    private PermissionService permissionService;

    @BeforeEach
    void setup() {
        permissionMapper = Mockito.mock(PermissionMapper.class);
        userGroupPermissionMapper = Mockito.mock(UserGroupPermissionMapper.class);
        userGroupService = Mockito.mock(UserGroupService.class);
        tenantService = Mockito.mock(TenantService.class);
        permissionService = new PermissionService(permissionMapper, userGroupPermissionMapper, userGroupService, tenantService);
    }

    @Test
    void createPermission_creates_successfully() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        Mockito.when(permissionMapper.selectOne(any())).thenReturn(null);
        Mockito.when(permissionMapper.insert(any())).thenReturn(1);

        PermissionRequests.CreatePermission req = new PermissionRequests.CreatePermission();
        req.setTenantId("t1");
        req.setCode("VIEW_DASHBOARD");
        req.setName("View Dashboard");
        req.setType("MENU");
        req.setResource("/dashboard");

        Permission result = permissionService.createPermission(req);

        ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
        verify(permissionMapper).insert(captor.capture());
        Permission inserted = captor.getValue();
        assertThat(inserted.getTenantId()).isEqualTo("t1");
        assertThat(inserted.getCode()).isEqualTo("VIEW_DASHBOARD");
        assertThat(inserted.getName()).isEqualTo("View Dashboard");
        assertThat(inserted.getType()).isEqualTo("MENU");
        assertThat(inserted.getResource()).isEqualTo("/dashboard");
    }

    @Test
    void createPermission_rejects_duplicate_code() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        Permission existing = new Permission();
        existing.setId(1L);
        existing.setCode("VIEW_DASHBOARD");
        Mockito.when(permissionMapper.selectOne(any())).thenReturn(existing);

        PermissionRequests.CreatePermission req = new PermissionRequests.CreatePermission();
        req.setTenantId("t1");
        req.setCode("VIEW_DASHBOARD");
        req.setName("View Dashboard");
        req.setType("MENU");

        assertThatThrownBy(() -> permissionService.createPermission(req))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void hasPermission_returns_true_when_user_has_permission() {
        UserGroup group = new UserGroup();
        group.setId(10L);
        group.setTenantId("t1");
        group.setEnabled(true);

        Mockito.when(userGroupService.getUserGroups(100L)).thenReturn(Arrays.asList(group));

        UserGroupPermission ugp = new UserGroupPermission();
        ugp.setGroupId(10L);
        ugp.setPermissionId(1L);

        Mockito.when(userGroupPermissionMapper.selectList(any())).thenReturn(Arrays.asList(ugp));

        Permission permission = new Permission();
        permission.setId(1L);
        permission.setCode("VIEW_DASHBOARD");
        permission.setTenantId("t1");

        Mockito.when(permissionMapper.selectBatchIds(any())).thenReturn(Arrays.asList(permission));

        boolean hasPermission = permissionService.hasPermission(100L, "VIEW_DASHBOARD", "t1");

        assertThat(hasPermission).isTrue();
    }

    @Test
    void hasPermission_returns_false_when_user_lacks_permission() {
        Mockito.when(userGroupService.getUserGroups(100L)).thenReturn(Arrays.asList());

        boolean hasPermission = permissionService.hasPermission(100L, "VIEW_DASHBOARD", "t1");

        assertThat(hasPermission).isFalse();
    }

    @Test
    void requirePermission_throws_when_no_permission() {
        Mockito.when(userGroupService.getUserGroups(100L)).thenReturn(Arrays.asList());

        assertThatThrownBy(() -> permissionService.requirePermission(100L, "VIEW_DASHBOARD", "t1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void getUserPermissions_returns_combined_permissions_from_all_groups() {
        UserGroup group1 = new UserGroup();
        group1.setId(10L);
        group1.setTenantId("t1");
        group1.setEnabled(true);

        UserGroup group2 = new UserGroup();
        group2.setId(20L);
        group2.setTenantId("t1");
        group2.setEnabled(true);

        Mockito.when(userGroupService.getUserGroups(100L)).thenReturn(Arrays.asList(group1, group2));

        UserGroupPermission ugp1 = new UserGroupPermission();
        ugp1.setGroupId(10L);
        ugp1.setPermissionId(1L);

        UserGroupPermission ugp2 = new UserGroupPermission();
        ugp2.setGroupId(20L);
        ugp2.setPermissionId(2L);

        // Now returns both permissions in single query (optimized)
        Mockito.when(userGroupPermissionMapper.selectList(any()))
                .thenReturn(Arrays.asList(ugp1, ugp2));

        Permission perm1 = new Permission();
        perm1.setId(1L);
        perm1.setCode("VIEW_DASHBOARD");

        Permission perm2 = new Permission();
        perm2.setId(2L);
        perm2.setCode("EDIT_PROFILE");

        Mockito.when(permissionMapper.selectBatchIds(any()))
                .thenReturn(Arrays.asList(perm1, perm2));

        List<Permission> permissions = permissionService.getUserPermissions(100L, "t1");

        assertThat(permissions).hasSize(2);
    }
}
