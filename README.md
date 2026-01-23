# mercury-auth

## Overview
Spring Boot multi-tenant authentication service with comprehensive security features including JWT authentication, multi-layer rate limiting, OTP protection, email/phone verification, token blacklist, audit logging, and tenant/user management.

## Security Features

### JWT Authentication & Authorization
- **JWT Filter**: All protected endpoints require valid JWT ****** in Authorization header
- **Signature Verification**: HS256 signature validation
- **Expiration Check**: Automatic token expiration enforcement
- **JTI Blacklist**: Unique token IDs for distributed revocation tracking
- **Multi-Tenant Isolation**: Mandatory X-Tenant-Id header validation
  - Every authenticated request must include `X-Tenant-Id` header
  - Header value must match the tenant ID in the JWT token
  - Prevents cross-tenant data access

### Multi-Layer Rate Limiting
- **IP-Based Protection** (50 req/min per IP, configurable)
  - Prevents distributed attacks from multiple sources
  - Applied to all public endpoints
- **Identifier-Based Protection** (10 req/min per account/email/phone, configurable)
  - Prevents abuse from single account
  - Tracks by username, email, or phone number per tenant
- **Token Refresh Protection**
  - Both IP and per-user rate limiting applied
  - Prevents token refresh abuse

### OTP (One-Time Password) Protection
- **Cooldown Period**: 60-second minimum between code requests (prevents spam)
- **Failed Attempt Limiting**: 5 maximum verification attempts before lockout (prevents brute force)
- **Daily Request Cap**: 20 code requests maximum per day per identifier (prevents abuse)
- **Account Enumeration Prevention**: Consistent responses regardless of account existence

### Protected Endpoints
The following endpoints require JWT authentication with X-Tenant-Id header:
- `/api/auth/logout` - Blacklist current token
- `/api/auth/change-password` - Change user password
- `/api/auth/user-status` - Update user status
- `/api/tenants/**` - All tenant management operations

### Public Endpoints (Rate Limited)
The following endpoints don't require authentication but are rate limited:
- `/api/auth/login-**` - All login endpoints
- `/api/auth/register-**` - All registration endpoints
- `/api/auth/send-**` - Verification code sending
- `/api/auth/refresh-token` - Token refresh
- `/api/auth/verify-token` - Token verification
- `/api/auth/captcha` - Captcha generation

## Features
- Multi-tenant registration/login with JWT issuance and refresh
- Email/SMS verification codes (one-time, Redis-backed)
- Token verification + blacklist (Redis + database)
- Audit logs persisted in `auth_logs` with IP tracking
- Tenant management (create/list/enable/disable)
- User management (status updates, password change)
- Rate limiting and captcha gating after repeated failures
- Comprehensive security logging

## Documentation
- [Rate Limiting Guide](RATE_LIMITING.md) - Detailed guide on rate limiting configuration and usage
- [Identifier Field Guide](IDENTIFIER_GUIDE.md) - Explanation of the identifier field usage in authentication (中英文)
- [Requirements](REQUIREMENTS.md) - Full system requirements (中文)

## Configuration
Key properties (see `application.yml`, with overrides in `application-dev.yml`, `application-test.yml`, `application-prod.yml`):

### JWT Configuration
- `security.jwt.secret` - JWT signing secret (min 32 bytes, use environment variable in production)
- `security.jwt.ttl-seconds` - Token expiration time (default: 7200 = 2 hours)

### OTP Protection Configuration
- `security.code.ttl-minutes` - Email verification code validity (default: 10 minutes)
- `security.code.phone-ttl-minutes` - SMS verification code validity (default: 5 minutes)
- `security.code.cooldown-seconds` - Minimum time between code requests (default: 60 seconds)
- `security.code.max-daily-requests` - Maximum code requests per day (default: 20)
- `security.code.max-verify-attempts` - Maximum verification attempts (default: 5)

### Rate Limiting Configuration
- `security.rate-limit.max-attempts` - Per-identifier rate limit (default: 10 requests)
- `security.rate-limit.window-minutes` - Rate limit time window (default: 1 minute)
- `security.rate-limit.ip.max-attempts` - Per-IP rate limit (default: 50 requests)
- `security.rate-limit.ip.window-minutes` - IP rate limit time window (default: 1 minute)

### Captcha Configuration
- `security.captcha.threshold` - Failed attempts before captcha required (default: 3)
- `security.captcha.ttl-minutes` - Captcha validity period (default: 5 minutes)

## Database
Initialize tables from `src/main/resources/schema.sql` (includes `users`, `tenants`, `auth_logs`, `token_blacklist`).

## Running
```bash
mvn spring-boot:run
```

## API Usage

### Authentication Flow

#### 1. Login and Get JWT Token (Public Endpoint)
```bash
# Password login - tenantId REQUIRED in body (no JWT token yet)
curl -X POST http://localhost:10000/auth/api/auth/login-password \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant1",
    "username": "user1",
    "password": "password123"
  }'

# Response includes JWT token
{
  "accessToken": "eyJhbGc...",
  "expiresInSeconds": 7200
}
```

