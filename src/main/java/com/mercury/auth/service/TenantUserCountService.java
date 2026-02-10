package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.store.UserMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing tenant user counts with Redis caching and auto-recovery.
 * 
 * This service provides a resilient counter mechanism that:
 * 1. Uses Redis for fast lookups (performance)
 * 2. Auto-initializes from database on cache miss
 * 3. Falls back to database on Redis failures (reliability)
 * 4. Validates and re-syncs stale counters (accuracy)
 * 
 * Design Philosophy:
 * - Performance first, but never sacrifice correctness
 * - Fail-safe: Redis unavailable → use database
 * - Self-healing: Detect and recover from drift
 */
@Service
@RequiredArgsConstructor
public class TenantUserCountService {

    private static final Logger logger = LoggerFactory.getLogger(TenantUserCountService.class);
    
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final TenantService tenantService;
    
    // Redis key patterns
    private static final String COUNT_KEY_PREFIX = "tenant:users:count:";
    private static final String SYNC_TIME_KEY_PREFIX = "tenant:users:sync:";
    
    // Configuration
    @Value("${security.user-count-cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${security.user-count-cache.ttl-hours:24}")
    private long cacheTtlHours;
    
    @Value("${security.user-count-cache.validation-threshold-minutes:60}")
    private long validationThresholdMinutes;
    
    // Lua script for atomic increment with bounds check
    private static final String INCREMENT_SCRIPT = 
        "local count = redis.call('incr', KEYS[1]) " +
        "redis.call('expire', KEYS[1], ARGV[1]) " +
        "redis.call('set', KEYS[2], ARGV[2]) " +
        "redis.call('expire', KEYS[2], ARGV[1]) " +
        "return count";
    
    private static final RedisScript<Long> INCREMENT_REDIS_SCRIPT =
        RedisScript.of(INCREMENT_SCRIPT, Long.class);
    
    /**
     * Check if tenant has reached maximum users limit.
     * Uses cached count with automatic fallback and recovery.
     * 
     * CRITICAL: When limit appears reached, verify with database to prevent
     * false rejections due to counter drift or bugs.
     * 
     * @param tenantId The tenant ID to check
     * @throws ApiException if max users limit is ACTUALLY reached (verified from database)
     */
    public void checkMaxUsersLimit(String tenantId) {
        // Get tenant configuration (already cached by TenantService)
        com.mercury.auth.entity.Tenant tenant = tenantService.getById(tenantId);
        
        // If max_users is null, unlimited users are allowed
        if (tenant.getMaxUsers() == null) {
            return;
        }
        
        // Get current user count (with caching and fallback)
        long currentUserCount = getUserCount(tenantId);
        
        // Check if limit is reached
        if (currentUserCount >= tenant.getMaxUsers()) {
            // CRITICAL: Verify with database before rejecting
            // This prevents false rejections due to:
            // 1. Counter drift (Redis count > actual count)
            // 2. Redis bugs or corruption
            // 3. Race conditions during recovery
            logger.info("Cached count suggests limit reached for tenant={}, verifying with database", 
                tenantId);
            
            long actualCount = countUsersFromDatabase(tenantId);
            
            if (actualCount >= tenant.getMaxUsers()) {
                // Actually at limit - reject registration
                logger.warn("Max users limit CONFIRMED for tenant={} actual={} max={}", 
                    tenantId, actualCount, tenant.getMaxUsers());
                throw new ApiException(ErrorCodes.TENANT_MAX_USERS_REACHED, 
                    "tenant has reached maximum users limit");
            } else {
                // False alarm - counter was wrong, sync and allow registration
                logger.warn("False limit alarm for tenant={}: cached={} actual={} max={}. Syncing counter.", 
                    tenantId, currentUserCount, actualCount, tenant.getMaxUsers());
                syncUserCountFromDatabase(tenantId);
                // Continue - registration is allowed
            }
        }
    }
    
    /**
     * Get current user count for a tenant.
     * Multi-layer strategy:
     * 1. Try Redis cache (fast path)
     * 2. If cache miss or stale, sync from database
     * 3. If Redis fails, fall back to direct database query
     * 
     * @param tenantId The tenant ID
     * @return Current user count
     */
    public long getUserCount(String tenantId) {
        if (!cacheEnabled) {
            return countUsersFromDatabase(tenantId);
        }
        
        try {
            String countKey = getCountKey(tenantId);
            String syncTimeKey = getSyncTimeKey(tenantId);
            
            // Try to get from Redis
            String countStr = redisTemplate.opsForValue().get(countKey);
            String syncTimeStr = redisTemplate.opsForValue().get(syncTimeKey);
            
            if (countStr != null && syncTimeStr != null) {
                // Check if counter is stale
                long syncTime = Long.parseLong(syncTimeStr);
                long currentTime = System.currentTimeMillis();
                long ageMinutes = (currentTime - syncTime) / (60 * 1000);
                
                if (ageMinutes < validationThresholdMinutes) {
                    // Counter is fresh, use it
                    return Long.parseLong(countStr);
                } else {
                    // Counter is stale, re-sync from database
                    logger.info("Counter stale for tenant={}, age={}min, re-syncing from database", 
                        tenantId, ageMinutes);
                    return syncUserCountFromDatabase(tenantId);
                }
            } else {
                // Cache miss, initialize from database
                logger.debug("Cache miss for tenant={}, initializing from database", tenantId);
                return syncUserCountFromDatabase(tenantId);
            }
            
        } catch (RedisConnectionFailureException e) {
            // Redis is unavailable, fall back to database
            logger.warn("Redis unavailable for tenant={}, falling back to database query: {}", 
                tenantId, e.getMessage());
            return countUsersFromDatabase(tenantId);
        } catch (Exception e) {
            // Any other error, fall back to database for safety
            logger.error("Error accessing Redis counter for tenant={}, falling back to database: {}", 
                tenantId, e.getMessage(), e);
            return countUsersFromDatabase(tenantId);
        }
    }
    
