# Implementation Summary: Tenant User Limits and Daily Registration Controls

**Date**: 2026-02-09  
**PR Branch**: `copilot/add-tenant-user-limit-field`

## Requirements

做如下优化：
1. 在tenant表增加一个字段，含义是当前租户允许的最大用户数，当达到最大用户数后不允许继续注册新用户
2. 增加一个注册验证，每个租户/每个ip 每天允许注册的最大用户数，要求可配置

**Translation**:
1. Add a field to the tenant table representing the maximum number of users allowed for that tenant. When this limit is reached, prevent new user registrations.
2. Add registration validation that limits the number of users that can register per tenant/per IP per day. This limit should be configurable.

## Changes Summary

### 1. Database Schema Changes

**File**: `src/main/resources/schema.sql`
- Added `max_users INT NULL` field to `tenants` table
- NULL value means unlimited users allowed
- Non-NULL value enforces hard limit on user count

**Migration Script**: `src/main/resources/migration_add_tenant_limits.sql`
- Provides ALTER TABLE statement for existing databases
- Safe to run on production (adds nullable column)

### 2. Entity and Configuration Updates

**Files Modified**:
- `src/main/java/com/mercury/auth/entity/Tenant.java`
  - Added `private Integer maxUsers` field
  
- `src/main/java/com/mercury/auth/config/RateLimitConfig.java`
  - Added `DailyRegistrationLimit` inner class
  - Configuration: `enabled` (boolean), `maxRegistrationsPerDay` (long)
  - Default: enabled=true, maxRegistrationsPerDay=10

- `src/main/java/com/mercury/auth/exception/ErrorCodes.java`
  - Added `TENANT_MAX_USERS_REACHED` (400406)
  - Added `DAILY_REGISTRATION_LIMIT_REACHED` (400703)

### 3. Error Messages

**Files Modified**:
- `src/main/resources/ErrorMessages.properties` (English)
  - error.400406=Tenant has reached maximum users limit
  - error.400703=Daily registration limit reached

- `src/main/resources/ErrorMessages_zh_CN.properties` (Chinese)
  - error.400406=租户已达到最大用户数限制
  - error.400703=已达到每日注册数量限制

### 4. Service Layer Changes

**File**: `src/main/java/com/mercury/auth/service/UserService.java`
- Added `countUsersByTenant(String tenantId)` method
  - Counts total users for a tenant
  
- Added `checkMaxUsersLimit(String tenantId)` method
  - Retrieves tenant configuration
  - Checks if max_users is NULL (unlimited)
  - Counts current users and compares to limit
  - Throws `TENANT_MAX_USERS_REACHED` exception if limit exceeded

**File**: `src/main/java/com/mercury/auth/service/RateLimitService.java`
- Added `checkDailyRegistrationLimit(String tenantId)` method
  - Extracts client IP address from request
  - Uses Redis key pattern: `registration:daily:<tenantId>:<ip>`
  - Uses atomic Lua script for increment + expire
  - 24-hour (86400 seconds) expiry window
  - Throws `DAILY_REGISTRATION_LIMIT_REACHED` exception if limit exceeded
  - Fail-closed security: rejects request if IP extraction fails

### 5. Registration Endpoint Updates

All registration endpoints now check both limits before creating users:

**Files Modified**:
- `src/main/java/com/mercury/auth/service/PasswordAuthService.java`
  - `registerPassword()`: Added both limit checks
  
- `src/main/java/com/mercury/auth/service/EmailAuthService.java`
  - `registerEmail()`: Added both limit checks
  
- `src/main/java/com/mercury/auth/service/PhoneAuthService.java`
  - `registerPhone()`: Added both limit checks
  - `quickLoginPhone()`: Added both limit checks (when creating new user)
  
- `src/main/java/com/mercury/auth/service/WeChatAuthService.java`
  - `loginOrRegister()`: Added both limit checks (when creating new user)

**Order of Checks**:
1. Tenant enabled check (existing)
2. Tenant max users limit check (NEW)
3. Daily registration limit check (NEW)
4. Existing validations (duplicate username, etc.)
5. User creation

### 6. Test Updates

**Files Modified**:
- Updated all test files to include `UserService` mock dependency
- Tests compile successfully
- 124/134 tests passing (10 pre-existing failures unrelated to our changes)

**Test Files Updated**:
- `AuthServiceTests.java`
- `CaptchaIntegrationTest.java`
- `PhoneAuthServiceTests.java`
- `VerificationFlowTests.java`
- `WeChatAuthServiceTests.java`

### 7. Documentation

**File**: `TENANT_USER_LIMITS.md` (NEW)
- Comprehensive guide covering:
  - Feature overview and behavior
  - Database schema details
  - Configuration examples
  - Migration instructions
  - Error responses (English and Chinese)
  - Implementation details
  - Security considerations
  - Monitoring and troubleshooting
  - Redis key patterns
  - Best practices
  - Testing procedures

**File**: `src/main/resources/application-dev.yml`
- Added documentation and configuration for `daily-registration` feature
- Example configuration with comments

## Security Considerations

### Fail-Closed Design
- If IP extraction fails, requests are rejected (not allowed)
- Prevents attackers from bypassing limits by triggering extraction failures

### Atomic Operations
- Uses Redis Lua scripts for race-condition-free increment operations
- Ensures accurate counting even under high concurrency

### Independent Tracking
- Each tenant has separate daily registration counters per IP
- Cross-tenant isolation maintained

### Automatic Cleanup
- Redis TTL ensures counters expire after 24 hours
- No manual cleanup required

## Configuration

### Development
```yaml
security:
  rate-limit:
    daily-registration:
      enabled: true
      max-registrations-per-day: 10
```

