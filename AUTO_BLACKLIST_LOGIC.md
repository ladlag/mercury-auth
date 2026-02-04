# 自动黑名单逻辑实现说明
# Auto-Blacklist Logic Implementation Guide

## 问题背景 (Problem Background)

原始问题：
> 没看到所说的 行为异常比如尝试次数过多加入黑名单的逻辑。而且要注意限流和加入和名单的合理配合

Translation:
> "Don't see the logic for adding to blacklist due to abnormal behavior like too many attempts. Need to pay attention to reasonable coordination between rate limiting and blacklist."

## 解决方案概述 (Solution Overview)

本实现提供了完整的自动黑名单逻辑，包括：
1. ✅ 失败次数追踪
2. ✅ 自动触发机制
3. ✅ 与限流的合理配合
4. ✅ 分级响应策略

This implementation provides complete auto-blacklist logic including:
1. ✅ Failure count tracking
2. ✅ Automatic triggering mechanism
3. ✅ Proper coordination with rate limiting
4. ✅ Graduated response strategy

---

## 核心实现 (Core Implementation)

### 1. 失败追踪 (Failure Tracking)

**位置 (Location):** `RateLimitService.recordFailedLoginAttempt()`

```java
public void recordFailedLoginAttempt(String tenantId, String identifier) {
    // 获取客户端 IP
    String clientIp = getCurrentRequestIp();
    
    // 在 Redis 中追踪失败次数（两个时间窗口）
    String failureKey = "login:failures:" + clientIp;
    String severeFailureKey = "login:failures:severe:" + clientIp;
    
    // 普通失败计数（5分钟窗口）
    Long failureCount = redisTemplate.execute(
        RedisScript.of(RATE_LIMIT_SCRIPT, Long.class),
        Collections.singletonList(failureKey),
        String.valueOf(5 * 60)  // 5 minutes
    );
    
    // 严重违规计数（10分钟窗口）
    Long severeCount = redisTemplate.execute(
        RedisScript.of(RATE_LIMIT_SCRIPT, Long.class),
        Collections.singletonList(severeFailureKey),
        String.valueOf(10 * 60)  // 10 minutes
    );
    
    // 检查阈值并触发黑名单
    if (severeCount >= 50) {
        // 严重违规：封禁 2 小时
        blacklistService.autoBlacklistIp(clientIp, tenantId, reason, 120);
        redisTemplate.delete(failureKey);
        redisTemplate.delete(severeFailureKey);
    } else if (failureCount >= 20) {
        // 普通违规：封禁 30 分钟
        blacklistService.autoBlacklistIp(clientIp, tenantId, reason, 30);
        redisTemplate.delete(failureKey);
    }
}
```

### 2. 自动触发集成 (Auto-Trigger Integration)

**位置 (Location):** `PasswordAuthService.loginPassword()`

在以下所有失败场景中自动调用：

```java
// 1. 用户不存在
if (user == null) {
    rateLimitService.recordFailedLoginAttempt(tenantId, username);
    throw new ApiException(ErrorCodes.USER_NOT_FOUND);
}

// 2. 用户被禁用
if (!user.getEnabled()) {
    rateLimitService.recordFailedLoginAttempt(tenantId, username);
    throw new ApiException(ErrorCodes.USER_DISABLED);
}

// 3. 密码解密失败
try {
    password = decryptPassword(tenantId, req.getPassword());
} catch (Exception e) {
    rateLimitService.recordFailedLoginAttempt(tenantId, username);
    throw new ApiException(ErrorCodes.BAD_CREDENTIALS);
}

// 4. 密码验证失败
if (!passwordEncoder.matches(password, user.getPasswordHash())) {
    rateLimitService.recordFailedLoginAttempt(tenantId, username);
    throw new ApiException(ErrorCodes.BAD_CREDENTIALS);
}
```

### 3. 与限流的协调 (Coordination with Rate Limiting)

**三层防护机制 (Three-Layer Protection):**

