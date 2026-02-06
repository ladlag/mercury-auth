# OOM Fix Deployment Guide

## Quick Summary
This PR fixes OOM (Out of Memory) crashes in 2C4G pods by:
1. Adding scheduled cleanup of auth logs (7-day retention)
2. Limiting database query result sizes (max 1000 rows)
3. Reducing token cache size (10K → 5K in production)
4. Optimizing log file retention (30 days → 7 days)

## Pre-Deployment Checklist

### 1. Review Configuration
Review these new configuration properties and adjust for your environment:

```yaml
security:
  audit:
    retention-days: 7  # Adjust based on compliance requirements
  
  cleanup:
    enabled: true  # Set to false to disable cleanup tasks
    auth-logs-cron: "0 0 2 * * ?"  # Daily at 2 AM
    ip-blacklist-cron: "0 0 3 * * ?"  # Daily at 3 AM
  
  blacklist:
    query-limit: 1000  # Max blacklist entries per query
  
  tenant:
    query-limit: 1000  # Max tenants per query
  
  token-cache:
    max-size: 5000  # Reduced from 10000 for 2C4G pods
```

### 2. Database Preparation
Before deploying, check your database:

```sql
-- Check auth_logs table size
SELECT COUNT(*) FROM auth_logs;
SELECT 
  MIN(created_at) as oldest,
  MAX(created_at) as newest,
  COUNT(*) as total_rows
FROM auth_logs;

-- Check ip_blacklist table size
SELECT COUNT(*) FROM ip_blacklist;

-- Ensure sufficient disk space for cleanup operation
SHOW TABLE STATUS LIKE 'auth_logs';
```

**Important**: The first cleanup may delete many rows if the table is large. Ensure:
- Database has sufficient disk space for temporary operations
- Backup is taken before deployment
- Cleanup runs during low-traffic hours

### 3. Environment Variables (Optional)

Set these environment variables to override defaults:

```bash
# Audit configuration
export AUDIT_RETENTION_DAYS=7  # Days to keep auth logs

# Cleanup configuration
export CLEANUP_ENABLED=true
export CLEANUP_AUTH_LOGS_CRON="0 0 2 * * ?"
export CLEANUP_IP_BLACKLIST_CRON="0 0 3 * * ?"

# Query limits
export BLACKLIST_QUERY_LIMIT=1000
export TENANT_QUERY_LIMIT=1000

# Cache configuration
export TOKEN_CACHE_MAX_SIZE=5000
export TOKEN_CACHE_EXPIRE_SECONDS=600
```

## Deployment Steps

### Step 1: Deploy to Test Environment
```bash
# Build the application
mvn clean package -DskipTests

# Deploy to test environment (adjust based on your deployment method)
kubectl apply -f deployment-test.yaml

# Or using Docker
docker build -t mercury-auth:oom-fix .
docker run -d -p 10000:10000 mercury-auth:oom-fix
```

### Step 2: Verify Application Starts
Check logs for successful startup:
```bash
kubectl logs -f deployment/mercury-auth | grep "Started MercuryAuthApplication"
```

Expected log messages:
```
Started MercuryAuthApplication in X.XXX seconds
```

### Step 3: Monitor Scheduled Tasks
Wait for the next scheduled cleanup (or trigger manually for testing):

**Check cleanup runs successfully:**
```bash
# View logs at 2 AM for auth log cleanup
kubectl logs deployment/mercury-auth | grep "Cleaned up.*auth log"

# Expected output:
# Cleaned up 1234 old auth log entries (older than 7 days)
```

### Step 4: Monitor Memory Usage

**Before fix (baseline):**
```bash
# Check current memory usage
kubectl top pod -l app=mercury-auth
```

**After fix (after 24 hours):**
Monitor these metrics:
- JVM heap usage should be lower (~2-5MB reduction)
- Pod memory should stabilize (no gradual increase)
- No OOM crashes after multiple hours

**Monitoring commands:**
```bash
# Continuous monitoring
watch -n 60 'kubectl top pod -l app=mercury-auth'

# Check for OOM events
kubectl get events --field-selector reason=OOMKilling

# View application metrics
curl http://localhost:10000/auth-api/actuator/health
```

### Step 5: Validate Functionality

