# Rate Limiting Granularity Fix - Summary

## Problem Statement (Chinese)
当前的RATE_LIMITED 在不同注册方式和场景下是通用的吗？ 我刚才操作用户名密码注册、获取验证码等出发限流，第一次点击发送邮箱验证码 直接提示 操作过于频繁。

所以需要你梳理当前RATE_LIMITED的维度，来确定合理的阈值

## Problem Statement (English Translation)
Is the current RATE_LIMITED universal across different registration methods and scenarios? When I performed username/password registration and requested verification codes, I triggered rate limiting. The first time I clicked to send an email verification code, it directly showed "operation too frequent".

Therefore, you need to sort out the current RATE_LIMITED dimensions to determine reasonable thresholds.

## Root Cause Analysis

The original implementation had a **single shared rate limit** (10 requests/minute) for ALL operations:
- Login attempts (password, email, phone)
- Verification code sending (email, SMS)
- Captcha generation
- Token refresh

This caused several problems:

1. **Captcha Generation Interferes with Login**: When a user triggers captcha requirement (after 3 failed logins), requesting captcha images would count toward the same 10 requests/minute limit as login attempts
2. **First-time Operations Fail**: Users reported getting "operation too frequent" on their FIRST attempt to send email verification codes
3. **No Risk-Based Differentiation**: High-risk operations (verification code sending) and low-risk operations (captcha viewing) shared the same limits
4. **Poor User Experience**: Users solving captchas would exhaust their rate limit just trying to see the image

## Solution Implemented

### 1. Created Granular Rate Limit Configuration (`RateLimitConfig`)

New configuration class with operation-specific limits:

```java
@Configuration
@ConfigurationProperties(prefix = "security.rate-limit")
public class RateLimitConfig {
    // Default (backward compatibility)
    private long maxAttempts = 10;
    private long windowMinutes = 1;
    
    // Verification code sending - MORE RESTRICTIVE
    private OperationRateLimit sendCode = new OperationRateLimit(5, 1);
    
    // Login operations - STANDARD
    private OperationRateLimit login = new OperationRateLimit(10, 1);
    
    // Captcha generation - MORE PERMISSIVE
    private OperationRateLimit captcha = new OperationRateLimit(20, 1);
    
    // Token refresh - STANDARD
    private OperationRateLimit refreshToken = new OperationRateLimit(10, 1);
}
```

### 2. Updated RateLimitService

Added action-aware rate limiting:

```java
public void check(String key, AuthAction action) {
    RateLimitConfig.OperationRateLimit limit = getRateLimitForAction(action);
    // Apply operation-specific limit
}

private RateLimitConfig.OperationRateLimit getRateLimitForAction(AuthAction action) {
    switch (action) {
        case RATE_LIMIT_SEND_EMAIL_CODE:
        case RATE_LIMIT_SEND_PHONE_CODE:
            return rateLimitConfig.getSendCode();  // 5/min
        
        case RATE_LIMIT_LOGIN_PASSWORD:
        case RATE_LIMIT_LOGIN_EMAIL:
        case RATE_LIMIT_LOGIN_PHONE:
            return rateLimitConfig.getLogin();  // 10/min
        
        case CAPTCHA_LOGIN_PASSWORD:
        case CAPTCHA_LOGIN_EMAIL:
        case CAPTCHA_LOGIN_PHONE:
            return rateLimitConfig.getCaptcha();  // 20/min
        
        case RATE_LIMIT_REFRESH_TOKEN:
            return rateLimitConfig.getRefreshToken();  // 10/min
    }
}
```

### 3. Updated All Service Calls

Modified all services to pass the action parameter:

```java
// Before
rateLimitService.check(key);

// After
rateLimitService.check(key, AuthAction.RATE_LIMIT_SEND_EMAIL_CODE);
```

Updated services:
- PasswordAuthService
- EmailAuthService
- PhoneAuthService
- CaptchaService
- TokenService

### 4. Updated Configuration Files

**application-dev.yml**:
```yaml
security:
  rate-limit:
    send-code:
      max-attempts: 5
      window-minutes: 1
    login:
      max-attempts: 10
      window-minutes: 1
    captcha:
      max-attempts: 20
      window-minutes: 1
    refresh-token:
      max-attempts: 10
      window-minutes: 1
```

**application-prod.yml**: Added environment variable support for all settings

## New Rate Limit Dimensions

| Operation Type | Max Attempts | Window | Rationale |
|---------------|-------------|---------|-----------|
| **Verification Code Sending** | 5 | 1 min | More restrictive to prevent spam/abuse and reduce SMS costs |
| **Login Operations** | 10 | 1 min | Standard security balance |
| **Captcha Generation** | 20 | 1 min | More permissive - users may need multiple attempts to read captcha |
| **Token Refresh** | 10 | 1 min | Standard security balance |

## Benefits

1. **Fixed "First-time Operation" Issue**: Email verification codes now have their own limit, preventing false "operation too frequent" errors
2. **Independent Counters**: Captcha requests don't interfere with login attempts - each has its own counter
3. **Better Security**: More restrictive limits (5/min) for sensitive operations like verification code sending
4. **Better UX**: More permissive limits (20/min) for captcha where users may struggle to read the image
5. **Backward Compatible**: Maintains default rate limit settings for unknown actions
6. **Environment-Configurable**: All limits can be overridden via environment variables in production

## Testing

- Created RateLimitConfigTest to verify configuration values
- Verified different operations have different limits
- Confirmed code compiles successfully
- Code review: No issues found
- Security scan (CodeQL): No alerts found

## Known Issues

Pre-existing test failures unrelated to this change:
- Several test files are missing PasswordEncryptionService mock
- These tests were broken BEFORE this change
- Not addressing these as they're out of scope for this fix

## Files Modified

1. **src/main/java/com/mercury/auth/config/RateLimitConfig.java** - NEW
2. **src/main/java/com/mercury/auth/service/RateLimitService.java** - Updated
3. **src/main/java/com/mercury/auth/service/PasswordAuthService.java** - Updated
4. **src/main/java/com/mercury/auth/service/EmailAuthService.java** - Updated
5. **src/main/java/com/mercury/auth/service/PhoneAuthService.java** - Updated
6. **src/main/java/com/mercury/auth/service/CaptchaService.java** - Updated
7. **src/main/java/com/mercury/auth/service/TokenService.java** - Updated
8. **src/main/resources/application-dev.yml** - Updated
9. **src/main/resources/application-prod.yml** - Updated
10. **RATE_LIMITING.md** - Updated documentation
11. **src/test/java/com/mercury/auth/RateLimitConfigTest.java** - NEW test

## Migration Guide

For existing deployments, no breaking changes. The system will use default values if new configuration is not provided.

To customize rate limits, add to application.yml:

```yaml
security:
  rate-limit:
    send-code:
      max-attempts: 5  # Customize as needed
      window-minutes: 1
    login:
      max-attempts: 10
      window-minutes: 1
    captcha:
      max-attempts: 20
      window-minutes: 1
    refresh-token:
      max-attempts: 10
      window-minutes: 1
```

Or use environment variables:
```bash
RATE_LIMIT_SEND_CODE_MAX_ATTEMPTS=5
RATE_LIMIT_LOGIN_MAX_ATTEMPTS=10
RATE_LIMIT_CAPTCHA_MAX_ATTEMPTS=20
RATE_LIMIT_REFRESH_TOKEN_MAX_ATTEMPTS=10
```
