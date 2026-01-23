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
- **User Groups**: Organize users into groups for role-based access (e.g., VIP, Premium members)
- **Permission System**: Fine-grained permission control for menus, APIs, data, and buttons

## Documentation
- [Rate Limiting Guide](RATE_LIMITING.md) - Detailed guide on rate limiting configuration and usage
- [Identifier Field Guide](IDENTIFIER_GUIDE.md) - Explanation of the identifier field usage in authentication (中英文)
- [Requirements](REQUIREMENTS.md) - Full system requirements (中文)

## Configuration
Key properties (see `application.yml`, with overrides in `application-dev.yml`, `application-test.yml`, `application-prod.yml`):
- `security.jwt.secret`, `security.jwt.ttl-seconds`
- `security.code.ttl-minutes`, `security.code.phone-ttl-minutes`
- `security.rate-limit.max-attempts`, `security.rate-limit.window-minutes`
- `security.captcha.threshold`, `security.captcha.ttl-minutes`

## Database
Initialize tables from `src/main/resources/schema.sql` (includes `users`, `tenants`, `auth_logs`, `token_blacklist`, `user_groups`, `user_group_members`, `permissions`, `user_group_permissions`).

## Running
```bash
mvn spring-boot:run
```

## API Notes
- Use `/api/auth/refresh-token` to refresh tokens.
- Use `/api/auth/verify-token` to validate tokens.
- Use `/api/auth/logout` to blacklist tokens.
- Tenant APIs under `/api/tenants`.
- User group APIs under `/api/user-groups`.
- Permission APIs under `/api/permissions`.
- Error responses use symbolic `code` values, and the same value is returned in `message`.
- Captcha is required only after repeated failed login attempts (password/email/phone). When the API returns code `CAPTCHA_REQUIRED`, request `/api/auth/captcha` with `tenantId`, `identifier`, and `action` (`CAPTCHA_LOGIN_PASSWORD`, `CAPTCHA_LOGIN_EMAIL`, `CAPTCHA_LOGIN_PHONE`), then resend the login request with `captchaId` + `captcha` (answer). Threshold and TTL are configured via `security.captcha.threshold` and `security.captcha.ttl-minutes`.
- **Rate Limiting**: All authentication operations (login, sending verification codes, captcha generation) are rate limited per user to prevent abuse. Configure via `security.rate-limit.max-attempts` (default: 10) and `security.rate-limit.window-minutes` (default: 1 minute). When limit is exceeded, API returns `RATE_LIMITED` error (HTTP 429).
- **Identifier Field**: The `identifier` field in captcha and rate limiting operations represents the user identifier (username, email, or phone) and is used to track requests per user within a tenant. This ensures rate limiting is applied per-user rather than globally.

## User Group & Permission Management

### User Groups
User groups allow organizing users into categories (e.g., VIP, Premium, Free tier) for role-based access control. A user can belong to multiple groups.

**API Endpoints:**
- `POST /api/user-groups/create` - Create a new user group
- `POST /api/user-groups/update` - Update group details
- `GET /api/user-groups/list?tenantId={id}` - List all groups for a tenant
- `GET /api/user-groups/{groupId}` - Get group details
- `POST /api/user-groups/add-user` - Add user to group
- `POST /api/user-groups/remove-user` - Remove user from group
- `GET /api/user-groups/user/{userId}` - Get all groups for a user
- `GET /api/user-groups/{groupId}/members` - Get all members of a group

### Permissions
Permissions define access rights for different types of resources (MENU, API, DATA, BUTTON). Permissions are assigned to user groups, and users inherit permissions from their groups.

**API Endpoints:**
- `POST /api/permissions/create` - Create a new permission
- `POST /api/permissions/update` - Update permission details
- `GET /api/permissions/list?tenantId={id}` - List all permissions for a tenant
- `GET /api/permissions/{permissionId}` - Get permission details
- `POST /api/permissions/assign-to-group` - Assign permissions to a group
- `GET /api/permissions/group/{groupId}` - Get all permissions for a group
- `GET /api/permissions/user/{userId}?tenantId={id}` - Get all permissions for a user (aggregated from all their groups)
- `POST /api/permissions/check` - Check if user has a specific permission

**Example: Create a Permission**
```json
POST /api/permissions/create
{
  "tenantId": "tenant1",
  "code": "VIEW_DASHBOARD",
  "name": "View Dashboard",
  "type": "MENU",
  "resource": "/dashboard",
  "description": "Access to view the dashboard"
}
```

**Example: Check Permission**
```json
POST /api/permissions/check
{
  "userId": 100,
  "permissionCode": "VIEW_DASHBOARD",
  "tenantId": "tenant1"
}
// Response: {"hasPermission": true}
```

### Permission Types
- **MENU**: Controls access to menu items in UI
- **API**: Controls access to API endpoints
- **DATA**: Controls access to specific data resources
- **BUTTON**: Controls visibility/access to UI buttons/actions
