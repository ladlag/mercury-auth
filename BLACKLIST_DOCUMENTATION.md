# 黑名单系统文档 (Blacklist System Documentation)

## 概述 (Overview)

Mercury Auth 认证系统支持多维度黑名单机制，为系统提供全面的安全防护。

Mercury Auth authentication system supports multi-dimensional blacklist mechanisms to provide comprehensive security protection.

## 黑名单维度 (Blacklist Dimensions)

### 1. Token 黑名单 (Token Blacklist)

**功能描述 (Description)**:
- 用于吊销已颁发的 JWT Token，确保用户登出或 Token 刷新后旧 Token 立即失效
- Used to revoke issued JWT tokens, ensuring old tokens become invalid immediately after logout or token refresh

**实现方式 (Implementation)**:
- 双层存储：Redis（快速查询）+ MySQL（持久化审计）
- Dual-layer storage: Redis (fast lookup) + MySQL (persistent audit)
- 自动过期：根据 Token 的剩余有效期设置 Redis TTL
- Auto expiration: Redis TTL set based on token's remaining validity

**触发条件 (Trigger Conditions)**:

1. **用户登出 (User Logout)**
   - 用户主动调用 `/api/v1/auth/logout` 接口
   - User actively calls `/api/v1/auth/logout` endpoint
   - Token 立即加入黑名单，有效期内无法再使用
   - Token is immediately blacklisted and cannot be used within its validity period

2. **Token 刷新 (Token Refresh)**
   - 用户调用 `/api/v1/auth/token/refresh` 获取新 Token
   - User calls `/api/v1/auth/token/refresh` to get new token
   - 旧 Token 自动加入黑名单，只有新 Token 有效
   - Old token is automatically blacklisted, only new token is valid

3. **可疑活动检测 (Suspicious Activity Detection)**
   - 系统检测到异常行为时可主动吊销 Token
   - System can proactively revoke tokens when abnormal behavior is detected
   - 例如：跨地域登录、并发会话异常等
   - Examples: cross-region login, concurrent session anomalies, etc.

**技术细节 (Technical Details)**:
- Redis Key: `blacklist:<token_hash>` 和 `blacklist:jti:<jti>`
- Redis Keys: `blacklist:<token_hash>` and `blacklist:jti:<jti>`
- 数据库表：`token_blacklist`
- Database table: `token_blacklist`
- 字段：token_hash, tenant_id, expires_at, created_at
- Fields: token_hash, tenant_id, expires_at, created_at

---

### 2. IP 黑名单 (IP Blacklist)

**功能描述 (Description)**:
- 支持全局和租户级别的 IP 地址黑名单
- Supports global and tenant-level IP address blacklisting
- 可设置临时或永久黑名单
- Can set temporary or permanent blacklists
- 支持 X-Forwarded-For 等代理头获取真实 IP
- Supports real IP extraction from X-Forwarded-For and other proxy headers

**实现方式 (Implementation)**:
- 双层存储：Redis（快速查询）+ MySQL（持久化管理）
- Dual-layer storage: Redis (fast lookup) + MySQL (persistent management)
- 全局黑名单：对所有租户生效
- Global blacklist: Effective for all tenants
- 租户黑名单：仅对特定租户生效
- Tenant blacklist: Effective only for specific tenant

**触发条件 (Trigger Conditions)**:

1. **重复登录失败 (Repeated Login Failures)**
   - 短时间内多次登录失败（密码错误、用户不存在等）
   - Multiple login failures in a short time (wrong password, user not found, etc.)
   - 自动黑名单时长：15-60 分钟（可配置）
   - Auto-blacklist duration: 15-60 minutes (configurable)
   - 示例：5 分钟内失败 10 次 → 自动封禁 30 分钟
   - Example: 10 failures in 5 minutes → auto-ban for 30 minutes

2. **速率限制违规 (Rate Limit Violations)**
   - 超过接口调用频率限制
   - Exceeds API call rate limits
   - 针对恶意刷接口、暴力破解等行为
   - Targets malicious API abuse, brute force attacks, etc.
   - 自动黑名单时长：根据违规严重程度动态调整
   - Auto-blacklist duration: Dynamically adjusted based on violation severity

3. **安全威胁检测 (Security Threat Detection)**
   - SQL 注入尝试
   - SQL injection attempts
   - XSS 攻击尝试
   - XSS attack attempts
   - 已知恶意 IP 库匹配
   - Known malicious IP database matches
   - DDoS 攻击特征
   - DDoS attack patterns

4. **管理员手动添加 (Manual Admin Addition)**
   - 管理员通过 API 手动添加 IP 黑名单
   - Admin manually adds IP blacklist via API
   - 适用于已知的威胁 IP 或安全策略执行
   - Suitable for known threat IPs or security policy enforcement
   - 支持添加原因说明和过期时间
   - Supports adding reason description and expiration time