#### 2. Use JWT Token for Protected Endpoints
**IMPORTANT**: Protected endpoints require:
- `Authorization: Bearer <token>` header
- `X-Tenant-Id: <tenantId>` header (must match JWT token's tenant)
- **tenantId in request body is OPTIONAL** - if included, it will be overwritten by header value
- **RECOMMENDED**: Omit tenantId from request body for protected endpoints

```bash
# Logout (blacklist token) - tenantId automatically injected from header
curl -X POST http://localhost:10000/auth/api/auth/logout \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "X-Tenant-Id: tenant1" \
  -H "Content-Type: application/json" \
  -d '{
    "token": "eyJhbGc..."
  }'

# Update user status - tenantId automatically injected from header
curl -X POST http://localhost:10000/auth/api/auth/user-status \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "X-Tenant-Id: tenant1" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user1",
    "enabled": false
  }'

# If you include tenantId in body, it will be IGNORED and overwritten
curl -X POST http://localhost:10000/auth/api/auth/logout \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "X-Tenant-Id: tenant1" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "any-value-will-be-replaced",
    "token": "eyJhbGc..."
  }'
```

**Security Notes**: 
- **Public endpoints** (login, register): tenantId required in request body
- **Protected endpoints** (logout, user management): X-Tenant-Id header required, body tenantId optional/ignored
- X-Tenant-Id header must match the tenant ID in your JWT token
- Any tenantId in request body for protected endpoints is **overwritten** by header value
- Missing header on protected endpoint → HTTP 400 MISSING_TENANT_HEADER
- Mismatched header → HTTP 403 TENANT_MISMATCH

#### 3. Token Refresh (Public Endpoint)
```bash
# tenantId required in body (no X-Tenant-Id header needed)
curl -X POST http://localhost:10000/auth/api/auth/refresh-token \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant1",
    "token": "eyJhbGc..."
  }'
```

#### 4. Token Verification
```bash
curl -X POST http://localhost:10000/auth/api/auth/verify-token \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant1",
    "token": "eyJhbGc..."
  }'
```

### Rate Limiting Behavior
- When rate limit is exceeded, API returns `RATE_LIMITED` error (HTTP 429)
- Rate limits reset after the configured time window
- Both IP-based and identifier-based limits are active simultaneously

### OTP Security
- Verification codes expire after the configured TTL
- After 5 failed verification attempts, you must request a new code
- Maximum 20 code requests per day per identifier
- Minimum 60 seconds between code requests

### Error Responses
Error responses use symbolic `code` values:
```json
{
  "code": "RATE_LIMITED",
  "message": "Too many requests",
  "timestamp": "2026-01-23T09:00:00Z"
}
```

Common error codes:
- `INVALID_TOKEN` - JWT token is invalid or expired
- `TOKEN_BLACKLISTED` - Token has been revoked
- `TENANT_MISMATCH` - X-Tenant-Id header doesn't match JWT token
- `MISSING_TENANT_HEADER` - X-Tenant-Id header is required for protected endpoints
- `RATE_LIMITED` - Too many requests (HTTP 429)
- `CAPTCHA_REQUIRED` - Captcha verification required
- `INVALID_CODE` - Verification code is incorrect or expired

### Captcha Flow
Captcha is required only after repeated failed login attempts (password/email/phone). 

1. When API returns code `CAPTCHA_REQUIRED`, request captcha:
```bash
curl -X POST http://localhost:10000/auth/api/auth/captcha \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant1",
    "identifier": "user1",
    "action": "CAPTCHA_LOGIN_PASSWORD"
  }'
```

2. Solve the captcha and retry login with `captchaId` + `captcha` fields:
```bash
curl -X POST http://localhost:10000/auth/api/auth/login-password \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant1",
    "username": "user1",
    "password": "password123",
    "captchaId": "abc123",
    "captcha": "42"
  }'
```

## Production Deployment

### Security Checklist
- ✅ Set strong JWT secret via `JWT_SECRET` environment variable (min 32 bytes)
- ✅ Use HTTPS/TLS for all communications
- ✅ Configure appropriate rate limits based on your traffic patterns
- ✅ Enable audit logging and monitor for suspicious activity
- ✅ Regularly rotate JWT secrets
- ✅ Configure Redis persistence for rate limiting and blacklist data
- ✅ Set up database backups for user data and audit logs

### Environment Variables (Production)
```bash
# JWT Configuration
JWT_SECRET=your-strong-secret-min-32-bytes

# Database
DB_URL=jdbc:mysql://db-host:3306/mercury_auth
DB_USERNAME=app_user
DB_PASSWORD=secure_password

# Redis
REDIS_HOST=redis-host
REDIS_PORT=6379

# Rate Limiting (optional, defaults shown)
RATE_LIMIT_MAX_ATTEMPTS=10
RATE_LIMIT_WINDOW_MINUTES=1
RATE_LIMIT_IP_MAX_ATTEMPTS=50
RATE_LIMIT_IP_WINDOW_MINUTES=1

# OTP Protection (optional, defaults shown)
CODE_COOLDOWN_SECONDS=60
CODE_MAX_DAILY_REQUESTS=20
CODE_MAX_VERIFY_ATTEMPTS=5

# Email Configuration
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=noreply@example.com
MAIL_PASSWORD=mail_password
MAIL_FROM=noreply@example.com
```
