# System Security Configuration Guide

## Overview

This document provides a comprehensive guide to configuring the Mercury Auth system's security features, including rate limiting, blacklist, and token cache. These features are designed to work independently while providing coordinated protection.

## Configuration Architecture

The security system consists of three independent but complementary layers:

1. **Rate Limiting** - Temporary request throttling
2. **Blacklist** - Persistent IP and token blocking
3. **Auto-Blacklist** - Automatic IP blocking based on behavior analysis

Each layer can be independently enabled/disabled and configured with custom parameters.

---

## Token Cache Configuration

Token verification caching improves performance by reducing repeated JWT parsing and validation.

### Configuration Parameters

```yaml
security:
  token-cache:
    max-size: 10000  # Maximum tokens to cache
    expire-after-write-seconds: 600  # Cache TTL (10 minutes)
```

### Environment Variables (Production)

```bash
TOKEN_CACHE_MAX_SIZE=10000
TOKEN_CACHE_EXPIRE_SECONDS=600
```

### Key Features

- **Cache Duration**: 10 minutes (increased from 5 minutes for better performance)
- **Cache Strategy**: Write-through cache with automatic eviction
- **Security**: Blacklist checks always performed before cache lookup
- **Tenant Isolation**: Cached tokens include tenant validation

### When Cache is Evicted

1. Token is blacklisted (logout/refresh)
2. User status changes (disabled/enabled)
3. Tenant status changes
4. TTL expires (10 minutes)

---

## Rate Limiting Configuration

Rate limiting provides **temporary throttling** to prevent abuse while allowing legitimate users.

### Core Principles

- **Short-term protection**: 1-minute time windows
- **Independent counters**: Each operation type has separate limits
- **No persistent blocking**: Resets after time window expires
- **User-level tracking**: Based on tenant + identifier combination

### Configuration Structure

```yaml
security:
  rate-limit:
    # Default settings (backward compatibility)
    max-attempts: 10
    window-minutes: 1
    
    # IP-based rate limiting (additional layer)
    ip:
      max-attempts: 50
      window-minutes: 1
    
    # Verification code sending (restrictive)
    send-code:
      max-attempts: 5
      window-minutes: 1
    
    # Login operations (balanced)
    login:
      max-attempts: 10
      window-minutes: 1
    
    # Captcha generation (permissive)
    captcha:
      max-attempts: 20
      window-minutes: 1
    
    # Token refresh
    refresh-token:
      max-attempts: 10
      window-minutes: 1
```

### Environment Variables (Production)

```bash
# IP Rate Limiting
RATE_LIMIT_IP_MAX_ATTEMPTS=50
RATE_LIMIT_IP_WINDOW_MINUTES=1

# Code Sending
RATE_LIMIT_SEND_CODE_MAX_ATTEMPTS=5
RATE_LIMIT_SEND_CODE_WINDOW_MINUTES=1

# Login Operations
RATE_LIMIT_LOGIN_MAX_ATTEMPTS=10
RATE_LIMIT_LOGIN_WINDOW_MINUTES=1

# Captcha Generation
RATE_LIMIT_CAPTCHA_MAX_ATTEMPTS=20
RATE_LIMIT_CAPTCHA_WINDOW_MINUTES=1

# Token Refresh
RATE_LIMIT_REFRESH_TOKEN_MAX_ATTEMPTS=10
RATE_LIMIT_REFRESH_TOKEN_WINDOW_MINUTES=1
```

### Operation Coverage

Rate limiting is applied to all core processes:

- ✅ Email verification code sending
- ✅ Phone verification code sending
- ✅ Password login
- ✅ Email login
- ✅ Phone login
- ✅ Quick login (phone)
- ✅ Captcha generation
- ✅ Token refresh

---

## Blacklist Configuration

Blacklist provides **persistent blocking** with fine-grained control over IP and token blocking.

### Core Principles

