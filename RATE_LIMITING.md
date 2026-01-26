# Rate Limiting Guide

## Overview

This service implements **granular rate limiting** to prevent abuse of authentication operations while ensuring a good user experience. Rate limiting is applied per user (identified by `tenantId` + `identifier` combination) with **different thresholds for different operation types**.

## Key Improvements

The rate limiting system now supports **operation-specific thresholds**:

1. **Verification Code Sending** - More restrictive (5 requests/minute) to prevent spam
2. **Login Operations** - Standard limits (10 requests/minute) for balanced security
3. **Captcha Generation** - More permissive (20 requests/minute) since users may need multiple attempts
4. **Token Refresh** - Standard limits (10 requests/minute)

This ensures that:
- Captcha requests don't interfere with login attempts
- First-time email verification requests work smoothly
- Different operations maintain independent rate limit counters

## Configuration

Rate limiting is configured via `application.yml` with separate settings for each operation type:

```yaml
security:
  rate-limit:
    # Default settings (backward compatibility)
    max-attempts: 10
    window-minutes: 1
    
    # IP-based rate limiting
    ip:
      max-attempts: 50
      window-minutes: 1
    
    # Verification code sending (more restrictive)
    send-code:
      max-attempts: 5
      window-minutes: 1
    
    # Login operations (balanced)
    login:
      max-attempts: 10
      window-minutes: 1
    
    # Captcha generation (more permissive)
    captcha:
      max-attempts: 20
      window-minutes: 1
    
    # Token refresh
    refresh-token:
      max-attempts: 10
      window-minutes: 1
```

## Rate Limited Operations

The following operations have independent rate limits based on their risk profile:

### 1. Verification Code Requests (More Restrictive: 5/minute)

**Email Verification Code (`/api/auth/send-email-code`)**
- **Action**: `RATE_LIMIT_SEND_EMAIL_CODE`
- **Identifier**: User's email address
- **Limit**: 5 requests per minute (configurable via `send-code.max-attempts`)
- **Reason**: More restrictive to prevent spam and abuse
- **Example**: User `user@example.com` in tenant `tenant1` can request at most 5 email verification codes per minute

**Phone Verification Code (`/api/auth/send-phone-code`)**
- **Action**: `RATE_LIMIT_SEND_PHONE_CODE`
- **Identifier**: User's phone number
- **Limit**: 5 requests per minute (configurable via `send-code.max-attempts`)
- **Reason**: More restrictive to prevent SMS spam and associated costs
- **Example**: User with phone `+1234567890` in tenant `tenant1` can request at most 5 phone verification codes per minute

### 2. Login Operations (Standard: 10/minute)

**Password Login (`/api/auth/login-password`)**
- **Action**: `RATE_LIMIT_LOGIN_PASSWORD`
- **Identifier**: Username
- **Limit**: 10 requests per minute (configurable via `login.max-attempts`)
- **Example**: User `john_doe` in tenant `tenant1` can attempt password login at most 10 times per minute

**Email Login (`/api/auth/login-email`)**
- **Action**: `RATE_LIMIT_LOGIN_EMAIL`
- **Identifier**: Email address
- **Limit**: 10 requests per minute (configurable via `login.max-attempts`)
- **Example**: User with email `user@example.com` in tenant `tenant1` can attempt email login at most 10 times per minute

**Phone Login (`/api/auth/login-phone`)**
- **Action**: `RATE_LIMIT_LOGIN_PHONE`
- **Identifier**: Phone number
- **Limit**: 10 requests per minute (configurable via `login.max-attempts`)
- **Example**: User with phone `+1234567890` in tenant `tenant1` can attempt phone login at most 10 times per minute

### 3. Captcha Generation (More Permissive: 20/minute)