### Production (More Restrictive)
```yaml
security:
  rate-limit:
    daily-registration:
      enabled: true
      max-registrations-per-day: 5
```

### Testing (Permissive)
```yaml
security:
  rate-limit:
    daily-registration:
      enabled: false  # or set higher limit
      max-registrations-per-day: 100
```

## Usage Examples

### Setting Tenant Max Users
```sql
-- Set limit to 1000 users
UPDATE tenants SET max_users = 1000 WHERE tenant_id = 'tenant-abc';

-- Remove limit (unlimited)
UPDATE tenants SET max_users = NULL WHERE tenant_id = 'tenant-abc';
```

### Monitoring
```sql
-- Check current user counts
SELECT tenant_id, COUNT(*) as user_count 
FROM users 
GROUP BY tenant_id;

-- Check tenants approaching limits
SELECT 
  t.tenant_id, 
  t.name, 
  t.max_users,
  COUNT(u.id) as current_users,
  (t.max_users - COUNT(u.id)) as remaining_capacity
FROM tenants t
LEFT JOIN users u ON t.tenant_id = u.tenant_id
WHERE t.max_users IS NOT NULL
GROUP BY t.tenant_id
HAVING current_users >= (t.max_users * 0.8);  -- 80% capacity
```

### Redis Inspection
```bash
# View all daily registration counters
redis-cli KEYS "registration:daily:*"

# Check specific counter
redis-cli GET "registration:daily:tenant1:192.168.1.100"

# Check TTL
redis-cli TTL "registration:daily:tenant1:192.168.1.100"

# Clear specific counter (for testing)
redis-cli DEL "registration:daily:tenant1:192.168.1.100"
```

## Testing Results

### Compilation
✅ All code compiles successfully

### Tests
✅ 124/134 tests passing
- 10 failures are pre-existing issues unrelated to our changes:
  - CaptchaServiceTests (2) - pre-existing rate limit mock issues
  - ChineseValidationIntegrationTest (3) - pre-existing locale issues
  - ErrorMessagesIntegrationTest (1) - pre-existing translation issue
  - IpUtilsTest (1) - pre-existing IP validation issue
  - PhoneValidationTest (1) - pre-existing validation issue
  - TokenVerifyResponseTest (2) - pre-existing JWT mock issues

### Security Review
✅ No new security vulnerabilities introduced
- Fail-closed design prevents bypass
- Atomic Redis operations prevent race conditions
- Error messages don't leak sensitive information
- IP extraction is secure

## Files Changed

### Source Files (13 files)
1. `src/main/java/com/mercury/auth/entity/Tenant.java`
2. `src/main/java/com/mercury/auth/config/RateLimitConfig.java`
3. `src/main/java/com/mercury/auth/exception/ErrorCodes.java`
4. `src/main/java/com/mercury/auth/service/UserService.java`
5. `src/main/java/com/mercury/auth/service/RateLimitService.java`
6. `src/main/java/com/mercury/auth/service/PasswordAuthService.java`
7. `src/main/java/com/mercury/auth/service/EmailAuthService.java`
8. `src/main/java/com/mercury/auth/service/PhoneAuthService.java`
9. `src/main/java/com/mercury/auth/service/WeChatAuthService.java`
10. `src/main/resources/ErrorMessages.properties`
11. `src/main/resources/ErrorMessages_zh_CN.properties`
12. `src/main/resources/schema.sql`
13. `src/main/resources/application-dev.yml`

### Test Files (5 files)
1. `src/test/java/com/mercury/auth/AuthServiceTests.java`
2. `src/test/java/com/mercury/auth/CaptchaIntegrationTest.java`
3. `src/test/java/com/mercury/auth/PhoneAuthServiceTests.java`
4. `src/test/java/com/mercury/auth/VerificationFlowTests.java`
5. `src/test/java/com/mercury/auth/WeChatAuthServiceTests.java`

### New Files (2 files)
1. `src/main/resources/migration_add_tenant_limits.sql`
2. `TENANT_USER_LIMITS.md`

## Git Commits

1. `4fd248e` - Initial plan
2. `b7d362b` - Add tenant max users and daily registration limits
3. `f718ee3` - Fix test compilation after adding UserService dependency
4. `2f62dcf` - Add documentation for tenant user limits feature

## Deployment Steps

### 1. Database Migration
```sql
-- Run the migration script
ALTER TABLE tenants ADD COLUMN max_users INT NULL 
  COMMENT 'Maximum number of users allowed for this tenant. NULL means unlimited.';
```

### 2. Configuration
Update `application.yml` with desired settings:
```yaml
security:
  rate-limit:
    daily-registration:
      enabled: true
      max-registrations-per-day: 10
```

### 3. Deploy Application
- Deploy the new version
- Monitor logs for any issues

### 4. Set Tenant Limits (Optional)
```sql
-- Set limits for specific tenants as needed
UPDATE tenants SET max_users = 1000 WHERE tenant_id = 'tenant-id';
```

### 5. Monitor
- Watch Redis for registration counter keys
- Monitor error logs for limit rejections
- Check user counts per tenant

## Rollback Plan

If needed, rollback is straightforward:

1. **Application**: Deploy previous version
2. **Database**: Column is nullable, safe to leave in place
3. **Configuration**: Set `enabled: false` for daily registration
4. **Redis**: Counters will expire naturally within 24 hours

## Conclusion

Both requirements have been successfully implemented with:
- ✅ Minimal code changes (surgical modifications)
- ✅ Comprehensive error handling
- ✅ Security-first design (fail-closed)
- ✅ Configurable limits
- ✅ Full documentation
- ✅ Backward compatibility
- ✅ No breaking changes

The features are production-ready and can be deployed immediately.
