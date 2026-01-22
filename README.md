# mercury-auth

## Overview
Spring Boot multi-tenant auth service with JWT, email/phone verification, token blacklist, audit logging, and tenant/user management.

## Features
- Multi-tenant registration/login with JWT issuance and refresh
- Email/SMS verification codes (one-time, Redis-backed)
- Token verification + blacklist (Redis + database)
- Audit logs persisted in `auth_logs`
- Tenant management (create/list/enable/disable)
- User management (status updates, password change)
- Rate limiting and captcha gating after repeated failures

## Configuration
Key properties (see `application.yml`, with overrides in `application-dev.yml`, `application-test.yml`, `application-prod.yml`):
- `security.jwt.secret`, `security.jwt.ttl-seconds`
- `security.code.ttl-minutes`, `security.code.phone-ttl-minutes`
- `security.rate-limit.max-attempts`, `security.rate-limit.window-minutes`
- `security.captcha.threshold`, `security.captcha.ttl-minutes`

## Database
Initialize tables from `src/main/resources/schema.sql` (includes `users`, `tenants`, `auth_logs`, `token_blacklist`).

## Running
```bash
mvn spring-boot:run
```

## API Notes
- Use `/api/auth/refresh-token` to refresh tokens.
- Use `/api/auth/verify-token` to validate tokens.
- Use `/api/auth/logout` to blacklist tokens.
- Tenant APIs under `/api/tenants`.
- Error responses use symbolic `code` values, and the same value is returned in `message`.
- Captcha is required only after repeated failed login attempts (password/email/phone). When the API returns code `CAPTCHA_REQUIRED`, request `/api/auth/captcha` with `tenantId`, `identifier`, and `action` (`CAPTCHA_LOGIN_PASSWORD`, `CAPTCHA_LOGIN_EMAIL`, `CAPTCHA_LOGIN_PHONE`), then resend the login request with `captchaId` + `captcha` (answer). Threshold and TTL are configured via `security.captcha.threshold` and `security.captcha.ttl-minutes`.
- **Rate Limiting**: All authentication operations (login, sending verification codes, captcha generation) are rate limited per user to prevent abuse. Configure via `security.rate-limit.max-attempts` (default: 10) and `security.rate-limit.window-minutes` (default: 1 minute). When limit is exceeded, API returns `RATE_LIMITED` error (HTTP 429).
- **Identifier Field**: The `identifier` field in captcha and rate limiting operations represents the user identifier (username, email, or phone) and is used to track requests per user within a tenant. This ensures rate limiting is applied per-user rather than globally.