**Captcha Request (`/api/auth/captcha`)**
- **Action**: Varies based on the captcha type (e.g., `CAPTCHA_LOGIN_PASSWORD`)
- **Identifier**: Username, email, or phone (depending on the action)
- **Limit**: 20 requests per minute (configurable via `captcha.max-attempts`)
- **Reason**: Users may need multiple attempts to read/solve captchas
- **Example**: User `john_doe` in tenant `tenant1` can request captcha at most 20 times per minute
- **Important**: Captcha rate limits are **independent** from login rate limits

### 4. Token Refresh (Standard: 10/minute)

**Token Refresh (`/api/auth/refresh-token`)**
- **Action**: `RATE_LIMIT_REFRESH_TOKEN`
- **Identifier**: User ID
- **Limit**: 10 requests per minute (configurable via `refresh-token.max-attempts`)
- **Example**: User with ID `12345` in tenant `tenant1` can refresh token at most 10 times per minute

## Identifier Field Usage

The `identifier` field is a critical component of the rate limiting system:

### Purpose

1. **Per-User Rate Limiting**: Tracks requests per individual user rather than globally
2. **Multi-Tenant Isolation**: Combined with `tenantId` to ensure tenant isolation
3. **Action-Specific Tracking**: Different identifiers for different authentication methods

### Examples

#### Captcha Request
```json
{
  "tenantId": "tenant1",
  "identifier": "john_doe",
  "action": "CAPTCHA_LOGIN_PASSWORD"
}
```
- The `identifier` is the username for password-based login
- Rate limiting key: `rate:CAPTCHA_LOGIN_PASSWORD:tenant1:john_doe`

#### Email Verification Code
```json
{
  "tenantId": "tenant1",
  "email": "user@example.com",
  "purpose": "REGISTER"
}
```
- The identifier is automatically set to the email address
- Rate limiting key: `rate:RATE_LIMIT_SEND_EMAIL_CODE:tenant1:user@example.com`

## Rate Limit Exceeded Response

When rate limit is exceeded, the API returns:

```json
{
  "code": "RATE_LIMITED",
  "message": "Too many requests",
  "timestamp": "2026-01-22T03:45:00Z"
}
```

**HTTP Status Code**: 429 (Too Many Requests)

## Best Practices

### For API Consumers

1. **Implement Exponential Backoff**: When receiving a `RATE_LIMITED` error, wait before retrying
2. **Cache Results**: Don't request the same verification code multiple times
3. **User Feedback**: Inform users about rate limits and remaining time
4. **Proper Identifiers**: Always provide the correct identifier matching the authentication method

### For System Administrators

1. **Monitor Rate Limits**: Track how often users hit rate limits
2. **Adjust Configuration**: Tune `max-attempts` and `window-minutes` based on usage patterns
3. **Consider Environment**: Production may need stricter limits than development
4. **Alert on Abuse**: Set up monitoring for users consistently hitting rate limits

## Adjusting Rate Limits

### For Development
In `application-dev.yml`:
```yaml
security:
  rate-limit:
    max-attempts: 10
    window-minutes: 1
    send-code:
      max-attempts: 5      # Restrictive for code sending
      window-minutes: 1
    login:
      max-attempts: 10     # Standard for logins
      window-minutes: 1
    captcha:
      max-attempts: 20     # Permissive for captcha
      window-minutes: 1
    refresh-token:
      max-attempts: 10
      window-minutes: 1
```

### For Production
In `application-prod.yml` or via environment variables:
```yaml
security:
  rate-limit:
    send-code:
      max-attempts: ${RATE_LIMIT_SEND_CODE_MAX_ATTEMPTS:5}
      window-minutes: ${RATE_LIMIT_SEND_CODE_WINDOW_MINUTES:1}
    login:
      max-attempts: ${RATE_LIMIT_LOGIN_MAX_ATTEMPTS:10}
      window-minutes: ${RATE_LIMIT_LOGIN_WINDOW_MINUTES:1}
    captcha:
      max-attempts: ${RATE_LIMIT_CAPTCHA_MAX_ATTEMPTS:20}
      window-minutes: ${RATE_LIMIT_CAPTCHA_WINDOW_MINUTES:1}
    refresh-token:
      max-attempts: ${RATE_LIMIT_REFRESH_TOKEN_MAX_ATTEMPTS:10}
      window-minutes: ${RATE_LIMIT_REFRESH_TOKEN_WINDOW_MINUTES:1}
```

