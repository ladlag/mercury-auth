package com.mercury.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.entity.IpBlacklist;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.service.BlacklistService;
import com.mercury.auth.store.IpBlacklistMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BlacklistServiceTests {

    private IpBlacklistMapper ipBlacklistMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private BlacklistService blacklistService;

    @BeforeEach
    void setup() {
        ipBlacklistMapper = Mockito.mock(IpBlacklistMapper.class);
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        blacklistService = new BlacklistService(ipBlacklistMapper, redisTemplate);
    }

    @Test
    void addIpBlacklist_createsNewEntry() {
        // Arrange
        String ipAddress = "192.168.1.100";
        String tenantId = "tenant1";
        String reason = "Multiple failed login attempts";
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        String createdBy = "ADMIN";
        
        when(ipBlacklistMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(ipBlacklistMapper.insert(any(IpBlacklist.class))).thenReturn(1);
        
        // Act
        blacklistService.addIpBlacklist(ipAddress, tenantId, reason, expiresAt, createdBy);
        
        // Assert
        ArgumentCaptor<IpBlacklist> captor = ArgumentCaptor.forClass(IpBlacklist.class);
        verify(ipBlacklistMapper).insert(captor.capture());
        
        IpBlacklist captured = captor.getValue();
        assertThat(captured.getIpAddress()).isEqualTo(ipAddress);
        assertThat(captured.getTenantId()).isEqualTo(tenantId);
        assertThat(captured.getReason()).isEqualTo(reason);
        assertThat(captured.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(captured.getCreatedBy()).isEqualTo(createdBy);
        
        // Verify Redis cache is updated
        verify(valueOperations).set(contains(ipAddress), eq("1"), any(Duration.class));
    }

    @Test
    void addIpBlacklist_updatesExistingEntry() {
        // Arrange
        String ipAddress = "192.168.1.100";
        String tenantId = "tenant1";
        String reason = "Repeated violations";
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(2);
        String createdBy = "ADMIN";
        
        IpBlacklist existing = new IpBlacklist();
        existing.setId(1L);
        existing.setIpAddress(ipAddress);
        existing.setTenantId(tenantId);
        
        when(ipBlacklistMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        when(ipBlacklistMapper.updateById(any(IpBlacklist.class))).thenReturn(1);
        
        // Act
        blacklistService.addIpBlacklist(ipAddress, tenantId, reason, expiresAt, createdBy);
        
        // Assert
        verify(ipBlacklistMapper).updateById(existing);
        assertThat(existing.getReason()).isEqualTo(reason);
        assertThat(existing.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(existing.getCreatedBy()).isEqualTo(createdBy);
    }

    @Test
    void isIpBlacklisted_checksRedisFirst() {
        // Arrange
        String ipAddress = "192.168.1.100";
        String tenantId = "tenant1";
        
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        
        // Act
        boolean result = blacklistService.isIpBlacklisted(ipAddress, tenantId);
        
        // Assert
        assertThat(result).isTrue();
        verify(redisTemplate).hasKey(contains(ipAddress));
        verify(ipBlacklistMapper, never()).selectList(any(QueryWrapper.class));
    }

    @Test
    void isIpBlacklisted_checksDatabase_whenNotInRedis() {
        // Arrange
        String ipAddress = "192.168.1.100";
        String tenantId = "tenant1";
        
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        
        IpBlacklist entry = new IpBlacklist();
        entry.setIpAddress(ipAddress);
        entry.setTenantId(tenantId);
        entry.setExpiresAt(LocalDateTime.now().plusHours(1));
        
        when(ipBlacklistMapper.selectList(any(QueryWrapper.class)))
            .thenReturn(Collections.singletonList(entry));
        
        // Act
        boolean result = blacklistService.isIpBlacklisted(ipAddress, tenantId);
        
        // Assert
        assertThat(result).isTrue();
        verify(ipBlacklistMapper).selectList(any(QueryWrapper.class));
        // Should cache the result in Redis
        verify(valueOperations).set(anyString(), eq("1"), any(Duration.class));
    }

    @Test
    void isIpBlacklisted_returnsFalse_whenExpired() {
        // Arrange
        String ipAddress = "192.168.1.100";
        String tenantId = "tenant1";
        
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        
        IpBlacklist entry = new IpBlacklist();
        entry.setIpAddress(ipAddress);
        entry.setTenantId(tenantId);
        entry.setExpiresAt(LocalDateTime.now().minusHours(1)); // Expired
        
        when(ipBlacklistMapper.selectList(any(QueryWrapper.class)))
            .thenReturn(Collections.singletonList(entry));
        
        // Act
        boolean result = blacklistService.isIpBlacklisted(ipAddress, tenantId);
        
        // Assert
        assertThat(result).isFalse();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void isIpBlacklisted_supportsPermanentBlacklist() {
        // Arrange
        String ipAddress = "192.168.1.100";
        String tenantId = "tenant1";
        
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        
        IpBlacklist entry = new IpBlacklist();
        entry.setIpAddress(ipAddress);
        entry.setTenantId(tenantId);
        entry.setExpiresAt(null); // Permanent blacklist
        
        when(ipBlacklistMapper.selectList(any(QueryWrapper.class)))
            .thenReturn(Collections.singletonList(entry));
        
        // Act
        boolean result = blacklistService.isIpBlacklisted(ipAddress, tenantId);
        
        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void removeIpBlacklist_removesFromBothDatabaseAndRedis() {
        // Arrange
        String ipAddress = "192.168.1.100";
        String tenantId = "tenant1";
        
        when(ipBlacklistMapper.delete(any(QueryWrapper.class))).thenReturn(1);
        
        // Act
        blacklistService.removeIpBlacklist(ipAddress, tenantId);
        
        // Assert
        verify(ipBlacklistMapper).delete(any(QueryWrapper.class));
        verify(redisTemplate).delete(contains(ipAddress));
    }

    @Test
    void listIpBlacklist_returnsAllEntries() {
        // Arrange
        String tenantId = "tenant1";
        
        IpBlacklist entry1 = new IpBlacklist();
        entry1.setIpAddress("192.168.1.100");
        entry1.setTenantId(tenantId);
        
        IpBlacklist entry2 = new IpBlacklist();
        entry2.setIpAddress("192.168.1.101");
        entry2.setTenantId(tenantId);
        
        when(ipBlacklistMapper.selectList(any(QueryWrapper.class)))
            .thenReturn(Arrays.asList(entry1, entry2));
        
        // Act
        List<IpBlacklist> result = blacklistService.listIpBlacklist(tenantId);
        
        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(entry1, entry2);
    }

    @Test
    void cleanupExpiredIpBlacklist_removesExpiredEntries() {
        // Arrange
        when(ipBlacklistMapper.delete(any(QueryWrapper.class))).thenReturn(5);
        
        // Act
        int count = blacklistService.cleanupExpiredIpBlacklist();
        
        // Assert
        assertThat(count).isEqualTo(5);
        verify(ipBlacklistMapper).delete(any(QueryWrapper.class));
    }

    @Test
    void autoBlacklistIp_addsTemporaryBlacklist() {
        // Arrange
        String ipAddress = "192.168.1.100";
        String tenantId = "tenant1";
        String reason = "Multiple failed login attempts";
        int durationMinutes = 30;
        
        when(ipBlacklistMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(ipBlacklistMapper.insert(any(IpBlacklist.class))).thenReturn(1);
        
        // Act
        blacklistService.autoBlacklistIp(ipAddress, tenantId, reason, durationMinutes);
        
        // Assert
        ArgumentCaptor<IpBlacklist> captor = ArgumentCaptor.forClass(IpBlacklist.class);
        verify(ipBlacklistMapper).insert(captor.capture());
        
        IpBlacklist captured = captor.getValue();
        assertThat(captured.getIpAddress()).isEqualTo(ipAddress);
        assertThat(captured.getTenantId()).isEqualTo(tenantId);
        assertThat(captured.getReason()).contains("AUTO:");
        assertThat(captured.getReason()).contains(reason);
        assertThat(captured.getCreatedBy()).isEqualTo("SYSTEM");
        
        // Verify expiration time is approximately 30 minutes from now
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expected = now.plusMinutes(durationMinutes);
        assertThat(captured.getExpiresAt())
            .isAfter(expected.minusSeconds(5))
            .isBefore(expected.plusSeconds(5));
    }

    @Test
    void addIpBlacklist_supportsGlobalBlacklist() {
        // Arrange
        String ipAddress = "192.168.1.100";
        String tenantId = null; // Global blacklist
        String reason = "Known malicious IP";
        LocalDateTime expiresAt = null; // Permanent
        String createdBy = "ADMIN";
        
        when(ipBlacklistMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(ipBlacklistMapper.insert(any(IpBlacklist.class))).thenReturn(1);
        
        // Act
        blacklistService.addIpBlacklist(ipAddress, tenantId, reason, expiresAt, createdBy);
        
        // Assert
        ArgumentCaptor<IpBlacklist> captor = ArgumentCaptor.forClass(IpBlacklist.class);
        verify(ipBlacklistMapper).insert(captor.capture());
        
        IpBlacklist captured = captor.getValue();
        assertThat(captured.getIpAddress()).isEqualTo(ipAddress);
        assertThat(captured.getTenantId()).isNull(); // Global
        assertThat(captured.getExpiresAt()).isNull(); // Permanent
        
        // Verify Redis cache uses global prefix
        verify(valueOperations).set(contains("global"), eq("1"), any(Duration.class));
    }
}