- **Long-term protection**: Minutes to days of blocking
- **Independent from rate limiting**: Can be enabled/disabled separately
- **Dual storage**: Redis (fast) + MySQL (persistent)
- **Configurable switches**: Enable/disable IP and token blacklisting independently

### Configuration Structure

```yaml
security:
  blacklist:
    # Enable/disable blacklist features
    ip-enabled: true       # Control IP blacklist checking
    token-enabled: true    # Control token blacklist checking
    
    # Cache settings
    permanent-blacklist-cache-days: 365  # Redis cache duration for permanent blocks
```

### Environment Variables (Production)

```bash
BLACKLIST_IP_ENABLED=true           # Enable IP blacklist
BLACKLIST_TOKEN_ENABLED=true        # Enable token blacklist
BLACKLIST_PERMANENT_CACHE_DAYS=365  # Cache duration for permanent blocks
```

### IP Blacklist Types

1. **Global Blacklist**: Blocks IP across all tenants
2. **Tenant-Specific Blacklist**: Blocks IP for specific tenant only

### Token Blacklist

Automatically triggered by:
- User logout
- Token refresh (old token blacklisted)
- Admin action (manual blacklist)

### Blacklist Check Points

IP blacklist is checked at:
- ✅ JwtAuthenticationFilter (all authenticated requests)
- ✅ Auto-blacklist system (failed login attempts)

Token blacklist is checked at:
- ✅ Token verification
- ✅ Token refresh
- ✅ Protected endpoints

---

## Auto-Blacklist Configuration

Auto-blacklist provides **behavior-based automatic IP blocking** triggered by repeated violations.

### Core Principles

- **Behavior analysis**: Tracks failed login attempts over time
- **Graduated response**: Different thresholds for normal vs. severe violations
- **Independent from rate limiting**: Longer-term protection layer
- **Configurable**: All thresholds and durations can be adjusted

### Configuration Structure

```yaml
security:
  rate-limit:
    auto-blacklist:
      # Enable/disable auto-blacklist
      enabled: true
      
      # Normal violation (lighter punishment)
      failure-threshold: 20              # Failed attempts to trigger
      failure-window-minutes: 5          # Time window to count failures
      blacklist-duration-minutes: 30     # How long to blacklist
      
      # Severe violation (heavier punishment)
      severe-failure-threshold: 50       # Failed attempts for severe violation
      severe-failure-window-minutes: 10  # Time window for severe violations
      severe-blacklist-duration-minutes: 120  # Blacklist duration for severe
```

### Environment Variables (Production)

```bash
# Auto-blacklist enable/disable
AUTO_BLACKLIST_ENABLED=true

# Normal violation settings
AUTO_BLACKLIST_FAILURE_THRESHOLD=20
AUTO_BLACKLIST_FAILURE_WINDOW_MINUTES=5
AUTO_BLACKLIST_DURATION_MINUTES=30

# Severe violation settings
AUTO_BLACKLIST_SEVERE_FAILURE_THRESHOLD=50
AUTO_BLACKLIST_SEVERE_FAILURE_WINDOW_MINUTES=10
AUTO_BLACKLIST_SEVERE_DURATION_MINUTES=120
```

### How It Works

```
Timeline Example:
─────────────────────────────────────────────────────
0 min              5 min             10 min
│                  │                 │
├──────────────────┤                 │
│  20 failures     │                 │
│  → 30 min ban    │                 │
│                  │                 │
└──────────────────┴─────────────────┤
   50 failures in this window        │
   → 120 min ban                     │
                                     │
```

### Trigger Scenarios

1. **Normal Violation**: 20 failed logins in 5 minutes → 30 minute blacklist
2. **Severe Violation**: 50 failed logins in 10 minutes → 2 hour blacklist

### Auto-Blacklist Coverage

Currently triggered by:
- ✅ Password login failures
- ✅ Email login failures
- ✅ Phone login failures

---

## Independence and Isolation

The three systems are designed to be **completely independent**:

### Rate Limiting vs. Auto-Blacklist