Or using environment variables:
```bash
# Verification code sending
RATE_LIMIT_SEND_CODE_MAX_ATTEMPTS=5
RATE_LIMIT_SEND_CODE_WINDOW_MINUTES=1

# Login operations
RATE_LIMIT_LOGIN_MAX_ATTEMPTS=10
RATE_LIMIT_LOGIN_WINDOW_MINUTES=1

# Captcha generation
RATE_LIMIT_CAPTCHA_MAX_ATTEMPTS=20
RATE_LIMIT_CAPTCHA_WINDOW_MINUTES=1

# Token refresh
RATE_LIMIT_REFRESH_TOKEN_MAX_ATTEMPTS=10
RATE_LIMIT_REFRESH_TOKEN_WINDOW_MINUTES=1
```

## Example Scenarios

### Scenario 1: Email Verification Code Rate Limit (5 requests/minute)

```
Time: 00:00
User: user@example.com requests verification code
Result: Success (1/5 requests used)

Time: 00:15
User: user@example.com requests verification code again
Result: Success (2/5 requests used)

Time: 00:30
User: user@example.com requests verification code (3rd time)
Result: Success (3/5 requests used)

Time: 00:45
User: user@example.com requests verification code (4th time)
Result: Success (4/5 requests used)

Time: 00:50
User: user@example.com requests verification code (5th time)
Result: Success (5/5 requests used)

Time: 00:55
User: user@example.com requests verification code (6th time)
Result: RATE_LIMITED error - exceeded 5 requests per minute

Time: 01:01 (window expired)
User: user@example.com requests verification code
Result: Success (1/5 requests in new window)
```

### Scenario 2: Captcha Generation with Independent Rate Limit (20 requests/minute)

```
Tenant: tenant1
Username: john_doe
Action: Login with password

Attempt 1-3: Login attempts (3 failures)
Result: Captcha required after 3 failures

Captcha Requests 1-20: Request captcha images
Result: Success - captcha has its own limit of 20/minute

Login Attempt 4 (with valid captcha): Success
Result: Can still login - login limit (10/minute) is separate from captcha limit

Key Point: The user can request up to 20 captchas per minute without 
affecting their ability to attempt login (10 attempts/minute)
```

### Scenario 3: Multiple Operations Don't Interfere

```
User: user@example.com
Tenant: tenant1

Time: 00:00 - Request email verification code (1/5 send-code limit)
Time: 00:10 - Request email verification code (2/5 send-code limit)
Time: 00:20 - Attempt email login (1/10 login limit)
Time: 00:25 - Request captcha for login (1/20 captcha limit)
Time: 00:30 - Attempt email login with captcha (2/10 login limit)

Result: All operations succeed because they have independent rate limits:
- Send code: 2/5 used
- Login: 2/10 used  
- Captcha: 1/20 used
```

## Implementation Details

Rate limiting is implemented using Redis with time-based expiration:

- **Storage**: Redis key-value store
- **Key Format**: `rate:{ACTION}:{TENANT_ID}:{IDENTIFIER}`
- **Value**: Request count
- **Expiration**: Set to `window-minutes` configuration value
- **Atomic Operations**: Uses Redis INCR for thread-safe counting

## Troubleshooting

### Problem: Legitimate Users Being Rate Limited

**Solution**: Increase `max-attempts` or `window-minutes` in configuration

### Problem: Rate Limits Not Working

**Checks**:
1. Verify Redis is running and accessible
2. Check Redis keys: `redis-cli KEYS "rate:*"`
3. Verify configuration is loaded correctly
4. Check application logs for errors

### Problem: Rate Limits Inconsistent Across Instances

**Solution**: Ensure all application instances connect to the same Redis instance for rate limiting to work correctly in clustered deployments
