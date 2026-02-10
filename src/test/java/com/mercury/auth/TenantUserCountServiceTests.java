package com.mercury.auth;

import com.mercury.auth.entity.Tenant;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.service.TenantService;
import com.mercury.auth.service.TenantUserCountService;
import com.mercury.auth.store.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for TenantUserCountService to verify resilient counter behavior.
 */
public class TenantUserCountServiceTests {

    private TenantUserCountService tenantUserCountService;
    private StringRedisTemplate redisTemplate;
    private UserMapper userMapper;
    private TenantService tenantService;
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setup() throws Exception {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        userMapper = Mockito.mock(UserMapper.class);
        tenantService = Mockito.mock(TenantService.class);
        valueOps = Mockito.mock(ValueOperations.class);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        
        tenantUserCountService = new TenantUserCountService(
            redisTemplate, userMapper, tenantService
        );
        
        // Enable caching using reflection
        java.lang.reflect.Field cacheEnabledField = TenantUserCountService.class.getDeclaredField("cacheEnabled");
        cacheEnabledField.setAccessible(true);
        cacheEnabledField.set(tenantUserCountService, true);
        
        // Set cache TTL
        java.lang.reflect.Field cacheTtlField = TenantUserCountService.class.getDeclaredField("cacheTtlHours");
        cacheTtlField.setAccessible(true);
        cacheTtlField.set(tenantUserCountService, 24L);
        
        // Set validation threshold
        java.lang.reflect.Field validationField = TenantUserCountService.class.getDeclaredField("validationThresholdMinutes");
        validationField.setAccessible(true);
        validationField.set(tenantUserCountService, 60L);
    }

    @Test
    void checkMaxUsersLimit_allowsRegistration_whenUnderLimit() {
        // Given: Tenant with max 10 users
        Tenant tenant = new Tenant();
        tenant.setTenantId("t1");
        tenant.setMaxUsers(10);
        when(tenantService.getById("t1")).thenReturn(tenant);
        
        // Redis cache hit with count = 5
        when(valueOps.get("tenant:users:count:t1")).thenReturn("5");
        when(valueOps.get("tenant:users:sync:t1")).thenReturn(String.valueOf(System.currentTimeMillis()));
        
        // When: Check limit
        tenantUserCountService.checkMaxUsersLimit("t1");
        
        // Then: No exception thrown
    }

    @Test
    void checkMaxUsersLimit_rejectsRegistration_whenAtLimit() {
        // Given: Tenant with max 10 users, currently at 10
        Tenant tenant = new Tenant();
        tenant.setTenantId("t1");
        tenant.setMaxUsers(10);
        when(tenantService.getById("t1")).thenReturn(tenant);
        
        // Redis cache hit with count = 10
        when(valueOps.get("tenant:users:count:t1")).thenReturn("10");
        when(valueOps.get("tenant:users:sync:t1")).thenReturn(String.valueOf(System.currentTimeMillis()));
        
        // When/Then: Check limit throws exception
        assertThatThrownBy(() -> tenantUserCountService.checkMaxUsersLimit("t1"))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("code", ErrorCodes.TENANT_MAX_USERS_REACHED);
    }

    @Test
    void checkMaxUsersLimit_allowsRegistration_whenMaxUsersNull() {
        // Given: Tenant with unlimited users (max_users = null)
        Tenant tenant = new Tenant();
        tenant.setTenantId("t1");
        tenant.setMaxUsers(null);
        when(tenantService.getById("t1")).thenReturn(tenant);
        
        // When: Check limit
        tenantUserCountService.checkMaxUsersLimit("t1");
        
        // Then: No exception thrown, no Redis access needed
        verify(valueOps, never()).get(anyString());
    }