| Feature | Rate Limiting | Auto-Blacklist |
|---------|--------------|----------------|
| **Purpose** | Temporary throttling | Long-term blocking |
| **Duration** | 1 minute windows | 30-120 minutes |
| **Storage** | Redis only | Redis + MySQL |
| **Configuration** | `security.rate-limit.*` | `security.rate-limit.auto-blacklist.*` |
| **Can Disable** | Change thresholds to very high values | Set `enabled: false` |

### Blacklist vs. Rate Limiting

| Feature | Blacklist | Rate Limiting |
|---------|-----------|---------------|
| **Purpose** | Persistent blocking | Temporary throttling |
| **Trigger** | Manual or auto-blacklist | Every request |
| **Storage** | Redis + MySQL | Redis only |
| **Configuration** | `security.blacklist.*` | `security.rate-limit.*` |
| **Can Disable** | Set `ip-enabled/token-enabled: false` | N/A (always active) |

### System Interaction

```
Request → IP Blacklist Check (if enabled)
              ↓ pass
          Rate Limit Check
              ↓ pass
          Token Blacklist Check (if enabled)
              ↓ pass
          Business Logic
```

**Key Point**: Blacklist checks are **independent** and happen **before** rate limiting affects the request count.

---

## Configuration Examples

### Development Environment

```yaml
# application-dev.yml
security:
  token-cache:
    expire-after-write-seconds: 600  # 10 minutes
  
  rate-limit:
    login:
      max-attempts: 10      # Allow more attempts for testing
      window-minutes: 1
    auto-blacklist:
      enabled: true
      failure-threshold: 20
  
  blacklist:
    ip-enabled: true
    token-enabled: true
```

### Production Environment (Strict)

```yaml
# application-prod.yml
security:
  token-cache:
    expire-after-write-seconds: ${TOKEN_CACHE_EXPIRE_SECONDS:600}
  
  rate-limit:
    login:
      max-attempts: ${RATE_LIMIT_LOGIN_MAX_ATTEMPTS:5}  # Stricter
      window-minutes: ${RATE_LIMIT_LOGIN_WINDOW_MINUTES:1}
    auto-blacklist:
      enabled: ${AUTO_BLACKLIST_ENABLED:true}
      failure-threshold: ${AUTO_BLACKLIST_FAILURE_THRESHOLD:10}  # Stricter
      blacklist-duration-minutes: ${AUTO_BLACKLIST_DURATION_MINUTES:60}  # Longer
  
  blacklist:
    ip-enabled: ${BLACKLIST_IP_ENABLED:true}
    token-enabled: ${BLACKLIST_TOKEN_ENABLED:true}
```

### Test Environment (Permissive)

```yaml
# application-test.yml
security:
  token-cache:
    expire-after-write-seconds: 600
  
  rate-limit:
    login:
      max-attempts: 100     # Very high for testing
      window-minutes: 1
    auto-blacklist:
      enabled: false        # Disable for testing
  
  blacklist:
    ip-enabled: true
    token-enabled: true
```

---

## Tuning Recommendations

### Based on Traffic Patterns

**Low Traffic (< 100 users)**
```yaml
rate-limit:
  login:
    max-attempts: 10
    window-minutes: 1
  auto-blacklist:
    enabled: true
    failure-threshold: 30
    blacklist-duration-minutes: 15
```

**Medium Traffic (100-1000 users)**
```yaml
rate-limit:
  login:
    max-attempts: 10
    window-minutes: 1
  auto-blacklist:
    enabled: true
    failure-threshold: 20
    blacklist-duration-minutes: 30
```

**High Traffic (1000+ users)**
```yaml
rate-limit:
  login:
    max-attempts: 15
    window-minutes: 1
  auto-blacklist:
    enabled: true
    failure-threshold: 50
    blacklist-duration-minutes: 60
```

### Based on Security Requirements

**High Security**
- Lower rate limits
- Lower auto-blacklist thresholds
- Longer blacklist durations
- Enable all blacklist features