    /**
     * Increment user count after successful user creation.
     * Safe to call even if Redis is unavailable - next read will sync from DB.
     * 
     * @param tenantId The tenant ID
     */
    public void incrementUserCount(String tenantId) {
        if (!cacheEnabled) {
            return;
        }
        
        try {
            String countKey = getCountKey(tenantId);
            String syncTimeKey = getSyncTimeKey(tenantId);
            long ttlSeconds = cacheTtlHours * 3600;
            String currentTime = String.valueOf(System.currentTimeMillis());
            
            // Atomic increment with timestamp update
            redisTemplate.execute(
                INCREMENT_REDIS_SCRIPT,
                java.util.Arrays.asList(countKey, syncTimeKey),
                String.valueOf(ttlSeconds),
                currentTime
            );
            
            logger.debug("Incremented user count for tenant={}", tenantId);
            
        } catch (Exception e) {
            // Don't fail the registration if Redis is down
            // Next read will sync from database
            logger.warn("Failed to increment Redis counter for tenant={}: {}", 
                tenantId, e.getMessage());
        }
    }
    
    /**
     * Decrement user count after user deletion.
     * Safe to call even if Redis is unavailable - next read will sync from DB.
     * 
     * @param tenantId The tenant ID
     */
    public void decrementUserCount(String tenantId) {
        if (!cacheEnabled) {
            return;
        }
        
        try {
            String countKey = getCountKey(tenantId);
            String syncTimeKey = getSyncTimeKey(tenantId);
            long ttlSeconds = cacheTtlHours * 3600;
            
            Long newCount = redisTemplate.opsForValue().decrement(countKey);
            
            // Update sync timestamp
            redisTemplate.opsForValue().set(
                syncTimeKey, 
                String.valueOf(System.currentTimeMillis()),
                ttlSeconds,
                TimeUnit.SECONDS
            );
            
            // Set expiry on count key
            redisTemplate.expire(countKey, ttlSeconds, TimeUnit.SECONDS);
            
            logger.debug("Decremented user count for tenant={}, new count={}", tenantId, newCount);
            
        } catch (Exception e) {
            // Don't fail the deletion if Redis is down
            logger.warn("Failed to decrement Redis counter for tenant={}: {}", 
                tenantId, e.getMessage());
        }
    }
    
    /**
     * Synchronize user count from database to Redis.
     * This is called on cache miss or when counter is stale.
     * 
     * @param tenantId The tenant ID
     * @return Current user count from database
     */
    public long syncUserCountFromDatabase(String tenantId) {
        long actualCount = countUsersFromDatabase(tenantId);
        
        if (!cacheEnabled) {
            return actualCount;
        }
        
        try {
            String countKey = getCountKey(tenantId);
            String syncTimeKey = getSyncTimeKey(tenantId);
            long ttlSeconds = cacheTtlHours * 3600;
            
            // Update Redis with actual count
            redisTemplate.opsForValue().set(
                countKey, 
                String.valueOf(actualCount),
                ttlSeconds,
                TimeUnit.SECONDS
            );
            
            // Update sync timestamp
            redisTemplate.opsForValue().set(
                syncTimeKey,
                String.valueOf(System.currentTimeMillis()),
                ttlSeconds,
                TimeUnit.SECONDS
            );
            
            logger.info("Synced user count for tenant={} from database, count={}", 
                tenantId, actualCount);
            
        } catch (Exception e) {
            // Sync failure is not critical - we still return the correct count
            logger.warn("Failed to sync counter to Redis for tenant={}: {}", 
                tenantId, e.getMessage());
        }
        
        return actualCount;
    }
    
    /**
     * Count users from database.
     * This is the source of truth for user counts.
     * 
     * @param tenantId The tenant ID
     * @return Actual user count from database
     */
    private long countUsersFromDatabase(String tenantId) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("tenant_id", tenantId);
        return userMapper.selectCount(qw);
    }
    
    /**
     * Invalidate cached count for a tenant.
     * Use this when you want to force a fresh count from database on next access.
     * 
     * @param tenantId The tenant ID
     */
    public void invalidateCount(String tenantId) {
        if (!cacheEnabled) {
            return;
        }
        
        try {
            redisTemplate.delete(getCountKey(tenantId));
            redisTemplate.delete(getSyncTimeKey(tenantId));
            logger.debug("Invalidated user count cache for tenant={}", tenantId);
        } catch (Exception e) {
            logger.warn("Failed to invalidate count cache for tenant={}: {}", 
                tenantId, e.getMessage());
        }
    }
    
    private String getCountKey(String tenantId) {
        return COUNT_KEY_PREFIX + tenantId;
    }
    
    private String getSyncTimeKey(String tenantId) {
        return SYNC_TIME_KEY_PREFIX + tenantId;
    }
}
