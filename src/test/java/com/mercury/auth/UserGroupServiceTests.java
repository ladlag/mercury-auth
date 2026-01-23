package com.mercury.auth;

import com.mercury.auth.dto.UserGroupRequests;
import com.mercury.auth.entity.Tenant;
import com.mercury.auth.entity.User;
import com.mercury.auth.entity.UserGroup;
import com.mercury.auth.entity.UserGroupMember;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.service.TenantService;
import com.mercury.auth.service.UserGroupService;
import com.mercury.auth.store.UserGroupMapper;
import com.mercury.auth.store.UserGroupMemberMapper;
import com.mercury.auth.store.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class UserGroupServiceTests {

    private UserGroupMapper userGroupMapper;
    private UserGroupMemberMapper userGroupMemberMapper;
    private UserMapper userMapper;
    private TenantService tenantService;
    private UserGroupService userGroupService;

    @BeforeEach
    void setup() {
        userGroupMapper = Mockito.mock(UserGroupMapper.class);
        userGroupMemberMapper = Mockito.mock(UserGroupMemberMapper.class);
        userMapper = Mockito.mock(UserMapper.class);
        tenantService = Mockito.mock(TenantService.class);
        userGroupService = new UserGroupService(userGroupMapper, userGroupMemberMapper, userMapper, tenantService);
    }

    @Test
    void createGroup_creates_successfully() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        Mockito.when(userGroupMapper.selectOne(any())).thenReturn(null);
        Mockito.when(userGroupMapper.insert(any())).thenReturn(1);

        UserGroupRequests.CreateGroup req = new UserGroupRequests.CreateGroup();
        req.setTenantId("t1");
        req.setName("VIP Users");
        req.setDescription("VIP membership group");

        UserGroup result = userGroupService.createGroup(req);

        ArgumentCaptor<UserGroup> captor = ArgumentCaptor.forClass(UserGroup.class);
        verify(userGroupMapper).insert(captor.capture());
        UserGroup inserted = captor.getValue();
        assertThat(inserted.getTenantId()).isEqualTo("t1");
        assertThat(inserted.getName()).isEqualTo("VIP Users");
        assertThat(inserted.getDescription()).isEqualTo("VIP membership group");
        assertThat(inserted.getEnabled()).isTrue();
    }

    @Test
    void createGroup_rejects_duplicate_name() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        UserGroup existing = new UserGroup();
        existing.setId(1L);
        existing.setTenantId("t1");
        existing.setName("VIP Users");
        Mockito.when(userGroupMapper.selectOne(any())).thenReturn(existing);

        UserGroupRequests.CreateGroup req = new UserGroupRequests.CreateGroup();
        req.setTenantId("t1");
        req.setName("VIP Users");

        assertThatThrownBy(() -> userGroupService.createGroup(req))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void addUserToGroup_adds_successfully() {
        User user = new User();
        user.setId(100L);
        user.setTenantId("t1");
        Mockito.when(userMapper.selectById(100L)).thenReturn(user);

        UserGroup group = new UserGroup();
        group.setId(10L);
        group.setTenantId("t1");
        Mockito.when(userGroupMapper.selectById(10L)).thenReturn(group);

        Mockito.when(userGroupMemberMapper.selectOne(any())).thenReturn(null);
        Mockito.when(userGroupMemberMapper.insert(any())).thenReturn(1);

        UserGroupRequests.AddUserToGroup req = new UserGroupRequests.AddUserToGroup();
        req.setUserId(100L);
        req.setGroupId(10L);

        userGroupService.addUserToGroup(req);

        ArgumentCaptor<UserGroupMember> captor = ArgumentCaptor.forClass(UserGroupMember.class);
        verify(userGroupMemberMapper).insert(captor.capture());
        UserGroupMember inserted = captor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(100L);
        assertThat(inserted.getGroupId()).isEqualTo(10L);
    }

    @Test
    void addUserToGroup_rejects_different_tenant() {
        User user = new User();
        user.setId(100L);
        user.setTenantId("t1");
        Mockito.when(userMapper.selectById(100L)).thenReturn(user);

        UserGroup group = new UserGroup();
        group.setId(10L);
        group.setTenantId("t2");
        Mockito.when(userGroupMapper.selectById(10L)).thenReturn(group);

        UserGroupRequests.AddUserToGroup req = new UserGroupRequests.AddUserToGroup();
        req.setUserId(100L);
        req.setGroupId(10L);

        assertThatThrownBy(() -> userGroupService.addUserToGroup(req))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void getUserGroups_returns_groups() {
        UserGroupMember member1 = new UserGroupMember();
        member1.setUserId(100L);
        member1.setGroupId(10L);

        UserGroupMember member2 = new UserGroupMember();
        member2.setUserId(100L);
        member2.setGroupId(20L);

        Mockito.when(userGroupMemberMapper.selectList(any())).thenReturn(Arrays.asList(member1, member2));

        UserGroup group1 = new UserGroup();
        group1.setId(10L);
        group1.setName("VIP");

        UserGroup group2 = new UserGroup();
        group2.setId(20L);
        group2.setName("Premium");

        Mockito.when(userGroupMapper.selectBatchIds(any())).thenReturn(Arrays.asList(group1, group2));

        List<UserGroup> groups = userGroupService.getUserGroups(100L);

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).getName()).isEqualTo("VIP");
        assertThat(groups.get(1).getName()).isEqualTo("Premium");
    }
}