**技术细节 (Technical Details)**:
- Redis Key: 
  - 全局：`blacklist:ip:global:<ip>`
  - Global: `blacklist:ip:global:<ip>`
  - 租户：`blacklist:ip:tenant:<tenant_id>:<ip>`
  - Tenant: `blacklist:ip:tenant:<tenant_id>:<ip>`
- 数据库表：`ip_blacklist`
- Database table: `ip_blacklist`
- 字段：ip_address, tenant_id (NULL for global), reason, expires_at, created_at, created_by
- Fields: ip_address, tenant_id (NULL for global), reason, expires_at, created_at, created_by

---

## 黑名单检查流程 (Blacklist Check Flow)

### 请求处理流程 (Request Processing Flow)

```
Client Request
    ↓
1. IP 黑名单检查 (IP Blacklist Check)
    ↓ (通过)
2. Token 黑名单检查 (Token Blacklist Check)
    ↓ (通过)
3. Token 验证 (Token Validation)
    ↓ (通过)
4. 业务逻辑处理 (Business Logic Processing)
```

### 检查优先级 (Check Priority)

1. **最高优先级：IP 黑名单**
   - 在 JwtAuthenticationFilter 中首先检查
   - Checked first in JwtAuthenticationFilter
   - 阻止黑名单 IP 的所有请求
   - Blocks all requests from blacklisted IPs

2. **第二优先级：Token 黑名单**
   - IP 检查通过后，检查 Token 是否在黑名单
   - After IP check passes, verify if token is blacklisted
   - 防止使用已吊销的 Token
   - Prevents use of revoked tokens

3. **最后：Token 有效性验证**
   - 黑名单检查通过后，验证 Token 签名、过期时间等
   - After blacklist checks pass, validate token signature, expiration, etc.

---

## API 接口 (API Endpoints)

### IP 黑名单管理 (IP Blacklist Management)

#### 1. 添加 IP 黑名单 (Add IP to Blacklist)

```http
POST /api/v1/admin/blacklist/ip
Content-Type: application/json

{
  "ipAddress": "192.168.1.100",
  "tenantId": "tenant1",  // null for global blacklist
  "reason": "Multiple failed login attempts",
  "expiresAt": "2024-12-31T23:59:59"  // null for permanent
}
```

#### 2. 移除 IP 黑名单 (Remove IP from Blacklist)

```http
DELETE /api/v1/admin/blacklist/ip?ipAddress=192.168.1.100&tenantId=tenant1
```

#### 3. 查询 IP 黑名单 (List IP Blacklists)

```http
GET /api/v1/admin/blacklist/ip?tenantId=tenant1
```

#### 4. 检查 IP 是否在黑名单 (Check if IP is Blacklisted)

```http
GET /api/v1/admin/blacklist/ip/check?ipAddress=192.168.1.100&tenantId=tenant1
```

#### 5. 清理过期黑名单 (Clean Up Expired Blacklist Entries)

```http
DELETE /api/v1/admin/blacklist/ip/cleanup
```

---

## 配置建议 (Configuration Recommendations)

### 自动黑名单策略 (Auto-Blacklist Policy)

```yaml
# 登录失败触发条件
login-failure-threshold:
  attempts: 10           # 失败次数
  window-minutes: 5      # 时间窗口（分钟）
  blacklist-duration: 30 # 黑名单时长（分钟）

# 速率限制违规
rate-limit-violation:
  blacklist-duration: 15 # 首次违规黑名单时长（分钟）
  escalation-factor: 2   # 重复违规时长倍增因子
```

### Redis 配置优化 (Redis Configuration Optimization)

```yaml
spring:
  redis:
    # 启用连接池
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
    # 启用 Redis Sentinel（生产环境推荐）
    sentinel:
      master: mymaster
      nodes: 
        - 127.0.0.1:26379
        - 127.0.0.1:26380
        - 127.0.0.1:26381
```

---

## 监控和告警 (Monitoring and Alerting)

### 关键指标 (Key Metrics)

1. **黑名单命中率 (Blacklist Hit Rate)**
   - Token 黑名单命中次数
   - Token blacklist hits
   - IP 黑名单命中次数
   - IP blacklist hits

2. **自动封禁统计 (Auto-Ban Statistics)**
   - 每日/每小时自动封禁 IP 数量
   - Daily/hourly auto-banned IP count
   - 封禁原因分布
   - Distribution of ban reasons

3. **黑名单规模 (Blacklist Size)**
   - Redis 中的黑名单条目数
   - Number of blacklist entries in Redis
   - 数据库中的历史记录数
   - Number of historical records in database

### 告警规则 (Alert Rules)

1. **异常流量告警**
   - 短时间内大量 IP 被自动封禁
   - Large number of IPs auto-banned in short time
   - 可能表示 DDoS 攻击
   - May indicate DDoS attack

