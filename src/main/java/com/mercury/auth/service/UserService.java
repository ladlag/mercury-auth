package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.TenantUserItem;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.store.UserMapper;
import com.mercury.auth.util.XssSanitizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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
    @Lazy
    private final TokenCacheService tokenCacheService;
    @Lazy
    private final TenantUserCountService tenantUserCountService;

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
        
        // SECURITY: Evict all token caches when user status changes
        // This prevents disabled users from continuing to use cached tokens
        tokenCacheService.evictAllForUserStatusChange(req.getTenantId(), user.getId());
        
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
    
    /**
     * Count total users for a tenant
     */
    public long countUsersByTenant(String tenantId) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("tenant_id", tenantId);
        return userMapper.selectCount(qw);
    }
    
    /**
     * Check if tenant has reached maximum users limit.
     * Uses TenantUserCountService for optimized counting with Redis cache and auto-recovery.
     * 
     * @param tenantId The tenant ID to check
     * @throws ApiException if max users limit is reached
     */
    public void checkMaxUsersLimit(String tenantId) {
        // Delegate to TenantUserCountService which handles:
        // - Redis caching for performance
        // - Auto-recovery from cache misses
        // - Fallback to database on Redis failures
        // - Stale counter detection and re-sync
        tenantUserCountService.checkMaxUsersLimit(tenantId);
    }
    
    /**
     * Notify the count service that a user was created.
     * This increments the cached counter in Redis.
     * Safe to call even if Redis is unavailable.
     * 
     * @param tenantId The tenant ID
     */
    public void notifyUserCreated(String tenantId) {
        tenantUserCountService.incrementUserCount(tenantId);
    }
    
    /**
     * Notify the count service that a user was deleted.
     * This decrements the cached counter in Redis.
     * Safe to call even if Redis is unavailable.
     * 
     * @param tenantId The tenant ID
     */
    public void notifyUserDeleted(String tenantId) {
        tenantUserCountService.decrementUserCount(tenantId);
    }
    
    /**
     * List all users for a specific tenant.
     * Only accessible by tenant admin users (userType = TENANT_ADMIN).
     * 
     * @param tenantId The tenant ID
     * @param requestingUserId The ID of the user making the request
     * @return List of tenant user items
     * @throws ApiException if the requesting user is not a tenant admin
     */
    public List<TenantUserItem> listTenantUsers(String tenantId, Long requestingUserId) {
        tenantService.requireEnabled(tenantId);
        
        // Check that the requesting user is a tenant admin
        QueryWrapper<User> adminWrapper = new QueryWrapper<>();
        adminWrapper.eq("tenant_id", tenantId).eq("id", requestingUserId);
        User requestingUser = userMapper.selectOne(adminWrapper);
        
        if (requestingUser == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        
        if (!"TENANT_ADMIN".equals(requestingUser.getUserType())) {
            logger.warn("listTenantUsers forbidden: user {} is not TENANT_ADMIN in tenant {}", 
                requestingUserId, tenantId);
            throw new ApiException(ErrorCodes.FORBIDDEN_OPERATION, "only tenant admin can access user list");
        }
        
        // Query all users for this tenant
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).orderByAsc("id");
        List<User> users = userMapper.selectList(wrapper);
        
        return users.stream()
                .map(user -> TenantUserItem.builder()
                        .userId(user.getId())
                        .username(XssSanitizer.sanitize(user.getUsername()))
                        .nickname(XssSanitizer.sanitize(user.getNickname()))
                        .userType(user.getUserType())
                        .email(XssSanitizer.sanitize(user.getEmail()))
                        .phone(XssSanitizer.sanitize(user.getPhone()))
                        .enabled(user.getEnabled())
                        .createdAt(user.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private void safeRecord(String tenantId, Long userId, AuthAction action, boolean success) {
        authLogService.safeRecord(tenantId, userId, action, success);
    }
}
