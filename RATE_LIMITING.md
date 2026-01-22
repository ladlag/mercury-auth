# Rate Limiting Guide

## Overview

This service implements rate limiting to prevent abuse of authentication operations. Rate limiting is applied per user (identified by `tenantId` + `identifier` combination) to ensure fair usage while protecting the system from malicious attacks.

## Configuration

Rate limiting is configured via `application.yml`:

```yaml
security:
  rate-limit:
    max-attempts: 10      # Maximum requests allowed within the window
    window-minutes: 1     # Time window in minutes
```

### Default Settings

- **Max Attempts**: 10 requests
- **Time Window**: 1 minute
- These settings can be overridden using environment variables or profile-specific configuration files

## Rate Limited Operations

The following operations are rate limited:

### 1. Verification Code Requests

**Email Verification Code (`/api/auth/send-email-code`)**
- **Action**: `RATE_LIMIT_SEND_EMAIL_CODE`
- **Identifier**: User's email address
- **Example**: User `user@example.com` in tenant `tenant1` can request at most 10 email verification codes per minute

**Phone Verification Code (`/api/auth/send-phone-code`)**
- **Action**: `RATE_LIMIT_SEND_PHONE_CODE`
- **Identifier**: User's phone number
- **Example**: User with phone `+1234567890` in tenant `tenant1` can request at most 10 phone verification codes per minute

### 2. Login Operations

**Password Login (`/api/auth/login-password`)**
- **Action**: `RATE_LIMIT_LOGIN_PASSWORD`
- **Identifier**: Username
- **Example**: User `john_doe` in tenant `tenant1` can attempt password login at most 10 times per minute

**Email Login (`/api/auth/login-email`)**
- **Action**: `RATE_LIMIT_LOGIN_EMAIL`
- **Identifier**: Email address
- **Example**: User with email `user@example.com` in tenant `tenant1` can attempt email login at most 10 times per minute

**Phone Login (`/api/auth/login-phone`)**
- **Action**: `RATE_LIMIT_LOGIN_PHONE`
- **Identifier**: Phone number
- **Example**: User with phone `+1234567890` in tenant `tenant1` can attempt phone login at most 10 times per minute

### 3. Captcha Generation

**Captcha Request (`/api/auth/captcha`)**
- **Action**: Varies based on the captcha type (e.g., `CAPTCHA_LOGIN_PASSWORD`)
- **Identifier**: Username, email, or phone (depending on the action)
- **Example**: User `john_doe` in tenant `tenant1` can request captcha at most 10 times per minute

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
    max-attempts: 20    # More lenient for development
    window-minutes: 1
```

### For Production
In `application-prod.yml` or via environment variables:
```yaml
security:
  rate-limit:
    max-attempts: 10    # Stricter for production
    window-minutes: 1
```

Or using environment variables:
```bash
SECURITY_RATE_LIMIT_MAX_ATTEMPTS=10
SECURITY_RATE_LIMIT_WINDOW_MINUTES=1
```

## Example Scenarios

### Scenario 1: Email Verification Code Rate Limit

```
Time: 00:00
User: user@example.com requests verification code
Result: Success (1/10 requests used)

Time: 00:05
User: user@example.com requests verification code again (forgot)
Result: Success (2/10 requests used)

... (8 more requests) ...

Time: 00:30
User: user@example.com requests verification code (11th time)
Result: RATE_LIMITED error

Time: 01:01 (window expired)
User: user@example.com requests verification code
Result: Success (1/10 requests in new window)
```

### Scenario 2: Captcha Generation Rate Limit

```
Tenant: tenant1
Username: john_doe

Request 1-10: Success (captcha generated)
Request 11: RATE_LIMITED error
Wait 1 minute
Request 12: Success (new window)
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
