package com.mercury.auth;

import com.mercury.auth.config.RateLimitConfig;
import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;

/**
 * Test for granular rate limiting configuration
 */
public class RateLimitConfigTest {

    private RateLimitConfig rateLimitConfig;
    private RateLimitService rateLimitService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setup() {
        rateLimitConfig = new RateLimitConfig();
        // Set up with default values
        rateLimitConfig.setMaxAttempts(10);
        rateLimitConfig.setWindowMinutes(1);
        
        // Send code - more restrictive
        RateLimitConfig.OperationRateLimit sendCodeLimit = new RateLimitConfig.OperationRateLimit();
        sendCodeLimit.setMaxAttempts(5);
        sendCodeLimit.setWindowMinutes(1);
        rateLimitConfig.setSendCode(sendCodeLimit);
        
        // Login - standard
        RateLimitConfig.OperationRateLimit loginLimit = new RateLimitConfig.OperationRateLimit();
        loginLimit.setMaxAttempts(10);
        loginLimit.setWindowMinutes(1);
        rateLimitConfig.setLogin(loginLimit);
        
        // Captcha - more permissive
        RateLimitConfig.OperationRateLimit captchaLimit = new RateLimitConfig.OperationRateLimit();
        captchaLimit.setMaxAttempts(20);
        captchaLimit.setWindowMinutes(1);
        rateLimitConfig.setCaptcha(captchaLimit);
        
        // Token refresh
        RateLimitConfig.OperationRateLimit refreshLimit = new RateLimitConfig.OperationRateLimit();
        refreshLimit.setMaxAttempts(10);
        refreshLimit.setWindowMinutes(1);
        rateLimitConfig.setRefreshToken(refreshLimit);
        
        // Mock Redis
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        
        rateLimitService = new RateLimitService(redisTemplate, rateLimitConfig);
    }

    @Test
    void testRateLimitConfigurationValues() {
        assertThat(rateLimitConfig.getMaxAttempts()).isEqualTo(10);
        assertThat(rateLimitConfig.getWindowMinutes()).isEqualTo(1);
        
        assertThat(rateLimitConfig.getSendCode().getMaxAttempts()).isEqualTo(5);
        assertThat(rateLimitConfig.getLogin().getMaxAttempts()).isEqualTo(10);
        assertThat(rateLimitConfig.getCaptcha().getMaxAttempts()).isEqualTo(20);
        assertThat(rateLimitConfig.getRefreshToken().getMaxAttempts()).isEqualTo(10);
    }
    
    @Test
    void testSendCodeRateLimitIsMoreRestrictive() {
        // Send code should have lower limit than login
        assertThat(rateLimitConfig.getSendCode().getMaxAttempts())
            .isLessThan(rateLimitConfig.getLogin().getMaxAttempts());
    }
    
    @Test
    void testCaptchaRateLimitIsMorePermissive() {
        // Captcha should have higher limit than login
        assertThat(rateLimitConfig.getCaptcha().getMaxAttempts())
            .isGreaterThan(rateLimitConfig.getLogin().getMaxAttempts());
    }
    
    @Test
    void testRateLimitServiceAcceptsRequestsUnderLimit() {
        // Mock Redis to return count under limit
        Mockito.when(redisTemplate.execute(
            any(RedisScript.class),
            anyList(),
            anyString()
        )).thenReturn(3L); // 3 requests, under the limit
        
        // Should not throw exception for send code (limit: 5)
        rateLimitService.check("rate:RATE_LIMIT_SEND_EMAIL_CODE:tenant1:user@example.com", 
                              AuthAction.RATE_LIMIT_SEND_EMAIL_CODE);
        
        // Should not throw exception for login (limit: 10)
        rateLimitService.check("rate:RATE_LIMIT_LOGIN_PASSWORD:tenant1:john_doe", 
                              AuthAction.RATE_LIMIT_LOGIN_PASSWORD);
        
        // Should not throw exception for captcha (limit: 20)
        rateLimitService.check("rate:CAPTCHA_LOGIN_PASSWORD:tenant1:john_doe", 
                              AuthAction.CAPTCHA_LOGIN_PASSWORD);
    }
    
    @Test
    void testDifferentActionsHaveDifferentLimits() {
        // Verify that different actions would be evaluated with different thresholds
        // Send code: 5 attempts
        // Login: 10 attempts
        // Captcha: 20 attempts
        
        assertThat(rateLimitConfig.getSendCode().getMaxAttempts()).isEqualTo(5);
        assertThat(rateLimitConfig.getLogin().getMaxAttempts()).isEqualTo(10);
        assertThat(rateLimitConfig.getCaptcha().getMaxAttempts()).isEqualTo(20);
        
        // Verify they're all different
        assertThat(rateLimitConfig.getSendCode().getMaxAttempts())
            .isNotEqualTo(rateLimitConfig.getCaptcha().getMaxAttempts());
    }
}
