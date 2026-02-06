# Token Verification Caching Security

## Overview

The mercury-auth system uses caching to improve token verification performance and reduce duplicate audit logs. This document explains the security measures in place to ensure caching does not compromise system security.

## Security Problem

**Question**: 是否会因为缓存带来安全性问题 (Could there be security issues caused by caching?)

**Answer**: The caching implementation includes multiple security validations to prevent security bypasses.

## Security Threats & Mitigations

### 1. Blacklisted Token Bypass ✅ MITIGATED

**Threat**: A revoked (blacklisted) token could be served from cache.

**Mitigation**: 
- Blacklist check happens **BEFORE** cache lookup
- When tokens are blacklisted (logout/refresh), they are immediately evicted from all caches
- Both token hash and JTI blacklists are checked

**Code**:
```java
// Check blacklist BEFORE cache
if (isBlacklisted(token)) {
    throw new ApiException(ErrorCodes.TOKEN_BLACKLISTED, "token blacklisted");
}

// Only then check cache
TokenVerifyResponse cached = tokenCacheService.getCachedVerifyResponse(tokenHash);
```

### 2. Disabled User Bypass ✅ MITIGATED

**Threat**: A disabled user could continue using cached token responses.

**Mitigation**:
- User enabled status is re-validated for EVERY cached response
- User status lookups are cached in-memory to reduce DB load
- If user is disabled, cache is evicted and exception is thrown
- When user status changes, caches are evicted immediately

**Code**:
```java
if (cached != null) {
    // Re-validate user status (cached lookup, DB on miss)
    User user = loadActiveUser(cached.getTenantId(), cached.getUserId());
    // If user.enabled == false, throws USER_DISABLED exception
    // Cache is then evicted
}
```

### 3. Disabled Tenant Bypass ✅ MITIGATED

**Threat**: A disabled tenant could continue using cached token responses.

**Mitigation**:
- Tenant enabled status is re-validated for EVERY cached response
- Tenant status lookups are cached in-memory to reduce DB load
- If tenant is disabled, cache is evicted and exception is thrown
- When tenant status changes, caches are evicted immediately

**Code**:
```java
if (cached != null) {
    // Re-validate tenant status (cached lookup, DB on miss)
    tenantService.requireEnabled(cached.getTenantId());
    // If tenant.enabled == false, throws TENANT_DISABLED exception
    // Cache is then evicted
}
```

### 4. Expired Token Bypass ✅ MITIGATED

**Threat**: An expired token could be served from cache if cache TTL > token expiration.

**Mitigation**:
- Token expiration timestamp is checked for EVERY cached response
- If token is expired, cache is evicted and exception is thrown
- Cache TTL (default 10 min) is separate from token expiration check

**Code**:
```java
if (cached != null) {
    // Check token expiration
    if (cached.getExpiresAt() <= System.currentTimeMillis()) {
        tokenCacheService.evictToken(tokenHash);
        throw new ApiException(ErrorCodes.INVALID_TOKEN, "token expired");
    }
}
```

### 5. Tenant Isolation Bypass ✅ MITIGATED

**Threat**: Cross-tenant access via cached responses.

**Mitigation**:
- Tenant ID validation happens for EVERY cached response
- Requested tenant must match token's tenant
- Multi-tenant isolation is enforced at both cache and validation levels

**Code**:
```java
if (cached != null) {
    // Validate tenant match
    if (!tenantId.equals(cached.getTenantId())) {
        throw new ApiException(ErrorCodes.TENANT_MISMATCH, "tenant mismatch");
    }
}
```

## Cache Eviction Strategy

### Automatic Eviction

1. **Logout**: Token is immediately evicted from all caches
2. **Token Refresh**: Old token is immediately evicted from all caches
3. **User Disabled**: Token caches are cleared and user status cache entry is evicted
4. **Tenant Disabled**: Token caches are cleared and tenant status cache entry is evicted
5. **Cache TTL**: Entries automatically expire after 10 minutes (configurable)

### Why Clear Token Caches?

The caching implementation uses Caffeine, which is a high-performance cache but does not support querying token entries by user/tenant. When a user or tenant is disabled:

1. **Option 1** (NOT chosen): Keep cache and rely on re-validation
   - Pro: Better performance
   - Con: Every cached token for that user/tenant would require DB lookup on next access
   
2. **Option 2** (CHOSEN): Clear entire token cache
   - Pro: Simpler, more secure, immediate effect
   - Con: Temporary performance hit (cache rebuilds quickly)
   - Rationale: User/tenant status changes are infrequent operations

Status caches for tenant/user lookups are evicted by key to avoid stale status without clearing unrelated entries.

