# Auth Log and Token Blacklist Fix Guide

## Problem Summary

When users logged in multiple times, no records appeared in the `auth_log` and `token_blacklist` tables. This was caused by the database schema not being automatically initialized when the application starts.

## Root Cause

The application had two issues:

1. **Missing Database Schema Initialization**: The `schema.sql` file existed but Spring Boot was not configured to automatically execute it on startup. Users needed to manually run the schema, which often wasn't done.

2. **Silent Exception Handling**: When database operations failed (because tables didn't exist), exceptions were being caught and completely ignored in `TokenService.java` and `WeChatAuthService.java`, making it impossible to diagnose the problem.

## Solution

### 1. Automatic Schema Initialization (Primary Fix)

Added Spring Boot SQL initialization configuration to automatically create database tables on startup:

**application-dev.yml** and **application-test.yml**:
```yaml
spring:
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
      continue-on-error: false
```

**application-prod.yml**:
```yaml
spring:
  sql:
    init:
      mode: ${DB_INIT_MODE:never}  # Configurable via environment variable
      schema-locations: classpath:schema.sql
      continue-on-error: false
```

This ensures that:
- In **development** and **test** environments, the schema is always initialized
- In **production**, you can control this via the `DB_INIT_MODE` environment variable (set to `always` for first deployment, then `never` to avoid re-running on every restart)

### 2. Improved Error Logging (Secondary Fix)

Changed exception handling to log errors with full stack traces instead of silently ignoring them:

```java
// Before (BAD):
catch (Exception ignored) {
    // ignore logging failures
}

// After (GOOD):
catch (Exception ex) {
    logger.error("Failed to record audit log for tenant={} userId={} action={} success={}", 
        tenantId, userId, action, success, ex);
}
```

This helps diagnose issues if schema initialization fails or if there are other database problems.

## Files Changed

1. **src/main/resources/application-dev.yml**
   - Added `spring.sql.init` configuration for automatic schema initialization

2. **src/main/resources/application-test.yml**
   - Added `spring.sql.init` configuration for automatic schema initialization

3. **src/main/resources/application-prod.yml**
   - Added `spring.sql.init` configuration with environment variable control

4. **src/main/java/com/mercury/auth/service/TokenService.java**
   - Fixed `safeRecord` method to log exceptions instead of ignoring them
   - Upgraded token blacklist insertion error logging from `warn` to `error`

5. **src/main/java/com/mercury/auth/service/WeChatAuthService.java**
   - Fixed `safeRecord` method to log exceptions instead of ignoring them

## Deployment Instructions

### For New Deployments

The schema will be automatically created when you start the application. No manual intervention needed in dev/test environments.

### For Production Deployments

**First Time:**
Set the environment variable to initialize the schema:
```bash
export DB_INIT_MODE=always
java -jar mercury-auth.jar
```

**Subsequent Deployments:**
To avoid re-running schema initialization on every restart:
```bash
export DB_INIT_MODE=never
java -jar mercury-auth.jar
```

Or don't set the variable at all (defaults to `never`).

### Manual Schema Initialization (Alternative)

If you prefer to manage schema manually in production:
1. Keep `DB_INIT_MODE=never` (the default)
2. Run the schema manually:
```sql
SOURCE /path/to/schema.sql;
```

## Monitoring Recommendations

After deploying, monitor your application logs:

### 1. Schema Initialization Success
```
INFO ... Successfully executed SQL script from resource [classpath:schema.sql]
```

### 2. Auth Log Insertion Failures (should not appear if schema is initialized)
```
ERROR ... Failed to record audit log for tenant=... userId=... action=... success=...
```

### 3. Token Blacklist Insertion Failures (should not appear if schema is initialized)
```
ERROR ... Failed to insert token blacklist entry tenant=... tokenHash=...
```

## Testing

To verify the fix is working:

1. **Start the application** - Watch the logs for schema initialization:
   ```
   INFO ... Initializing SQL scripts
   ```

2. **Query the database** after startup:
   ```sql
   SHOW TABLES LIKE 'auth_logs';
   SHOW TABLES LIKE 'token_blacklist';
   ```

3. **Perform authentication actions** (login, logout)

4. **Verify records are being created**:
   ```sql
   SELECT COUNT(*) FROM auth_logs;
   SELECT COUNT(*) FROM token_blacklist;
   ```

Records should now appear in both tables.

## Troubleshooting

### If schema initialization fails:

1. **Check database connectivity**: Ensure the database server is accessible
2. **Check permissions**: Database user needs CREATE TABLE permissions
3. **Check existing tables**: If tables already exist, set `continue-on-error: true` or use `mode: never`

### If you see "Table already exists" errors:

This means the schema was already initialized. You can:
- Set `spring.sql.init.mode: never` to disable schema initialization
- Or set `continue-on-error: true` to ignore the error

## Security Note

The auth log is crucial for:
- Security auditing
- Compliance requirements
- Detecting suspicious activities
- Investigating security incidents

With this fix, auth logging will work automatically without manual database setup.

