package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.store.UserMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for user management operations including
 * status updates and user queries.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserMapper userMapper;
    private final TenantService tenantService;
    private final AuthLogService authLogService;

    /**
     * Update user's enabled/disabled status
     */
    public User updateUserStatus(AuthRequests.UserStatusUpdate req) {
        tenantService.requireEnabled(req.getTenantId());
        
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        User user = userMapper.selectOne(wrapper);
        
        if (user == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        
        user.setEnabled(req.isEnabled());
        userMapper.updateById(user);
        
        logger.info("User status updated tenant={} username={} enabled={}", 
            req.getTenantId(), req.getUsername(), req.isEnabled());
        safeRecord(req.getTenantId(), user.getId(), AuthAction.UPDATE_USER_STATUS, true);
        return user;
    }

    /**
     * Find user by tenant and username
     */
    public User findByTenantAndUsername(String tenantId, String username) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("username", username);
        return userMapper.selectOne(wrapper);
    }

    /**
     * Find user by tenant and email
     */
    public User findByTenantAndEmail(String tenantId, String email) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("email", email);
        return userMapper.selectOne(wrapper);
    }

    /**
     * Find user by tenant and phone
     */
    public User findByTenantAndPhone(String tenantId, String phone) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("phone", phone);
        return userMapper.selectOne(wrapper);
    }

    /**
     * Check if username exists for tenant
     */
    public boolean existsByTenantAndUsername(String tenantId, String username) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("tenant_id", tenantId).eq("username", username);
        return userMapper.selectCount(qw) > 0;
    }

    /**
     * Check if email exists for tenant
     */
    public boolean existsByTenantAndEmail(String tenantId, String email) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("tenant_id", tenantId).eq("email", email);
        return userMapper.selectCount(qw) > 0;
    }

    /**
     * Check if phone exists for tenant
     */
    public boolean existsByTenantAndPhone(String tenantId, String phone) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("tenant_id", tenantId).eq("phone", phone);
        return userMapper.selectCount(qw) > 0;
    }

    private void safeRecord(String tenantId, Long userId, AuthAction action, boolean success) {
        try {
            authLogService.record(tenantId, userId, action, success);
        } catch (Exception ex) {
            logger.error("Failed to record audit log for tenant={} action={}", tenantId, action, ex);
        }
    }
}
