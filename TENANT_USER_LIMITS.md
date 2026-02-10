# Tenant User Limits and Daily Registration Controls

## Overview

This document describes the tenant user limits and daily registration control features that help prevent abuse and manage tenant capacity.

## Features

### 1. Tenant Maximum Users Limit

Each tenant can have a maximum number of users limit. When this limit is reached, no new users can register for that tenant.

#### Database Schema

The `tenants` table includes a `max_users` field:
- Type: `INT`
- Nullable: `YES`
- Default: `NULL` (unlimited)

#### Behavior

- If `max_users` is `NULL`: Unlimited users are allowed
- If `max_users` is a positive integer: Registration is blocked when the count reaches this limit
- Error returned when limit is reached: `TENANT_MAX_USERS_REACHED` (400406)

#### Setting the Limit

Update the tenant record directly in the database:

```sql
-- Set max users to 1000 for a tenant
UPDATE tenants SET max_users = 1000 WHERE tenant_id = 'your-tenant-id';

-- Remove the limit (unlimited users)
UPDATE tenants SET max_users = NULL WHERE tenant_id = 'your-tenant-id';
```

#### Migration

To add this field to an existing database, run:

```sql
-- Migration script
ALTER TABLE tenants ADD COLUMN max_users INT NULL 
  COMMENT 'Maximum number of users allowed for this tenant. NULL means unlimited.';
```

Or use the provided migration script at `src/main/resources/migration_add_tenant_limits.sql`.

### 2. Daily Registration Limits per Tenant/IP

This feature limits the number of user registrations from the same IP address per tenant per day. This helps prevent mass account creation and abuse.

#### Configuration

Configure in `application.yml` under `security.rate-limit.daily-registration`:

```yaml
security:
  rate-limit:
    daily-registration:
      enabled: true  # Enable/disable the feature
      max-registrations-per-day: 10  # Maximum registrations per IP per tenant per day
```

#### Behavior

- Tracks registrations using Redis with a 24-hour window
- Key format: `registration:daily:<tenantId>:<clientIP>`
- When limit is reached, returns error: `DAILY_REGISTRATION_LIMIT_REACHED` (400703)
- Counter resets automatically after 24 hours

#### IP Address Extraction

The system uses the `IpUtils.getClientIp()` method which checks the following headers in order:
1. `X-Forwarded-For`
2. `X-Real-IP`
3. `Proxy-Client-IP`
4. `WL-Proxy-Client-IP`
5. `HTTP_CLIENT_IP`
6. `HTTP_X_FORWARDED_FOR`
7. Remote address from request

## Affected Registration Endpoints

Both limits are enforced on all registration endpoints:

1. **Password Registration**: `POST /api/auth/register-password`
2. **Email Registration**: `POST /api/auth/register-email`
3. **Phone Registration**: `POST /api/auth/register-phone`
4. **Phone Quick Login** (when creating new user): `POST /api/auth/quick-login-phone`
5. **WeChat Login/Register** (when creating new user): `POST /api/auth/wechat-login`

## Error Responses

### Tenant Max Users Reached

```json
{
  "success": false,
  "errorCode": "400406",
  "message": "Tenant has reached maximum users limit",
  "data": null
}
```

**Chinese**: 租户已达到最大用户数限制

### Daily Registration Limit Reached

```json
{
  "success": false,
  "errorCode": "400703",
  "message": "Daily registration limit reached",
  "data": null
}
```

**Chinese**: 已达到每日注册数量限制

## Implementation Details

### Max Users Check

The system performs the following check before each registration:

```java
// 1. Check if tenant has max users limit
userService.checkMaxUsersLimit(tenantId);

// This method:
// - Retrieves tenant configuration
// - If max_users is NULL, allows registration
// - Otherwise, counts current users for the tenant
// - Throws exception if count >= max_users
```

### Daily Registration Check

The system tracks registrations per tenant/IP combination:

```java
// 2. Check daily registration limit
rateLimitService.checkDailyRegistrationLimit(tenantId);

// This method:
// - Extracts client IP address
// - Increments counter in Redis with 24-hour expiry
// - Throws exception if counter exceeds configured limit
```

## Security Considerations

1. **Fail Closed**: If IP extraction fails, the request is rejected for security
2. **Independent Tracking**: Each tenant has separate counters per IP
3. **Atomic Operations**: Uses Redis Lua scripts for race-condition-free counting
4. **Automatic Cleanup**: Redis TTL ensures counters are cleaned up automatically

## Monitoring and Troubleshooting

### Check Current User Count

```sql
SELECT tenant_id, COUNT(*) as user_count 
FROM users 
GROUP BY tenant_id;
```

### Check Tenant Limits

```sql
SELECT tenant_id, name, max_users, 
  (SELECT COUNT(*) FROM users WHERE users.tenant_id = tenants.tenant_id) as current_users
FROM tenants;
```

### Redis Keys

Daily registration counters are stored with the pattern:
```
registration:daily:<tenant_id>:<ip_address>
```

You can inspect them using Redis CLI:
```bash
# List all registration counters
redis-cli KEYS "registration:daily:*"

# Get counter value
redis-cli GET "registration:daily:tenant1:192.168.1.100"

# Get TTL (time to live)
redis-cli TTL "registration:daily:tenant1:192.168.1.100"
```

## Best Practices

1. **Set Appropriate Limits**: Consider your use case when setting `max-registrations-per-day`
   - Public services: 5-10 per day
   - Internal services: 20-50 per day
   - Development/Testing: Can be disabled or set higher

2. **Monitor Tenant Growth**: Regularly check user counts against limits to plan capacity

3. **IP-Based Limits**: Remember that IP-based limits may affect legitimate users behind NAT/proxies. Balance security with user experience.

4. **Tenant Configuration**: Set `max_users` based on your licensing or capacity planning needs

## Example Configuration

### Production Configuration

```yaml
security:
  rate-limit:
    daily-registration:
      enabled: true
      max-registrations-per-day: 5  # More restrictive for production
```

### Development Configuration

```yaml
security:
  rate-limit:
    daily-registration:
      enabled: false  # or set a higher limit
      max-registrations-per-day: 100
```

## Testing

To test these features:

1. **Max Users Limit**:
   - Set a low limit (e.g., 5) on a test tenant
   - Register users until limit is reached
   - Verify error response

2. **Daily Registration Limit**:
   - Configure a low limit (e.g., 2)
   - Register multiple users from the same IP
   - Verify limit enforcement
   - Wait 24 hours or clear Redis to reset counter

## Conclusion

These features provide flexible controls for managing tenant capacity and preventing registration abuse. They can be configured per-deployment to match your specific requirements and security policies.