**Balanced Security**
- Standard rate limits (defaults)
- Moderate auto-blacklist thresholds
- Moderate blacklist durations

**User-Friendly**
- Higher rate limits
- Higher auto-blacklist thresholds
- Shorter blacklist durations

---

## Monitoring and Alerts

### Key Metrics to Monitor

1. **Rate Limit Hit Rate**
   - Redis key pattern: `rate:*`
   - Alert if > 5% of requests are rate limited

2. **Auto-Blacklist Trigger Rate**
   - Log entries: "Auto-blacklist triggered"
   - Alert if > 10 IPs per hour

3. **Blacklist Size**
   - Redis keys: `blacklist:ip:*`
   - MySQL table: `ip_blacklist`
   - Alert if > 1000 active entries

4. **Token Cache Hit Rate**
   - Cache statistics: CacheManager metrics
   - Target: > 70% hit rate

### Recommended Alerts

```yaml
alerts:
  - name: High Rate Limit Hits
    condition: rate_limit_rejections > 100/min
    action: Notify security team
  
  - name: Mass Auto-Blacklist
    condition: auto_blacklist_count > 50/hour
    action: Investigate potential DDoS
  
  - name: Large Blacklist Size
    condition: blacklist_entries > 5000
    action: Review and clean up old entries
```

---

## Troubleshooting

### Problem: Legitimate Users Being Blocked

**Symptoms**: Users report they can't log in despite correct credentials

**Check**:
1. Review blacklist entries: `GET /api/v1/admin/blacklist/ip?tenantId=<tenant>`
2. Check rate limit counters: `redis-cli KEYS "rate:*"`
3. Review auto-blacklist logs

**Solution**:
- Remove from blacklist: `DELETE /api/v1/admin/blacklist/ip?ipAddress=<ip>`
- Adjust auto-blacklist thresholds
- Increase rate limits if needed

### Problem: System Under Attack

**Symptoms**: High rate limit hits, many auto-blacklist triggers

**Immediate Actions**:
1. Enable all blacklist features if not already enabled
2. Lower rate limit thresholds temporarily
3. Lower auto-blacklist thresholds
4. Review and manually blacklist attacking IPs

**Configuration Changes**:
```yaml
rate-limit:
  login:
    max-attempts: 3  # Temporary strict limit
auto-blacklist:
  failure-threshold: 5  # Very strict
  blacklist-duration-minutes: 120  # Longer blocks
```

### Problem: Configuration Not Taking Effect

**Check**:
1. Verify configuration file is loaded: Check startup logs
2. Check environment variables override
3. Restart application to reload configuration
4. Verify Redis connectivity

---

## Security Best Practices

1. **Use Environment Variables in Production**
   - Never commit secrets to version control
   - Use environment-specific configurations

2. **Regular Blacklist Cleanup**
   - Schedule cleanup job: `DELETE /api/v1/admin/blacklist/ip/cleanup`
   - Remove expired entries to maintain performance

3. **Monitor and Tune**
   - Regularly review metrics
   - Adjust thresholds based on actual traffic patterns
   - Balance security with user experience

4. **Defense in Depth**
   - Don't rely on a single security layer
   - Use all three systems together
   - Combine with other security measures (firewall, WAF, etc.)

5. **Document Changes**
   - Keep track of configuration changes
   - Document reasons for tuning decisions
   - Review security policies periodically

---

## Summary

The Mercury Auth security system provides three independent, configurable layers:

- **Token Cache**: 10-minute caching for performance optimization
- **Rate Limiting**: Temporary throttling with operation-specific limits
- **Blacklist**: Persistent IP and token blocking with enable/disable switches
- **Auto-Blacklist**: Behavior-based automatic IP blocking

All systems can be independently configured via:
- Configuration files (application-{env}.yml)
- Environment variables (production recommended)
- Runtime adjustments (blacklist management APIs)

This architecture ensures maximum flexibility while maintaining security and performance.
