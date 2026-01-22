package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.UserGroupRequests;
import com.mercury.auth.entity.User;
import com.mercury.auth.entity.UserGroup;
import com.mercury.auth.entity.UserGroupMember;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.store.UserGroupMapper;
import com.mercury.auth.store.UserGroupMemberMapper;
import com.mercury.auth.store.UserMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserGroupService {

    private static final Logger logger = LoggerFactory.getLogger(UserGroupService.class);
    
    private final UserGroupMapper userGroupMapper;
    private final UserGroupMemberMapper userGroupMemberMapper;
    private final UserMapper userMapper;
    private final TenantService tenantService;

    public UserGroup createGroup(UserGroupRequests.CreateGroup req) {
        tenantService.requireEnabled(req.getTenantId());
        
        QueryWrapper<UserGroup> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId())
               .eq("name", req.getName());
        
        if (userGroupMapper.selectOne(wrapper) != null) {
            throw new ApiException(ErrorCodes.DUPLICATE_ENTRY, "group already exists");
        }
        
        UserGroup group = new UserGroup();
        group.setTenantId(req.getTenantId());
        group.setName(req.getName());
        group.setDescription(req.getDescription());
        group.setEnabled(true);
        
        userGroupMapper.insert(group);
        logger.info("created user group id={} tenant={} name={}", 
                    group.getId(), req.getTenantId(), req.getName());
        return group;
    }

    public UserGroup updateGroup(UserGroupRequests.UpdateGroup req) {
        UserGroup group = userGroupMapper.selectById(req.getId());
        if (group == null) {
            throw new ApiException(ErrorCodes.NOT_FOUND, "group not found");
        }
        
        if (req.getName() != null) {
            group.setName(req.getName());
        }
        if (req.getDescription() != null) {
            group.setDescription(req.getDescription());
        }
        if (req.getEnabled() != null) {
            group.setEnabled(req.getEnabled());
        }
        
        userGroupMapper.updateById(group);
        logger.info("updated user group id={}", group.getId());
        return group;
    }

    public List<UserGroup> listGroups(String tenantId) {
        tenantService.requireEnabled(tenantId);
        
        QueryWrapper<UserGroup> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId);
        
        return userGroupMapper.selectList(wrapper);
    }

    public UserGroup getGroup(Long groupId) {
        UserGroup group = userGroupMapper.selectById(groupId);
        if (group == null) {
            throw new ApiException(ErrorCodes.NOT_FOUND, "group not found");
        }
        return group;
    }

    @Transactional
    public void addUserToGroup(UserGroupRequests.AddUserToGroup req) {
        User user = userMapper.selectById(req.getUserId());
        if (user == null) {
            throw new ApiException(ErrorCodes.NOT_FOUND, "user not found");
        }
        
        UserGroup group = userGroupMapper.selectById(req.getGroupId());
        if (group == null) {
            throw new ApiException(ErrorCodes.NOT_FOUND, "group not found");
        }
        
        if (!user.getTenantId().equals(group.getTenantId())) {
            throw new ApiException(ErrorCodes.VALIDATION_FAILED, "user and group must be in same tenant");
        }
        
        QueryWrapper<UserGroupMember> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", req.getUserId())
               .eq("group_id", req.getGroupId());
        
        if (userGroupMemberMapper.selectOne(wrapper) != null) {
            throw new ApiException(ErrorCodes.DUPLICATE_ENTRY, "user already in group");
        }
        
        UserGroupMember member = new UserGroupMember();
        member.setUserId(req.getUserId());
        member.setGroupId(req.getGroupId());
        
        userGroupMemberMapper.insert(member);
        logger.info("added user {} to group {}", req.getUserId(), req.getGroupId());
    }

    @Transactional
    public void removeUserFromGroup(UserGroupRequests.RemoveUserFromGroup req) {
        QueryWrapper<UserGroupMember> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", req.getUserId())
               .eq("group_id", req.getGroupId());
        
        int deleted = userGroupMemberMapper.delete(wrapper);
        if (deleted == 0) {
            throw new ApiException(ErrorCodes.NOT_FOUND, "user not in group");
        }
        
        logger.info("removed user {} from group {}", req.getUserId(), req.getGroupId());
    }

    public List<UserGroup> getUserGroups(Long userId) {
        QueryWrapper<UserGroupMember> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        
        List<UserGroupMember> members = userGroupMemberMapper.selectList(wrapper);
        
        if (members.isEmpty()) {
            return List.of();
        }
        
        List<Long> groupIds = members.stream()
                                     .map(UserGroupMember::getGroupId)
                                     .collect(java.util.stream.Collectors.toList());
        
        return userGroupMapper.selectBatchIds(groupIds);
    }

    public List<User> getGroupMembers(Long groupId) {
        QueryWrapper<UserGroupMember> wrapper = new QueryWrapper<>();
        wrapper.eq("group_id", groupId);
        
        List<UserGroupMember> members = userGroupMemberMapper.selectList(wrapper);
        
        if (members.isEmpty()) {
            return List.of();
        }
        
        List<Long> userIds = members.stream()
                                    .map(UserGroupMember::getUserId)
                                    .collect(java.util.stream.Collectors.toList());
        
        return userMapper.selectBatchIds(userIds);
    }
}
