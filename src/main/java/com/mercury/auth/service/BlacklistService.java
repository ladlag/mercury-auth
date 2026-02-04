package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.entity.IpBlacklist;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.store.IpBlacklistMapper;
import com.mercury.auth.util.IpUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Unified blacklist management service supporting multiple dimensions:
 * - IP blacklist (global or tenant-specific)
 * - Token blacklist (handled by TokenService)
 * 
 * Blacklist trigger conditions:
 * 1. Token blacklist: logout, token refresh, suspicious activity detection
 * 2. IP blacklist: repeated failed login attempts, rate limit violations, security threats
 * 3. Admin manual blacklist: security policy enforcement
 */
@Service
@RequiredArgsConstructor
public class BlacklistService {
    
    private static final Logger logger = LoggerFactory.getLogger(BlacklistService.class);
    private final IpBlacklistMapper ipBlacklistMapper;
    private final StringRedisTemplate redisTemplate;
    
    // Redis key prefixes
    private static final String IP_BLACKLIST_PREFIX = "blacklist:ip:";
    private static final String IP_BLACKLIST_GLOBAL_PREFIX = "blacklist:ip:global:";
    private static final String IP_BLACKLIST_TENANT_PREFIX = "blacklist:ip:tenant:";
    
    /**
     * Check if current request IP is blacklisted
     * Checks both global and tenant-specific blacklists
     * 
     * @param tenantId tenant ID to check tenant-specific blacklist, can be null
     * @throws ApiException if IP is blacklisted
     */
    public void checkIpBlacklist(String tenantId) {
        String clientIp = getCurrentRequestIp();
        if (clientIp == null) {
            return; // Unable to determine IP, skip check
        }
        
        // Check global IP blacklist first
        if (isIpBlacklisted(clientIp, null)) {
            logger.warn("Request blocked - IP is globally blacklisted: ip={}", clientIp);
            throw new ApiException(ErrorCodes.IP_BLACKLISTED, "IP address is blacklisted");
        }
        
        // Check tenant-specific IP blacklist
        if (tenantId != null && isIpBlacklisted(clientIp, tenantId)) {
            logger.warn("Request blocked - IP is blacklisted for tenant: ip={} tenant={}", clientIp, tenantId);
            throw new ApiException(ErrorCodes.IP_BLACKLISTED, "IP address is blacklisted for this tenant");
        }
    }
    