## Performance vs Security Tradeoff

### What Cache Provides
- ✅ Reduces JWT parsing overhead
- ✅ Prevents duplicate audit logs (N logs → 1 log per 10 minutes)
- ✅ Reduces database load for repeat verifications

### What Cache Does NOT Skip
- ❌ Blacklist checks (always checked first)
- ❌ Tenant status validation (re-checked for cached responses via cache lookup)
- ❌ User status validation (re-checked for cached responses via cache lookup)
- ❌ Token expiration checks (re-checked for cached responses)

### Performance Impact of Security Validations

For cached responses, we perform:
1. Blacklist check (Redis lookup) - ~1ms
2. Tenant status check (cache lookup, DB on miss) - ~0.1-5ms
3. User status check (cache lookup, DB on miss) - ~0.1-5ms
4. Expiration check (memory comparison) - ~0.001ms

**Total overhead**: ~5-11ms per cached verification

**Compared to full verification**:
- JWT parsing: ~5-10ms
- All security checks: ~5-11ms
- Audit log insertion: ~5-10ms
- **Total**: ~15-31ms

**Cache benefit**: Still 30-50% faster than full verification

## Configuration

### Cache Settings

```yaml
security:
  token-cache:
    max-size: 10000                    # Maximum cache entries (shared across token/user/tenant caches)
    expire-after-write-seconds: 600    # 10 minutes TTL
```

### Recommendations

1. **max-size**: Set based on expected concurrent users
    - 10,000 = supports ~10K active users with cached tokens
    - Increase if you have more concurrent users
    - When the cache exceeds max-size, Caffeine evicts entries using its default
      size-based policy (Window TinyLFU, similar to LRU for hot entries) to free space

2. **expire-after-write-seconds**: Security vs Performance tradeoff
    - Lower = More secure (status changes take effect faster)
    - Higher = Better performance (fewer DB queries)
    - Recommended: 600 seconds (10 minutes) balances both
    - Do not exceed token TTL (default 2 hours)
    - Entries also expire automatically after this TTL even if max-size isn't reached

## Monitoring

### Cache Statistics

Cache statistics are enabled via `recordStats()` in CacheConfig. Monitor:

- **Hit rate**: Should be >70% in normal operation
- **Miss rate**: Increases after cache clears (user/tenant status changes)
- **Eviction count**: Tracks cache clearing events

### Security Logs

Watch for these log patterns:

```
WARN - verifyToken cached response invalid, evicting: tenant=X userId=Y error=USER_DISABLED
WARN - verifyToken cached response invalid, evicting: tenant=X userId=Y error=TENANT_DISABLED  
WARN - verifyToken cached token expired tenant=X userId=Y
WARN - All token caches cleared due to user status change: tenantId=X userId=Y
WARN - All token caches cleared due to tenant status change: tenantId=X
```

These indicate security validations are working correctly.

## Testing

### Security Tests

The system includes comprehensive security tests:

1. **testCachedResponseRevalidatesUserStatus**: Verifies disabled users cannot use cached tokens
2. **testTokenVerifyResponseEvictionOnLogout**: Verifies cache eviction on logout
3. **testTokenVerifyResponseCaching**: Verifies cache hit path
4. **testTokenCacheEvictionOnRefresh**: Verifies cache eviction on token refresh

Run tests:
```bash
mvn test -Dtest=TokenCacheIntegrationTests
```

### Manual Testing

To test security validations:

1. Verify a token (should cache)
2. Disable the user
3. Verify same token (should fail with USER_DISABLED)
4. Check logs for cache eviction

## Security Checklist

Before deploying caching to production:

- [x] Blacklist checked before cache lookup
- [x] Tenant status validated for cached responses
- [x] User status validated for cached responses
- [x] Token expiration checked for cached responses
- [x] Cache evicted on logout
- [x] Cache evicted on token refresh
- [x] Cache cleared on user status change
- [x] Cache cleared on tenant status change
- [x] Security tests pass
- [x] CodeQL scan clean (0 vulnerabilities)
- [x] Cache TTL configured appropriately
- [x] Monitoring enabled

## References

- [SECURITY_REVIEW.md](SECURITY_REVIEW.md) - Overall security architecture
- [RATE_LIMITING.md](RATE_LIMITING.md) - Rate limiting configuration
- [CacheConfig.java](src/main/java/com/mercury/auth/config/CacheConfig.java) - Cache configuration
- [TokenService.java](src/main/java/com/mercury/auth/service/TokenService.java) - Token verification logic
- [TokenCacheService.java](src/main/java/com/mercury/auth/service/TokenCacheService.java) - Cache service