    @Test
    void getUserCount_initializesFromDatabase_onCacheMiss() {
        // Given: Redis cache miss
        when(valueOps.get("tenant:users:count:t1")).thenReturn(null);
        
        // Database has 5 users
        when(userMapper.selectCount(any())).thenReturn(5L);
        
        // When: Get count
        long count = tenantUserCountService.getUserCount("t1");
        
        // Then: Returns count from database and syncs to Redis
        assertThat(count).isEqualTo(5);
        verify(userMapper).selectCount(any());
        verify(valueOps).set(eq("tenant:users:count:t1"), eq("5"), anyLong(), any());
        verify(valueOps).set(eq("tenant:users:sync:t1"), anyString(), anyLong(), any());
    }

    @Test
    void getUserCount_fallsBackToDatabase_onRedisFailure() {
        // Given: Redis throws exception
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("Redis down"));
        
        // Database has 7 users
        when(userMapper.selectCount(any())).thenReturn(7L);
        
        // When: Get count
        long count = tenantUserCountService.getUserCount("t1");
        
        // Then: Returns count from database
        assertThat(count).isEqualTo(7);
        verify(userMapper).selectCount(any());
    }

    @Test
    void getUserCount_resyncFromDatabase_whenCounterStale() throws InterruptedException {
        // Given: Redis has stale counter (older than validation threshold)
        long oldTime = System.currentTimeMillis() - (70 * 60 * 1000); // 70 minutes ago
        when(valueOps.get("tenant:users:count:t1")).thenReturn("5");
        when(valueOps.get("tenant:users:sync:t1")).thenReturn(String.valueOf(oldTime));
        
        // Database now has 8 users (some drift occurred)
        when(userMapper.selectCount(any())).thenReturn(8L);
        
        // When: Get count
        long count = tenantUserCountService.getUserCount("t1");
        
        // Then: Re-syncs from database
        assertThat(count).isEqualTo(8);
        verify(userMapper).selectCount(any());
        verify(valueOps).set(eq("tenant:users:count:t1"), eq("8"), anyLong(), any());
    }

    @Test
    void incrementUserCount_updatesRedis_successfully() {
        // Given: Redis is available
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString())).thenReturn(6L);
        
        // When: Increment count
        tenantUserCountService.incrementUserCount("t1");
        
        // Then: Redis script executed
        verify(redisTemplate).execute(any(), anyList(), anyString(), anyString());
    }

    @Test
    void incrementUserCount_doesNotFail_onRedisError() {
        // Given: Redis throws exception
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString()))
            .thenThrow(new RedisConnectionFailureException("Redis down"));
        
        // When: Increment count (should not throw)
        tenantUserCountService.incrementUserCount("t1");
        
        // Then: No exception thrown, graceful degradation
        // Next read will sync from database
    }

    @Test
    void decrementUserCount_updatesRedis_successfully() {
        // Given: Redis is available
        when(valueOps.decrement("tenant:users:count:t1")).thenReturn(4L);
        
        // When: Decrement count
        tenantUserCountService.decrementUserCount("t1");
        
        // Then: Redis decrement called
        verify(valueOps).decrement("tenant:users:count:t1");
        verify(valueOps).set(eq("tenant:users:sync:t1"), anyString(), anyLong(), any());
    }

    @Test
    void syncUserCountFromDatabase_updatesRedisWithActualCount() {
        // Given: Database has 12 users
        when(userMapper.selectCount(any())).thenReturn(12L);
        
        // When: Sync from database
        long count = tenantUserCountService.syncUserCountFromDatabase("t1");
        
        // Then: Returns actual count and updates Redis
        assertThat(count).isEqualTo(12);
        verify(valueOps).set(eq("tenant:users:count:t1"), eq("12"), anyLong(), any());
        verify(valueOps).set(eq("tenant:users:sync:t1"), anyString(), anyLong(), any());
    }

    @Test
    void invalidateCount_clearsRedisCache() {
        // When: Invalidate count
        tenantUserCountService.invalidateCount("t1");
        
        // Then: Both keys deleted from Redis
        verify(redisTemplate).delete("tenant:users:count:t1");
        verify(redisTemplate).delete("tenant:users:sync:t1");
    }
}
