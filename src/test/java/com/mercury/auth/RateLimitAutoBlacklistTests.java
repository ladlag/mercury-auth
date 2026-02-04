package com.mercury.auth;

import com.mercury.auth.config.RateLimitConfig;
import com.mercury.auth.service.BlacklistService;
import com.mercury.auth.service.RateLimitService;
import com.mercury.auth.util.IpUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RateLimitAutoBlacklistTests {

    private StringRedisTemplate redisTemplate;
    private RateLimitConfig rateLimitConfig;
    private BlacklistService blacklistService;
    private RateLimitService rateLimitService;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setup() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        blacklistService = Mockito.mock(BlacklistService.class);
        rateLimitConfig = new RateLimitConfig();
        
        // Configure auto-blacklist with lower thresholds for testing
        rateLimitConfig.getAutoBlacklist().setEnabled(true);
        rateLimitConfig.getAutoBlacklist().setFailureThreshold(5);
        rateLimitConfig.getAutoBlacklist().setFailureWindowMinutes(5);
        rateLimitConfig.getAutoBlacklist().setBlacklistDurationMinutes(30);
        rateLimitConfig.getAutoBlacklist().setSevereFailureThreshold(10);
        rateLimitConfig.getAutoBlacklist().setSevereFailureWindowMinutes(10);
        rateLimitConfig.getAutoBlacklist().setSevereBlacklistDurationMinutes(120);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        rateLimitService = new RateLimitService(redisTemplate, rateLimitConfig, blacklistService);
        
        // Setup mock HTTP request
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    @Test
    void recordFailedLoginAttempt_doesNotBlacklistBelowThreshold() {
        // Arrange
        String tenantId = "tenant1";
        String identifier = "user1";
        
        // Simulate 4 failures (below threshold of 5)
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
            .thenReturn(4L)  // failure count
            .thenReturn(4L); // severe count
        
        // Act
        rateLimitService.recordFailedLoginAttempt(tenantId, identifier);
        
        // Assert
        verify(blacklistService, never()).autoBlacklistIp(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void recordFailedLoginAttempt_triggersBlacklistAtThreshold() {
        // Arrange
        String tenantId = "tenant1";
        String identifier = "user1";
        
        // Simulate 5 failures (at threshold)
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
            .thenReturn(5L)  // failure count
            .thenReturn(5L); // severe count
        
        // Act
        rateLimitService.recordFailedLoginAttempt(tenantId, identifier);
        
        // Assert
        ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> durationCaptor = ArgumentCaptor.forClass(Integer.class);
        
        verify(blacklistService).autoBlacklistIp(
            ipCaptor.capture(), 
            tenantCaptor.capture(), 
            reasonCaptor.capture(), 
            durationCaptor.capture()
        );
        
        assertThat(ipCaptor.getValue()).isEqualTo("192.168.1.100");
        assertThat(tenantCaptor.getValue()).isEqualTo(tenantId);
        assertThat(reasonCaptor.getValue()).contains("5 failed login attempts");
        assertThat(durationCaptor.getValue()).isEqualTo(30); // Normal blacklist duration
    }

    @Test
    void recordFailedLoginAttempt_triggersSevereBlacklistAtHighThreshold() {
        // Arrange
        String tenantId = "tenant1";
        String identifier = "user1";
        
        // Simulate 10 failures (at severe threshold)
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
            .thenReturn(10L)  // failure count
            .thenReturn(10L); // severe count
        
        // Act
        rateLimitService.recordFailedLoginAttempt(tenantId, identifier);
        
        // Assert
        ArgumentCaptor<Integer> durationCaptor = ArgumentCaptor.forClass(Integer.class);
        
        verify(blacklistService).autoBlacklistIp(
            eq("192.168.1.100"), 
            eq(tenantId), 
            contains("10 failed login attempts"), 
            durationCaptor.capture()
        );
        
        assertThat(durationCaptor.getValue()).isEqualTo(120); // Severe blacklist duration
    }

    @Test
    void recordFailedLoginAttempt_clearsCounterAfterBlacklist() {
        // Arrange
        String tenantId = "tenant1";
        String identifier = "user1";
        
        // Simulate threshold reached
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
            .thenReturn(5L)  // failure count
            .thenReturn(5L); // severe count
        
        // Act
        rateLimitService.recordFailedLoginAttempt(tenantId, identifier);
        
        // Assert
        // Should delete the failure counter after blacklisting
        verify(redisTemplate, atLeastOnce()).delete(contains("login:failures:"));
    }

    @Test
    void recordFailedLoginAttempt_doesNothingWhenDisabled() {
        // Arrange
        rateLimitConfig.getAutoBlacklist().setEnabled(false);
        String tenantId = "tenant1";
        String identifier = "user1";
        
        // Act
        rateLimitService.recordFailedLoginAttempt(tenantId, identifier);
        
        // Assert
        verify(blacklistService, never()).autoBlacklistIp(anyString(), anyString(), anyString(), anyInt());
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    void recordFailedLoginAttempt_handlesNullIpGracefully() {
        // Arrange
        RequestContextHolder.resetRequestAttributes(); // Clear request context
        String tenantId = "tenant1";
        String identifier = "user1";
        
        // Act & Assert - should not throw exception
        assertThatCode(() -> rateLimitService.recordFailedLoginAttempt(tenantId, identifier))
            .doesNotThrowAnyException();
        
        verify(blacklistService, never()).autoBlacklistIp(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void recordFailedLoginAttempt_usesCorrectRedisKeys() {
        // Arrange
        String tenantId = "tenant1";
        String identifier = "user1";
        
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
            .thenReturn(4L); // Below threshold
        
        // Act
        rateLimitService.recordFailedLoginAttempt(tenantId, identifier);
        
        // Assert
        ArgumentCaptor<List> keyCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, times(2)).execute(
            any(RedisScript.class), 
            keyCaptor.capture(), 
            anyString()
        );
        
        List<List> allKeys = keyCaptor.getAllValues();
        assertThat(allKeys).hasSize(2);
        
        // First call should use normal failure key
        assertThat(allKeys.get(0).get(0).toString()).contains("login:failures:192.168.1.100");
        // Second call should use severe failure key
        assertThat(allKeys.get(1).get(0).toString()).contains("login:failures:severe:192.168.1.100");
    }
}