Test key features still work:
```bash
# Test authentication
curl -X POST http://localhost:10000/auth-api/api/auth/login-password \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: test-tenant" \
  -d '{"username":"test","password":"test123"}'

# Test token verification
curl -X POST http://localhost:10000/auth-api/api/auth/verify-token \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Step 6: Monitor for 48 Hours

Key metrics to watch:
- **Memory stability**: No gradual increase over time
- **No OOM crashes**: Service runs continuously
- **Cleanup operations**: Successful daily cleanup logs
- **API performance**: No degradation in response times
- **Database size**: auth_logs table size remains bounded

## Production Deployment

Once test environment is stable for 48 hours:

### 1. Schedule Maintenance Window
- Deploy during low-traffic hours
- Allow time for initial large cleanup operation
- Have rollback plan ready

### 2. Deploy to Production
```bash
# Deploy with appropriate environment variables
kubectl apply -f deployment-prod.yaml

# Or use your CI/CD pipeline
./deploy-to-prod.sh
```

### 3. Post-Deployment Monitoring

**First 24 Hours:**
- Monitor for OOM crashes (should be zero)
- Check cleanup runs successfully at scheduled times
- Verify memory usage stays stable
- Monitor API performance and error rates

**After First Cleanup:**
```bash
# Check cleanup was successful
kubectl logs deployment/mercury-auth | grep "Cleaned up"

# Verify database table size reduced
SELECT COUNT(*) FROM auth_logs WHERE created_at < NOW() - INTERVAL 7 DAY;
# Should be 0 after cleanup
```

## Rollback Plan

If issues occur:

### Option 1: Disable Cleanup (Keep Other Fixes)
```bash
# Set environment variable
kubectl set env deployment/mercury-auth CLEANUP_ENABLED=false

# Or edit configmap
kubectl edit configmap mercury-auth-config
```

### Option 2: Full Rollback
```bash
# Rollback to previous version
kubectl rollback deployment/mercury-auth

# Or redeploy previous version
kubectl apply -f deployment-previous-version.yaml
```

## Troubleshooting

### Issue: Cleanup Not Running
**Check:**
```bash
# Verify scheduled cleanup service is enabled
kubectl exec -it deployment/mercury-auth -- \
  grep "cleanup.enabled" /app/config/application.yml

# Check for errors in logs
kubectl logs deployment/mercury-auth | grep -i "cleanup\|schedule"
```

**Solution:**
- Verify `security.cleanup.enabled=true`
- Check cron expression is valid
- Ensure application has database write permissions

### Issue: Large Initial Cleanup Causes Issues
**Symptoms:**
- Database connection timeouts
- High CPU usage during cleanup
- Long-running transactions

**Solution:**
```bash
# Temporarily increase cleanup retention to reduce first cleanup size
kubectl set env deployment/mercury-auth AUDIT_RETENTION_DAYS=14

# Or run manual cleanup in batches
mysql> DELETE FROM auth_logs 
       WHERE created_at < NOW() - INTERVAL 30 DAY 
       LIMIT 10000;
# Run multiple times until complete
```

### Issue: Memory Still High
**Check:**
1. Token cache size is reduced: `TOKEN_CACHE_MAX_SIZE=5000`
2. Query limits are applied: Check `BLACKLIST_QUERY_LIMIT` and `TENANT_QUERY_LIMIT`
3. Log retention is optimized: Check logback-spring.xml
4. No Redis memory issues: `redis-cli INFO memory`

**Additional tuning:**
```bash
# Further reduce cache size for 2C4G pods
export TOKEN_CACHE_MAX_SIZE=2000

# Reduce cache TTL to expire entries faster
export TOKEN_CACHE_EXPIRE_SECONDS=300

# Reduce query limits if needed
export BLACKLIST_QUERY_LIMIT=500
export TENANT_QUERY_LIMIT=500
```

## Performance Impact

**Expected behavior:**

| Metric | Before Fix | After Fix | Notes |
|--------|-----------|-----------|-------|
| **Uptime** | 2-4 hours | Weeks/months | No OOM crashes |
| **Memory** | Gradual increase | Stable | ~2-5MB reduction |
| **DB Size** | Unbounded growth | Bounded | 7 days of data max |
| **API Latency** | No change | No change | Should not increase |
| **CPU Usage** | Normal | Slightly higher during cleanup | 2-3 AM only |

## Success Criteria

After 48 hours in production:
- ✅ No OOM crashes
- ✅ Memory usage remains stable (no gradual increase)
- ✅ Cleanup operations complete successfully
- ✅ auth_logs table size bounded to ~7 days
- ✅ API performance unchanged
- ✅ No increase in error rates

## Contact and Support

If you encounter issues:
1. Check application logs: `kubectl logs deployment/mercury-auth`
2. Check cleanup logs at 2 AM and 3 AM
3. Review metrics: memory, CPU, database size
4. Refer to OOM_FIX_SUMMARY.md for detailed analysis

For urgent issues:
- Disable cleanup: `CLEANUP_ENABLED=false`
- Rollback to previous version
- Contact development team with logs and metrics
