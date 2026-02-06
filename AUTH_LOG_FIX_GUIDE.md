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
- In **development** and **test** environments, the schema is always initialized on startup
  - **Note**: The schema is idempotent (uses `CREATE TABLE IF NOT EXISTS`), so it's safe to run on every restart
- In **production**, you can control this via the `DB_INIT_MODE` environment variable
  - Set to `always` for automatic schema initialization (recommended for new deployments)
  - Set to `never` if you prefer manual schema management
  - **Default is `never`** to prevent accidental schema changes in production

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

The schema will be automatically created when you start the application in dev/test environments.

### For Production Deployments

**IMPORTANT**: Production defaults to `mode: never` for safety. You must explicitly enable schema initialization.

**Option 1: Automatic Schema Initialization (Recommended)**
Set the environment variable to initialize the schema:
```bash
export DB_INIT_MODE=always
java -jar mercury-auth.jar
```

Since the schema is idempotent (uses `CREATE TABLE IF NOT EXISTS`), you can safely keep this setting for all deployments.

**Option 2: Manual Schema Management**
If you prefer manual control:
1. Keep `DB_INIT_MODE` unset or set to `never` (the default)
2. Run the schema manually before first deployment:
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

### If you see "Table doesn't exist" errors in production:

This means schema initialization is disabled (the default). You have two options:

1. **Enable automatic initialization**: Set `DB_INIT_MODE=always` and restart
2. **Run schema manually**: Execute `schema.sql` using MySQL client

### If schema initialization fails:

1. **Check database connectivity**: Ensure the database server is accessible
2. **Check permissions**: Database user needs CREATE TABLE permissions
3. **Check database exists**: Ensure the database specified in the URL exists

### If you see "Table already exists" errors:

This shouldn't happen because the schema uses `CREATE TABLE IF NOT EXISTS`. If you still see this:
- The schema.sql file may have been modified
- Verify the file uses `IF NOT EXISTS` clause

## Security Note

The auth log is crucial for:
- Security auditing
- Compliance requirements
- Detecting suspicious activities
- Investigating security incidents

With this fix, auth logging will work automatically without manual database setup.