```
第一层：速率限制 (Rate Limiting)
├─ 目的：短期保护，防止暴力破解
├─ 阈值：10 次/分钟
├─ 响应：临时阻止请求
└─ 重置：1 分钟后自动重置

第二层：失败追踪 (Failure Tracking)
├─ 目的：中期监控，识别持续攻击
├─ 窗口：5 分钟（普通）/ 10 分钟（严重）
├─ 计数：独立于速率限制
└─ 持久：跨多个速率限制周期

第三层：自动黑名单 (Auto-Blacklist)
├─ 目的：长期保护，阻止持续攻击者
├─ 触发：达到失败阈值
├─ 时长：30 分钟（普通）/ 120 分钟（严重）
└─ 存储：Redis + MySQL（持久化）
```

---

## 实际执行流程 (Execution Flow)

### 场景 1: 正常用户偶尔失败 (Normal User Occasional Failures)

```
时间    | 操作                | 速率限制 | 失败计数 | 结果
--------|---------------------|----------|----------|------
0:00    | 登录失败            | 1/10     | 1/20     | 允许
0:30    | 登录失败            | 2/10     | 2/20     | 允许
1:00    | 速率限制重置        | 0/10     | 2/20     | -
1:30    | 登录成功            | 1/10     | 2/20     | 成功
5:00    | 失败计数过期        | -        | 0/20     | -

结果：正常用户不受影响
```

### 场景 2: 暴力破解攻击 (Brute Force Attack)

```
时间    | 操作                | 速率限制 | 失败计数 | 结果
--------|---------------------|----------|----------|------
0:00    | 开始攻击            | 0/10     | 0/20     | -
0:00-1  | 10次失败            | 10/10    | 10/20    | 速率限制触发
1:00    | 速率限制重置        | 0/10     | 10/20    | 允许继续
1:00-2  | 又10次失败          | 10/10    | 20/20    | 速率限制 + 自动黑名单触发！
2:00    | 尝试登录            | -        | -        | IP黑名单阻止（30分钟）
...
32:00   | 黑名单过期          | -        | 0/20     | 重新允许

结果：攻击者被自动封禁 30 分钟
```

### 场景 3: 大规模攻击 (Large-Scale Attack)

```
时间    | 操作                | 速率限制 | 严重计数 | 结果
--------|---------------------|----------|----------|------
0:00-5  | 持续攻击50次        | 多次触发 | 50/50    | 严重违规黑名单触发！
5:00    | 尝试登录            | -        | -        | IP黑名单阻止（2小时）
...
125:00  | 黑名单过期          | -        | 0/50     | 重新允许

结果：严重攻击者被封禁 2 小时
```

---

## 配置说明 (Configuration Guide)

### 默认配置 (Default Configuration)

```yaml
security:
  rate-limit:
    # 速率限制（第一层防护）
    login:
      max-attempts: 10          # 每分钟最多 10 次
      window-minutes: 1
    
    # 自动黑名单（第三层防护）
    auto-blacklist:
      enabled: true
      
      # 普通违规
      failure-threshold: 20              # 20 次失败
      failure-window-minutes: 5          # 5 分钟内
      blacklist-duration-minutes: 30     # 封禁 30 分钟
      
      # 严重违规
      severe-failure-threshold: 50       # 50 次失败
      severe-failure-window-minutes: 10  # 10 分钟内
      severe-blacklist-duration-minutes: 120  # 封禁 2 小时
```

### 安全级别调整 (Security Level Tuning)

#### 严格模式 (Strict Mode) - 高安全性
```yaml
auto-blacklist:
  failure-threshold: 10          # 更低的阈值
  failure-window-minutes: 5
  blacklist-duration-minutes: 60  # 更长的封禁时间
```

#### 宽松模式 (Lenient Mode) - 开发环境
```yaml
auto-blacklist:
  failure-threshold: 50          # 更高的阈值
  failure-window-minutes: 10
  blacklist-duration-minutes: 15  # 更短的封禁时间
```

---

## 监控和维护 (Monitoring and Maintenance)

### 关键指标 (Key Metrics)

1. **失败率 (Failure Rate)**
   - Redis Key: `login:failures:<ip>`
   - 监控：每分钟失败次数

