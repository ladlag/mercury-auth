package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.PermissionRequests;
import com.mercury.auth.entity.Permission;
import com.mercury.auth.entity.UserGroup;
import com.mercury.auth.entity.UserGroupPermission;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.store.PermissionMapper;
import com.mercury.auth.store.UserGroupPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final Logger logger = LoggerFactory.getLogger(PermissionService.class);
    
    private final PermissionMapper permissionMapper;
    private final UserGroupPermissionMapper userGroupPermissionMapper;
    private final UserGroupService userGroupService;
    private final TenantService tenantService;

    public Permission createPermission(PermissionRequests.CreatePermission req) {
        tenantService.requireEnabled(req.getTenantId());
        
        QueryWrapper<Permission> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId())
               .eq("code", req.getCode());
        
        if (permissionMapper.selectOne(wrapper) != null) {
            throw new ApiException(ErrorCodes.DUPLICATE_ENTRY, "permission already exists");
        }
        
        Permission permission = new Permission();
        permission.setTenantId(req.getTenantId());
        permission.setCode(req.getCode());
        permission.setName(req.getName());
        permission.setType(req.getType());
        permission.setResource(req.getResource());
        permission.setDescription(req.getDescription());
        
        permissionMapper.insert(permission);
        logger.info("created permission id={} tenant={} code={}", 
                    permission.getId(), req.getTenantId(), req.getCode());
        return permission;
    }

    public Permission updatePermission(PermissionRequests.UpdatePermission req) {
        Permission permission = permissionMapper.selectById(req.getId());
        if (permission == null) {
            throw new ApiException(ErrorCodes.NOT_FOUND, "permission not found");
        }
        
        if (req.getName() != null) {
            permission.setName(req.getName());
        }
        if (req.getType() != null) {
            permission.setType(req.getType());
        }
        if (req.getResource() != null) {
            permission.setResource(req.getResource());
        }
        if (req.getDescription() != null) {
            permission.setDescription(req.getDescription());
        }
        
        permissionMapper.updateById(permission);
        logger.info("updated permission id={}", permission.getId());
        return permission;
    }

    public List<Permission> listPermissions(String tenantId) {
        tenantService.requireEnabled(tenantId);
        
        QueryWrapper<Permission> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId);
        
        return permissionMapper.selectList(wrapper);
    }

    public Permission getPermission(Long permissionId) {
        Permission permission = permissionMapper.selectById(permissionId);
        if (permission == null) {
            throw new ApiException(ErrorCodes.NOT_FOUND, "permission not found");
        }
        return permission;
    }

    @Transactional
    public void assignPermissionsToGroup(Long groupId, List<Long> permissionIds) {
        UserGroup group = userGroupService.getGroup(groupId);
        
        // Verify all permissions exist and belong to same tenant
        List<Permission> permissions = permissionMapper.selectBatchIds(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            throw new ApiException(ErrorCodes.NOT_FOUND, "one or more permissions not found");
        }
        
        for (Permission permission : permissions) {
            if (!permission.getTenantId().equals(group.getTenantId())) {
                throw new ApiException(ErrorCodes.VALIDATION_FAILED, "permission must be in same tenant as group");
            }
        }
        
        // Remove existing permissions for this group
        QueryWrapper<UserGroupPermission> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("group_id", groupId);
        userGroupPermissionMapper.delete(deleteWrapper);
        
        // Add new permissions
        for (Long permissionId : permissionIds) {
            UserGroupPermission ugp = new UserGroupPermission();
            ugp.setGroupId(groupId);
            ugp.setPermissionId(permissionId);
            userGroupPermissionMapper.insert(ugp);
        }
        
        logger.info("assigned {} permissions to group {}", permissionIds.size(), groupId);
    }

    public List<Permission> getGroupPermissions(Long groupId) {
        QueryWrapper<UserGroupPermission> wrapper = new QueryWrapper<>();
        wrapper.eq("group_id", groupId);
        
        List<UserGroupPermission> ugps = userGroupPermissionMapper.selectList(wrapper);
        
        if (ugps.isEmpty()) {
            return List.of();
        }
        
        List<Long> permissionIds = ugps.stream()
                                       .map(UserGroupPermission::getPermissionId)
                                       .collect(java.util.stream.Collectors.toList());
        
        return permissionMapper.selectBatchIds(permissionIds);
    }

    public List<Permission> getUserPermissions(Long userId, String tenantId) {
        List<UserGroup> userGroups = userGroupService.getUserGroups(userId);
        
        if (userGroups.isEmpty()) {
            return List.of();
        }
        
        // Filter enabled groups for the tenant and collect group IDs
        List<Long> enabledGroupIds = new ArrayList<>();
        for (UserGroup group : userGroups) {
            if (group.getEnabled() && group.getTenantId().equals(tenantId)) {
                enabledGroupIds.add(group.getId());
            }
        }
        
        if (enabledGroupIds.isEmpty()) {
            return List.of();
        }
        
        // Batch fetch all permissions for all groups to avoid N+1 queries
        QueryWrapper<UserGroupPermission> wrapper = new QueryWrapper<>();
        wrapper.in("group_id", enabledGroupIds);
        List<UserGroupPermission> ugps = userGroupPermissionMapper.selectList(wrapper);
        
        if (ugps.isEmpty()) {
            return List.of();
        }
        
        // Collect unique permission IDs
        Set<Long> permissionIds = new HashSet<>();
        for (UserGroupPermission ugp : ugps) {
            permissionIds.add(ugp.getPermissionId());
        }
        
        return new ArrayList<>(permissionMapper.selectBatchIds(permissionIds));
    }

    public boolean hasPermission(Long userId, String permissionCode, String tenantId) {
        List<Permission> userPermissions = getUserPermissions(userId, tenantId);
        
        for (Permission permission : userPermissions) {
            if (permission.getCode().equals(permissionCode)) {
                logger.debug("user {} has permission {}", userId, permissionCode);
                return true;
            }
        }
        
        logger.debug("user {} does not have permission {}", userId, permissionCode);
        return false;
    }

    public void requirePermission(Long userId, String permissionCode, String tenantId) {
        if (!hasPermission(userId, permissionCode, tenantId)) {
            throw new ApiException(ErrorCodes.PERMISSION_DENIED, "permission denied");
        }
    }
}