2. **黑名单绕过尝试**
   - 同一用户从多个 IP 尝试登录
   - Same user attempting login from multiple IPs
   - 可能表示账号被盗用
   - May indicate account compromise

---

## 最佳实践 (Best Practices)

### 1. 分层防护 (Layered Protection)

- **网络层**：防火墙 + DDoS 防护
- **Network Layer**: Firewall + DDoS protection
- **应用层**：IP 黑名单 + 速率限制
- **Application Layer**: IP blacklist + rate limiting
- **认证层**：Token 黑名单 + 多因素认证
- **Authentication Layer**: Token blacklist + MFA

### 2. 定期清理 (Regular Cleanup)

```java
// 建议每天凌晨执行
@Scheduled(cron = "0 0 0 * * ?")
public void cleanupExpiredBlacklist() {
    blacklistService.cleanupExpiredIpBlacklist();
    // Token 黑名单由 Redis TTL 自动清理
}
```

### 3. 日志审计 (Audit Logging)

- 记录所有黑名单添加/移除操作
- Log all blacklist add/remove operations
- 记录黑名单命中事件
- Log blacklist hit events
- 定期分析安全日志，优化防护策略
- Regularly analyze security logs to optimize protection strategies

### 4. 白名单机制 (Whitelist Mechanism)

- 为可信 IP 设置白名单，跳过黑名单检查
- Set whitelist for trusted IPs to skip blacklist checks
- 例如：内部管理 IP、CDN 节点 IP
- Examples: Internal admin IPs, CDN node IPs

---

## 故障排查 (Troubleshooting)

### 问题 1：无法获取客户端真实 IP

**症状**: 所有请求显示相同 IP（代理/负载均衡器 IP）

**解决方案**:
- 配置 `X-Forwarded-For` 或 `X-Real-IP` 请求头
- Configure `X-Forwarded-For` or `X-Real-IP` headers
- 检查 `IpUtils.getClientIp()` 方法是否正确处理代理头
- Verify `IpUtils.getClientIp()` correctly handles proxy headers

### 问题 2：黑名单未生效

**症状**: 添加黑名单后仍可正常访问

**排查步骤**:
1. 检查 Redis 连接是否正常
2. 确认 IP 地址格式正确（IPv4/IPv6）
3. 检查租户 ID 是否匹配
4. 查看应用日志中的黑名单检查记录

### 问题 3：误封正常用户

**症状**: 正常用户被错误地加入黑名单

**解决方案**:
- 立即从黑名单移除该 IP
- 调整自动封禁阈值，降低误判率
- 考虑使用验证码而非直接封禁
- 建立申诉机制

---

## 安全注意事项 (Security Considerations)

1. **防止绕过 (Bypass Prevention)**
   - 不要仅依赖 IP 黑名单，需结合其他安全机制
   - Don't rely solely on IP blacklist, combine with other security mechanisms
   - 考虑用户指纹、设备指纹等多维度识别
   - Consider user fingerprint, device fingerprint for multi-dimensional identification

2. **分布式场景 (Distributed Scenarios)**
   - 使用 Redis 确保黑名单在所有实例间同步
   - Use Redis to ensure blacklist synchronization across instances
   - 考虑使用 Redis Pub/Sub 实现实时通知
   - Consider using Redis Pub/Sub for real-time notifications

3. **性能优化 (Performance Optimization)**
   - Redis 黑名单查询优先于数据库
   - Redis blacklist query takes priority over database
   - 使用布隆过滤器优化大规模黑名单查询
   - Use Bloom filter to optimize large-scale blacklist queries

4. **合规性 (Compliance)**
   - 记录黑名单操作日志，满足审计要求
   - Log blacklist operations to meet audit requirements
   - 定期清理过期数据，符合数据保护法规
   - Regularly clean expired data to comply with data protection regulations

---

## 总结 (Summary)

Mercury Auth 的多维度黑名单系统提供了：

Mercury Auth's multi-dimensional blacklist system provides:

✅ **Token 黑名单**：确保登出和刷新后旧 Token 失效
✅ **Token Blacklist**: Ensures old tokens become invalid after logout/refresh

✅ **IP 黑名单**：防止恶意 IP 攻击和滥用
✅ **IP Blacklist**: Prevents malicious IP attacks and abuse

✅ **自动防护**：基于行为分析的自动封禁机制
✅ **Auto Protection**: Auto-ban mechanism based on behavior analysis

✅ **灵活管理**：支持全局和租户级别的细粒度控制
✅ **Flexible Management**: Supports fine-grained control at global and tenant levels

✅ **高性能**：Redis + MySQL 双层存储确保查询效率
✅ **High Performance**: Redis + MySQL dual-layer storage ensures query efficiency

通过合理配置和使用黑名单系统，可以有效提升系统的安全性和稳定性。

Through proper configuration and use of the blacklist system, system security and stability can be effectively enhanced.
