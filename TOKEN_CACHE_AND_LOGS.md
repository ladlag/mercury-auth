# Token 验证缓存与日志/黑名单写入说明

## 1. Token 验证是否使用缓存

- **缓存位置**：
  - `JwtAuthenticationFilter` 会先检查 IP/Token 黑名单，再使用 `TokenCacheService` 的 `tokenCache` 缓存 JWT Claims。
  - `TokenService.verifyToken` 在完成完整校验后，会将 `TokenVerifyResponse` 写入 `tokenVerifyCache`。
  - `TokenCacheService` 同时缓存租户与用户状态（`tenantStatusCache` / `userStatusCache`），用于安全校验加速。
- **缓存实现**：`CacheConfig` 使用 Caffeine（本地内存缓存）。
- **缓存时间**：`security.token-cache.expire-after-write-seconds` 配置控制，默认 **600 秒（10 分钟）**。
  - 配置文件：`application-dev.yml`、`application-test.yml`、`application-prod.yml`。
  - `CacheConfig` 也提供相同的默认值（600）。
- **缓存大小**：`security.token-cache.max-size` 默认 **10000**。

## 2. auth_logs 表写入逻辑

- **写入入口**：`AuthLogService.record(...)`。
- **写入字段**：`tenant_id`、`user_id`（可为空）、`action`、`success`、`ip`、`created_at`。
- **IP 获取**：默认通过 `IpUtils.getClientIp()`，也可显式传入。
- **调用场景**（成功/失败都会记录）：
  - `TokenService`（`verifyToken` / `refreshToken` / `logout`）
  - `PasswordAuthService`、`EmailAuthService`、`PhoneAuthService`、`WeChatAuthService`
  - `UserService` 用户状态修改等管理操作
- **容错**：`TokenService.safeRecord(...)` 会捕获写入异常，避免影响主流程。

## 3. token_blacklist 表写入逻辑

- **表名**：`token_blacklist`（见 `schema.sql`）。
- **说明**：问题中提到的 `black_token` 对应该表。
- **写入入口**：
  - `TokenService.blacklistToken(...)`（用户登出）
  - `TokenService.refreshToken(...)`（刷新时旧 token 失效）
- **写入流程**：
  1. 解析 JWT，校验租户与用户状态。
  2. 计算 token 剩余有效期（TTL）。
  3. 生成 `tokenHash`，立即清理缓存（`tokenCache` / `tokenVerifyCache`）。
  4. 写入 Redis 黑名单：
     - `blacklist:<tokenHash>`
     - 若存在 JTI，写入 `blacklist:jti:<jti>`
     - TTL 与 token 过期时间一致。
  5. 写入数据库：
     - `token_hash`、`tenant_id`、`expires_at`、`created_at`
     - 通过 `TokenBlacklistMapper.insert(...)`，异常时仅记录日志，不影响登出/刷新主流程。
- **校验方式**：验证阶段优先通过 Redis 判断是否黑名单命中，数据库主要用于持久化与审计。