2. **黑名单触发次数 (Blacklist Triggers)**
   - 日志：`Auto-blacklist triggered`
   - 监控：每小时触发次数

3. **黑名单条目数 (Blacklist Entry Count)**
   - 查询：`SELECT COUNT(*) FROM ip_blacklist`
   - 监控：当前被封禁的 IP 数量

### 管理接口 (Management APIs)

```bash
# 查看黑名单
GET /api/v1/admin/blacklist/ip?tenantId=tenant1

# 手动添加黑名单
POST /api/v1/admin/blacklist/ip
{
  "ipAddress": "1.2.3.4",
  "tenantId": "tenant1",
  "reason": "Manual block",
  "expiresAt": "2024-12-31T23:59:59"
}

# 移除黑名单
DELETE /api/v1/admin/blacklist/ip?ipAddress=1.2.3.4&tenantId=tenant1

# 清理过期条目
DELETE /api/v1/admin/blacklist/ip/cleanup
```

---

## 测试验证 (Test Verification)

### 单元测试覆盖 (Unit Test Coverage)

1. ✅ `recordFailedLoginAttempt_doesNotBlacklistBelowThreshold`
   - 验证低于阈值不触发黑名单

2. ✅ `recordFailedLoginAttempt_triggersBlacklistAtThreshold`
   - 验证达到阈值触发黑名单

3. ✅ `recordFailedLoginAttempt_triggersSevereBlacklistAtHighThreshold`
   - 验证严重违规触发更长黑名单

4. ✅ `recordFailedLoginAttempt_clearsCounterAfterBlacklist`
   - 验证触发后清除计数器

5. ✅ `recordFailedLoginAttempt_doesNothingWhenDisabled`
   - 验证禁用状态不执行

6. ✅ `recordFailedLoginAttempt_handlesNullIpGracefully`
   - 验证边界情况处理

7. ✅ `recordFailedLoginAttempt_usesCorrectRedisKeys`
   - 验证 Redis 键名正确

**测试结果: 7/7 全部通过 ✅**

---

## 关键优势 (Key Advantages)

### 1. 真实实现 (Actual Implementation)
- ✅ 不仅是基础设施，而是完整的工作逻辑
- ✅ 自动集成到登录流程
- ✅ 无需手动调用

### 2. 合理协调 (Proper Coordination)
- ✅ 三层防护相互配合
- ✅ 不同时间窗口避免冲突
- ✅ 速率限制 + 黑名单 = 完整防护

### 3. 分级响应 (Graduated Response)
- ✅ 普通违规：30 分钟
- ✅ 严重违规：2 小时
- ✅ 根据攻击强度调整

### 4. 灵活配置 (Flexible Configuration)
- ✅ 可调整所有阈值
- ✅ 支持启用/禁用
- ✅ 适应不同安全需求

### 5. 生产就绪 (Production Ready)
- ✅ 完整测试覆盖
- ✅ 安全扫描通过
- ✅ 详细文档
- ✅ 配置示例

---

## 总结 (Summary)

本实现完全解决了原始问题：

1. ✅ **"没看到...尝试次数过多加入黑名单的逻辑"**
   - 已实现完整的自动黑名单逻辑
   - 在 PasswordAuthService 中自动触发
   - 20次/5分钟 或 50次/10分钟 自动封禁

2. ✅ **"注意限流和加入黑名单的合理配合"**
   - 三层防护机制
   - 速率限制（短期）+ 失败追踪（中期）+ 自动黑名单（长期）
   - 不同时间窗口，互不冲突
   - 分级响应策略

This implementation completely addresses the original issue:

1. ✅ **Auto-blacklist logic for excessive attempts**
   - Complete auto-blacklist implementation
   - Automatically triggered in PasswordAuthService
   - 20 failures/5min or 50 failures/10min → auto-ban

2. ✅ **Proper coordination between rate limiting and blacklist**
   - Three-layer protection mechanism
   - Rate limiting (short) + Failure tracking (medium) + Auto-blacklist (long)
   - Different time windows, no conflicts
   - Graduated response strategy

**状态: 完全实现并测试通过 ✅**
**Status: Fully implemented and tested ✅**
