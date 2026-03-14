package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.AdminResetPasswordResponse;
import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.TenantUserItem;
import com.mercury.auth.dto.UserType;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.store.UserMapper;
import com.mercury.auth.util.XssSanitizer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
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
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*";
    private static final String ALL_CHARS = UPPER + LOWER + DIGITS + SPECIAL;
    private static final int GENERATED_PASSWORD_LENGTH = 12;
    private final UserMapper userMapper;
    private final TenantService tenantService;
    private final AuthLogService authLogService;
    private final PasswordEncoder passwordEncoder;
    @Lazy
    private final TokenCacheService tokenCacheService;
    @Lazy
    private final TenantUserCountService tenantUserCountService;

    /**
     * Update user's enabled/disabled status.
     * Only accessible by tenant admin users (userType = TENANT_ADMIN).
     * Admins cannot disable themselves.
     * When a user is disabled, all their tokens are revoked.
     *
     * @param req The status update request containing tenantId, username, and enabled flag
     * @param requestingUserId The ID of the user making the request (from JWT)
     * @return The updated user
     * @throws ApiException if the requesting user is not a tenant admin, or trying to disable self
     */
    public User updateUserStatus(AuthRequests.UserStatusUpdate req, Long requestingUserId) {
        tenantService.requireEnabled(req.getTenantId());
        
        // Check that the requesting user is a tenant admin
        QueryWrapper<User> adminWrapper = new QueryWrapper<>();
        adminWrapper.eq("tenant_id", req.getTenantId()).eq("id", requestingUserId);
        User requestingUser = userMapper.selectOne(adminWrapper);
        
        if (requestingUser == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "requesting user not found");
        }
        
        if (UserType.TENANT_ADMIN != requestingUser.getUserType()) {
            logger.warn("updateUserStatus forbidden: user {} is not TENANT_ADMIN in tenant {}", 
                requestingUserId, req.getTenantId());
            throw new ApiException(ErrorCodes.FORBIDDEN_OPERATION, "only tenant admin can update user status");
        }
        
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        User user = userMapper.selectOne(wrapper);
        
        if (user == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        
        // Prevent admin from disabling themselves
        if (!req.isEnabled() && user.getId().equals(requestingUserId)) {
            throw new ApiException(ErrorCodes.FORBIDDEN_OPERATION, "cannot disable your own account");
        }
        
        user.setEnabled(req.isEnabled());
        userMapper.updateById(user);
        
        // SECURITY: Evict all token caches when user status changes
        // This prevents disabled users from continuing to use cached tokens
        tokenCacheService.evictAllForUserStatusChange(req.getTenantId(), user.getId());
        
        logger.info("User status updated tenant={} username={} enabled={} by adminUserId={}", 
            req.getTenantId(), req.getUsername(), req.isEnabled(), requestingUserId);
        safeRecord(req.getTenantId(), user.getId(), AuthAction.UPDATE_USER_STATUS, true);
        return user;
    }

    /**
     * Reset a user's password by tenant admin.
     * Generates a random password that meets password policy requirements and returns it.
     * Only accessible by tenant admin users (userType = TENANT_ADMIN).
     * After reset, all tokens for the target user are revoked.
     *
     * @param req The admin reset password request containing tenantId and username
     * @param requestingUserId The ID of the user making the request (from JWT)
     * @return AdminResetPasswordResponse containing user info and the generated password
     * @throws ApiException if the requesting user is not a tenant admin, or target user not found
     */
    public AdminResetPasswordResponse adminResetPassword(AuthRequests.AdminResetPassword req, Long requestingUserId) {
        tenantService.requireEnabled(req.getTenantId());
        
        // Check that the requesting user is a tenant admin
        QueryWrapper<User> adminWrapper = new QueryWrapper<>();
        adminWrapper.eq("tenant_id", req.getTenantId()).eq("id", requestingUserId);
        User requestingUser = userMapper.selectOne(adminWrapper);
        
        if (requestingUser == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "requesting user not found");
        }
        
        if (UserType.TENANT_ADMIN != requestingUser.getUserType()) {
            logger.warn("adminResetPassword forbidden: user {} is not TENANT_ADMIN in tenant {}", 
                requestingUserId, req.getTenantId());
            throw new ApiException(ErrorCodes.FORBIDDEN_OPERATION, "only tenant admin can reset user password");
        }
        
        // Find the target user
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        User user = userMapper.selectOne(wrapper);
        
        if (user == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        
        // Generate random password and update
        String newPassword = generateRandomPassword();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        
        logger.info("Admin reset password tenant={} username={} by adminUserId={}", 
            req.getTenantId(), req.getUsername(), requestingUserId);
        safeRecord(req.getTenantId(), user.getId(), AuthAction.ADMIN_RESET_PASSWORD, true);
        
        return AdminResetPasswordResponse.builder()
                .tenantId(XssSanitizer.sanitize(user.getTenantId()))
                .userId(user.getId())
                .username(XssSanitizer.sanitize(user.getUsername()))
                .nickname(XssSanitizer.sanitize(user.getNickname()))
                .userType(user.getUserType())
                .newPassword(newPassword)
                .build();
    }

    /**
     * Generate a random password that meets the password policy requirements.
     * The generated password contains at least one uppercase letter, one lowercase letter,
     * one digit, and one special character, with a total length of 12 characters.
     *
     * @return A randomly generated password string
     */
    String generateRandomPassword() {
        char[] password = new char[GENERATED_PASSWORD_LENGTH];
        
        // Ensure at least one character from each required category
        password[0] = UPPER.charAt(SECURE_RANDOM.nextInt(UPPER.length()));
        password[1] = LOWER.charAt(SECURE_RANDOM.nextInt(LOWER.length()));
        password[2] = DIGITS.charAt(SECURE_RANDOM.nextInt(DIGITS.length()));
        password[3] = SPECIAL.charAt(SECURE_RANDOM.nextInt(SPECIAL.length()));
        
        // Fill remaining positions with random characters from all categories
        for (int i = 4; i < GENERATED_PASSWORD_LENGTH; i++) {
            password[i] = ALL_CHARS.charAt(SECURE_RANDOM.nextInt(ALL_CHARS.length()));
        }
        
        // Shuffle the password to avoid predictable pattern
        for (int i = GENERATED_PASSWORD_LENGTH - 1; i > 0; i--) {
            int j = SECURE_RANDOM.nextInt(i + 1);
            char temp = password[i];
            password[i] = password[j];
            password[j] = temp;
        }
        
        return new String(password);
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
     * List users for a specific tenant with pagination.
     * Only accessible by tenant admin users (userType = TENANT_ADMIN).
     * 
     * @param tenantId The tenant ID
     * @param requestingUserId The ID of the user making the request
     * @param page Page number (1-based, default 1)
     * @param size Page size (default 20, max 100)
     * @return List of tenant user items
     * @throws ApiException if the requesting user is not a tenant admin
     */
    public List<TenantUserItem> listTenantUsers(String tenantId, Long requestingUserId, int page, int size) {
        tenantService.requireEnabled(tenantId);
        
        // Check that the requesting user is a tenant admin
        QueryWrapper<User> adminWrapper = new QueryWrapper<>();
        adminWrapper.eq("tenant_id", tenantId).eq("id", requestingUserId);
        User requestingUser = userMapper.selectOne(adminWrapper);
        
        if (requestingUser == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        
        if (UserType.TENANT_ADMIN != requestingUser.getUserType()) {
            logger.warn("listTenantUsers forbidden: user {} is not TENANT_ADMIN in tenant {}", 
                requestingUserId, tenantId);
            throw new ApiException(ErrorCodes.FORBIDDEN_OPERATION, "only tenant admin can access user list");
        }
        
        // Sanitize pagination parameters
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        int offset = (safePage - 1) * safeSize;
        
        // Query users for this tenant with pagination
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).orderByAsc("id");
        wrapper.last("LIMIT " + safeSize + " OFFSET " + offset);
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

    /**
     * Search users within a tenant by username, phone, or email.
     * Only accessible by tenant admin users (userType = TENANT_ADMIN).
     * At least one search parameter must be provided.
     * 
     * @param tenantId The tenant ID
     * @param requestingUserId The ID of the user making the request
     * @param username Optional username to search for (exact match)
     * @param phone Optional phone to search for (exact match)
     * @param email Optional email to search for (exact match)
     * @return List of matching tenant user items
     * @throws ApiException if the requesting user is not a tenant admin or no search criteria provided
     */
    public List<TenantUserItem> searchTenantUsers(String tenantId, Long requestingUserId, 
            String username, String phone, String email) {
        tenantService.requireEnabled(tenantId);
        
        // Check that the requesting user is a tenant admin
        QueryWrapper<User> adminWrapper = new QueryWrapper<>();
        adminWrapper.eq("tenant_id", tenantId).eq("id", requestingUserId);
        User requestingUser = userMapper.selectOne(adminWrapper);
        
        if (requestingUser == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        
        if (UserType.TENANT_ADMIN != requestingUser.getUserType()) {
            logger.warn("searchTenantUsers forbidden: user {} is not TENANT_ADMIN in tenant {}", 
                requestingUserId, tenantId);
            throw new ApiException(ErrorCodes.FORBIDDEN_OPERATION, "only tenant admin can search users");
        }
        
        // At least one search parameter must be provided
        boolean hasUsername = username != null && !username.trim().isEmpty();
        boolean hasPhone = phone != null && !phone.trim().isEmpty();
        boolean hasEmail = email != null && !email.trim().isEmpty();
        
        if (!hasUsername && !hasPhone && !hasEmail) {
            throw new ApiException(ErrorCodes.VALIDATION_FAILED, 
                "at least one search parameter (username, phone, email) is required");
        }
        
        // Build OR query for matching users in this tenant
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId);
        wrapper.and(w -> {
            boolean first = true;
            if (hasUsername) {
                w.eq("username", username.trim());
                first = false;
            }
            if (hasPhone) {
                if (!first) {
                    w.or();
                }
                w.eq("phone", phone.trim());
                first = false;
            }
            if (hasEmail) {
                if (!first) {
                    w.or();
                }
                w.eq("email", email.trim());
            }
        });
        wrapper.orderByAsc("id");
        wrapper.last("LIMIT 100");
        
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