    /**
     * Check if an IP is blacklisted (checks both Redis cache and database)
     * 
     * @param ipAddress IP address to check
     * @param tenantId tenant ID (null for global blacklist)
     * @return true if IP is blacklisted
     */
    public boolean isIpBlacklisted(String ipAddress, String tenantId) {
        // Check Redis cache first for performance
        String redisKey = buildIpBlacklistKey(ipAddress, tenantId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            return true;
        }
        
        // Check database if not in cache
        QueryWrapper<IpBlacklist> wrapper = new QueryWrapper<>();
        wrapper.eq("ip_address", ipAddress);
        if (tenantId == null) {
            wrapper.isNull("tenant_id");
        } else {
            wrapper.eq("tenant_id", tenantId);
        }
        
        // Check if blacklist entry exists and is not expired
        List<IpBlacklist> entries = ipBlacklistMapper.selectList(wrapper);
        for (IpBlacklist entry : entries) {
            if (entry.getExpiresAt() == null || entry.getExpiresAt().isAfter(LocalDateTime.now())) {
                // Entry is valid, cache it in Redis
                cacheIpBlacklist(ipAddress, tenantId, entry.getExpiresAt());
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Add IP to blacklist
     * 
     * @param ipAddress IP address to blacklist
     * @param tenantId tenant ID (null for global blacklist)
     * @param reason reason for blacklisting
     * @param expiresAt expiration time (null for permanent)
     * @param createdBy who created the blacklist entry
     */
    public void addIpBlacklist(String ipAddress, String tenantId, String reason, 
                               LocalDateTime expiresAt, String createdBy) {
        // Check if already exists
        QueryWrapper<IpBlacklist> wrapper = new QueryWrapper<>();
        wrapper.eq("ip_address", ipAddress);
        if (tenantId == null) {
            wrapper.isNull("tenant_id");
        } else {
            wrapper.eq("tenant_id", tenantId);
        }
        
        IpBlacklist existing = ipBlacklistMapper.selectOne(wrapper);
        if (existing != null) {
            // Update existing entry
            existing.setReason(reason);
            existing.setExpiresAt(expiresAt);
            existing.setCreatedBy(createdBy);
            ipBlacklistMapper.updateById(existing);
            logger.info("Updated IP blacklist: ip={} tenant={} reason={}", ipAddress, tenantId, reason);
        } else {
            // Create new entry
            IpBlacklist entry = new IpBlacklist();
            entry.setIpAddress(ipAddress);
            entry.setTenantId(tenantId);
            entry.setReason(reason);
            entry.setExpiresAt(expiresAt);
            entry.setCreatedAt(LocalDateTime.now());
            entry.setCreatedBy(createdBy);
            ipBlacklistMapper.insert(entry);
            logger.info("Added IP to blacklist: ip={} tenant={} reason={}", ipAddress, tenantId, reason);
        }
        
        // Cache in Redis
        cacheIpBlacklist(ipAddress, tenantId, expiresAt);
    }
    
    /**
     * Remove IP from blacklist
     * 
     * @param ipAddress IP address to remove
     * @param tenantId tenant ID (null for global blacklist)
     */
    public void removeIpBlacklist(String ipAddress, String tenantId) {
        QueryWrapper<IpBlacklist> wrapper = new QueryWrapper<>();
        wrapper.eq("ip_address", ipAddress);
        if (tenantId == null) {
            wrapper.isNull("tenant_id");
        } else {
            wrapper.eq("tenant_id", tenantId);
        }
        
        ipBlacklistMapper.delete(wrapper);
        
        // Remove from Redis cache
        String redisKey = buildIpBlacklistKey(ipAddress, tenantId);
        redisTemplate.delete(redisKey);
        
        logger.info("Removed IP from blacklist: ip={} tenant={}", ipAddress, tenantId);
    }
    
    /**
     * List all IP blacklist entries for a tenant
     * 
     * @param tenantId tenant ID (null for global blacklist)
     * @return list of IP blacklist entries
     */
    public List<IpBlacklist> listIpBlacklist(String tenantId) {
        QueryWrapper<IpBlacklist> wrapper = new QueryWrapper<>();
        if (tenantId == null) {
            wrapper.isNull("tenant_id");
        } else {
            wrapper.eq("tenant_id", tenantId);
        }
        wrapper.orderByDesc("created_at");
        
        return ipBlacklistMapper.selectList(wrapper);
    }
    
    /**
     * Clean up expired IP blacklist entries from database
     * Should be called periodically (e.g., by scheduled task)
     * 
     * @return number of entries cleaned up
     */
    public int cleanupExpiredIpBlacklist() {
        QueryWrapper<IpBlacklist> wrapper = new QueryWrapper<>();
        wrapper.isNotNull("expires_at");
        wrapper.lt("expires_at", LocalDateTime.now());
        
        int count = ipBlacklistMapper.delete(wrapper);
        if (count > 0) {
            logger.info("Cleaned up {} expired IP blacklist entries", count);
        }
        return count;
    }
    
    /**
     * Automatically blacklist IP based on security violations
     * This is triggered by:
     * - Multiple failed login attempts
     * - Rate limit violations
     * - Suspicious activity patterns
     * 
     * @param ipAddress IP address to blacklist
     * @param tenantId tenant ID
     * @param reason reason for auto-blacklist
     * @param durationMinutes how long to blacklist (in minutes)
     */
    public void autoBlacklistIp(String ipAddress, String tenantId, String reason, int durationMinutes) {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(durationMinutes);
        addIpBlacklist(ipAddress, tenantId, "AUTO: " + reason, expiresAt, "SYSTEM");
        logger.warn("Auto-blacklisted IP: ip={} tenant={} reason={} duration={}min", 
                    ipAddress, tenantId, reason, durationMinutes);
    }
    
    // Private helper methods
    
    private String buildIpBlacklistKey(String ipAddress, String tenantId) {
        if (tenantId == null) {
            return IP_BLACKLIST_GLOBAL_PREFIX + ipAddress;
        } else {
            return IP_BLACKLIST_TENANT_PREFIX + tenantId + ":" + ipAddress;
        }
    }
    
    private void cacheIpBlacklist(String ipAddress, String tenantId, LocalDateTime expiresAt) {
        String redisKey = buildIpBlacklistKey(ipAddress, tenantId);
        
        if (expiresAt == null) {
            // Permanent blacklist - cache for 1 year
            redisTemplate.opsForValue().set(redisKey, "1", Duration.ofDays(365));
        } else {
            Duration ttl = Duration.between(LocalDateTime.now(), expiresAt);
            if (!ttl.isNegative() && !ttl.isZero()) {
                redisTemplate.opsForValue().set(redisKey, "1", ttl);
            }
        }
    }
    
    private String getCurrentRequestIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            
            HttpServletRequest request = attributes.getRequest();
            String clientIp = IpUtils.getClientIp(request);
            
            if ("unknown".equals(clientIp)) {
                return null;
            }
            
            return clientIp;
        } catch (Exception e) {
            logger.warn("Failed to get client IP: {}", e.getMessage());
            return null;
        }
    }
}
