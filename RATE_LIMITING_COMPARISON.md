# Rate Limiting: Before vs After

## Before (Problematic)

All operations shared a SINGLE rate limit counter:

```
┌─────────────────────────────────────────┐
│    SHARED RATE LIMIT: 10 requests/min  │
│    Key: rate:{ACTION}:{tenant}:{user}  │
└─────────────────────────────────────────┘
                    │
                    ├─── Login (Password)
                    ├─── Login (Email)
                    ├─── Login (Phone)
                    ├─── Send Email Code
                    ├─── Send Phone Code
                    ├─── Generate Captcha
                    └─── Refresh Token
```

### Problems:
1. ❌ Captcha generation counts against login attempts
2. ❌ First-time verification code requests fail ("operation too frequent")
3. ❌ No differentiation between high-risk and low-risk operations
4. ❌ Poor user experience when solving captchas

### Example Failure Scenario:
```
Time 00:00 - User attempts login (fails) - Count: 1/10
Time 00:10 - User attempts login (fails) - Count: 2/10
Time 00:20 - User attempts login (fails) - Count: 3/10
             → System requires CAPTCHA
Time 00:25 - User requests captcha - Count: 4/10
Time 00:30 - Can't read captcha, requests new one - Count: 5/10
Time 00:35 - Still can't read, requests another - Count: 6/10
Time 00:40 - Finally readable, attempts login - Count: 7/10
Time 00:45 - Typo in captcha, tries again - Count: 8/10
Time 00:50 - Another attempt - Count: 9/10
Time 00:55 - Another attempt - Count: 10/10
Time 01:00 - User tries one more time - RATE_LIMITED! ❌
```

## After (Fixed)

Each operation type has INDEPENDENT rate limit counters:

```
┌─────────────────────────────────────────┐
│  VERIFICATION CODE: 5 requests/min      │ ← More Restrictive
│  Key: rate:SEND_CODE:{tenant}:{user}   │
└─────────────────────────────────────────┘
            │
            ├─── Send Email Code
            └─── Send Phone Code

┌─────────────────────────────────────────┐
│  LOGIN OPERATIONS: 10 requests/min      │ ← Standard Security
│  Key: rate:LOGIN:{tenant}:{user}       │
└─────────────────────────────────────────┘
            │
            ├─── Login (Password)
            ├─── Login (Email)
            └─── Login (Phone)

┌─────────────────────────────────────────┐
│  CAPTCHA GENERATION: 20 requests/min    │ ← More Permissive
│  Key: rate:CAPTCHA:{tenant}:{user}     │
└─────────────────────────────────────────┘
            │
            ├─── Captcha (Login Password)
            ├─── Captcha (Login Email)
            └─── Captcha (Login Phone)

┌─────────────────────────────────────────┐
│  TOKEN REFRESH: 10 requests/min         │ ← Standard Security
│  Key: rate:REFRESH:{tenant}:{user}     │
└─────────────────────────────────────────┘
            │
            └─── Refresh Token
```

### Benefits:
1. ✅ Independent counters - operations don't interfere with each other
2. ✅ First-time verification codes work correctly
3. ✅ Risk-based limits: restrictive for codes, permissive for captchas
4. ✅ Better user experience

### Example Success Scenario:
```
Time 00:00 - User attempts login (fails) - Login: 1/10, Captcha: 0/20
Time 00:10 - User attempts login (fails) - Login: 2/10, Captcha: 0/20
Time 00:20 - User attempts login (fails) - Login: 3/10, Captcha: 0/20
             → System requires CAPTCHA
Time 00:25 - User requests captcha - Login: 3/10, Captcha: 1/20 ✅
Time 00:30 - Can't read, requests new one - Login: 3/10, Captcha: 2/20 ✅
Time 00:35 - Requests another - Login: 3/10, Captcha: 3/20 ✅
Time 00:40 - Finally readable, attempts login - Login: 4/10, Captcha: 3/20 ✅
Time 00:45 - Typo, tries again - Login: 5/10, Captcha: 3/20 ✅
Time 00:50 - Another attempt - Login: 6/10, Captcha: 3/20 ✅
Time 00:55 - Success! User logs in - Login: 7/10, Captcha: 3/20 ✅

User has plenty of room to:
- Continue logging in (3 more attempts)
- Request more captchas (17 more available)
```

## Rate Limit Matrix

| Operation | Before | After | Change | Rationale |
|-----------|--------|-------|--------|-----------|
| Send Email Code | 10/min (shared) | **5/min** (independent) | More restrictive | Prevent spam, reduce costs |
| Send Phone Code | 10/min (shared) | **5/min** (independent) | More restrictive | Prevent SMS spam, reduce costs |
| Login (Password) | 10/min (shared) | **10/min** (independent) | Same limit, independent | Standard security |
| Login (Email) | 10/min (shared) | **10/min** (independent) | Same limit, independent | Standard security |
| Login (Phone) | 10/min (shared) | **10/min** (independent) | Same limit, independent | Standard security |
| Captcha Generation | 10/min (shared) | **20/min** (independent) | More permissive | Users need multiple attempts |
| Token Refresh | 10/min (shared) | **10/min** (independent) | Same limit, independent | Standard security |

## Configuration

### Development (application-dev.yml)
```yaml
security:
  rate-limit:
    send-code:
      max-attempts: 5      # Restrictive for verification codes
      window-minutes: 1
    login:
      max-attempts: 10     # Standard for logins
      window-minutes: 1
    captcha:
      max-attempts: 20     # Permissive for captcha
      window-minutes: 1
    refresh-token:
      max-attempts: 10     # Standard for token refresh
      window-minutes: 1
```

### Production (Environment Variables)
```bash
RATE_LIMIT_SEND_CODE_MAX_ATTEMPTS=5
RATE_LIMIT_LOGIN_MAX_ATTEMPTS=10
RATE_LIMIT_CAPTCHA_MAX_ATTEMPTS=20
RATE_LIMIT_REFRESH_TOKEN_MAX_ATTEMPTS=10
```

## Impact on User Experience

### Scenario: First-time Email Registration

**Before:**
```
User: "Let me register with my email"
System: (RATE_LIMITED after 0 requests) ❌
User: "What? I haven't done anything yet!"
```

**After:**
```
User: "Let me register with my email"
System: (Email sent successfully - 1/5 attempts used) ✅
User: "Great! Got my verification code"
```

### Scenario: Login with Multiple Captcha Attempts

**Before:**
```
User fails login 3 times → Captcha required
User requests captcha: 4/10
User can't read it, requests again: 5/10
...continues requesting captchas...
User hits 10/10 limit → RATE_LIMITED ❌
User can't login anymore for 1 minute
```

**After:**
```
User fails login 3 times → Captcha required
User requests captcha: Login 3/10, Captcha 1/20
User can't read it, requests again: Login 3/10, Captcha 2/20
...continues requesting captchas (can request 20 total)...
User finally gets good captcha, logs in: Login 4/10, Captcha 7/20 ✅
Still has 6 login attempts and 13 captcha requests remaining
```
