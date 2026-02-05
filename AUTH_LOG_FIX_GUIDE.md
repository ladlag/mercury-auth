# Auth Log and Token Blacklist Fix Guide

## Problem Summary

When users logged in multiple times, no records appeared in the `auth_log` and `token_blacklist` tables. This was caused by silent exception handling in the authentication and token services.

## Root Cause

The `safeRecord` methods in `TokenService.java` and `WeChatAuthService.java` were catching and completely ignoring exceptions:

```java
// Before (BAD):
catch (Exception ignored) {
    // ignore logging failures
}
```

When database operations failed (due to connection issues, schema problems, or mapper configuration errors), the exceptions were silently swallowed, making it impossible to diagnose the problem.

## Solution

Changed the exception handling to log errors with full stack traces:

```java
// After (GOOD):
catch (Exception ex) {
    logger.error("Failed to record audit log for tenant={} userId={} action={} success={}", 
        tenantId, userId, action, success, ex);
}
```

Also upgraded token blacklist insertion errors from `logger.warn` to `logger.error` level with stack traces.

## Files Changed

1. `src/main/java/com/mercury/auth/service/TokenService.java`
   - Fixed `safeRecord` method to log exceptions instead of ignoring them
   - Upgraded token blacklist insertion error logging from `warn` to `error`

2. `src/main/java/com/mercury/auth/service/WeChatAuthService.java`
   - Fixed `safeRecord` method to log exceptions instead of ignoring them

## Monitoring Recommendations

After deploying this fix, monitor your application logs for the following error messages:

### 1. Auth Log Insertion Failures
```
ERROR ... Failed to record audit log for tenant=... userId=... action=... success=...
```

**Possible Causes:**
- Database connection issues
- `auth_logs` table doesn't exist
- MyBatis-Plus mapper not configured correctly
- Database permissions issues
- Column type mismatches

**How to Diagnose:**
1. Check if the database is accessible
2. Verify the `auth_logs` table exists using: `SHOW TABLES LIKE 'auth_logs';`
3. Verify table schema matches the `AuthLog` entity
4. Check MyBatis-Plus logs for mapper initialization errors

### 2. Token Blacklist Insertion Failures
```
ERROR ... Failed to insert token blacklist entry tenant=... tokenHash=...
```

**Possible Causes:**
- Database connection issues
- `token_blacklist` table doesn't exist
- Duplicate `tokenHash` (should be unique)
- Database permissions issues

**How to Diagnose:**
1. Check if the database is accessible
2. Verify the `token_blacklist` table exists using: `SHOW TABLES LIKE 'token_blacklist';`
3. Verify table schema matches the `TokenBlacklist` entity
4. Check for unique constraint violations on `token_hash` column

## Database Setup

If you see errors after deployment, ensure your database schema is properly initialized:

```sql
-- Verify tables exist
SHOW TABLES LIKE 'auth_logs';
SHOW TABLES LIKE 'token_blacklist';

-- Create tables if missing (run schema.sql)
SOURCE src/main/resources/schema.sql;

-- Verify table structure
DESCRIBE auth_logs;
DESCRIBE token_blacklist;
```

## Testing

To verify the fix is working:

1. **Check application logs** - You should see INFO logs like:
   ```
   INFO ... audit action=LOGIN_PASSWORD tenant=... userId=... success=true ip=...
   ```

2. **Query the database** after performing some authentication actions:
   ```sql
   SELECT COUNT(*) FROM auth_logs;
   SELECT COUNT(*) FROM token_blacklist;
   ```

3. **Verify records are being created** - counts should increase after login/logout operations.

## Next Steps

1. Deploy the fix to your environment
2. Monitor logs for any ERROR messages related to auth logging
3. If errors appear, investigate and fix the underlying database/configuration issues
4. Verify that records are being created in `auth_logs` and `token_blacklist` tables

## Security Note

The auth log is crucial for:
- Security auditing
- Compliance requirements
- Detecting suspicious activities
- Investigating security incidents

Ensure that auth logging is working properly to maintain your security posture.
