# Identifier Field Usage Guide / identifier字段使用指南

## English

### What is the Identifier Field?

The `identifier` field is used throughout the authentication system to uniquely identify a user within a tenant for rate limiting and tracking purposes.

### Key Concepts

1. **Purpose**: The identifier helps track authentication operations per user to prevent abuse
2. **Scope**: Combined with `tenantId` to ensure proper multi-tenant isolation
3. **Variability**: The identifier changes based on the authentication method being used

### Identifier by Authentication Method

| Authentication Method | Identifier Value | Example |
|----------------------|------------------|---------|
| Password Login | Username | `john_doe` |
| Email Login | Email Address | `user@example.com` |
| Phone Login | Phone Number | `+1234567890` |
| Email Verification | Email Address | `user@example.com` |
| Phone Verification | Phone Number | `+1234567890` |

### Usage in Captcha Requests

When requesting a captcha, the identifier must match the credential being used for login:

#### Password Login Example
```json
POST /api/auth/captcha
{
  "tenantId": "company-a",
  "identifier": "john_doe",
  "action": "CAPTCHA_LOGIN_PASSWORD"
}
```

#### Email Login Example
```json
POST /api/auth/captcha
{
  "tenantId": "company-a",
  "identifier": "john@company-a.com",
  "action": "CAPTCHA_LOGIN_EMAIL"
}
```

#### Phone Login Example
```json
POST /api/auth/captcha
{
  "tenantId": "company-a",
  "identifier": "+1234567890",
  "action": "CAPTCHA_LOGIN_PHONE"
}
```

### How Identifier is Used

#### 1. Rate Limiting
The identifier is used to build rate limiting keys:
```
Redis Key: rate:{ACTION}:{TENANT_ID}:{IDENTIFIER}
Example: rate:RATE_LIMIT_LOGIN_PASSWORD:company-a:john_doe
```

This ensures:
- Each user has their own rate limit quota
- Rate limits are isolated between tenants
- Abuse from one user doesn't affect others

#### 2. Captcha Failure Tracking
Similar to rate limiting, captcha requirements are tracked per identifier:
```
Redis Key: captcha:fail:{ACTION}:{TENANT_ID}:{IDENTIFIER}
Example: captcha:fail:CAPTCHA_LOGIN_PASSWORD:company-a:john_doe
```

After multiple failed login attempts (default: 3), captcha becomes required for that specific user.

### Complete Authentication Flow Examples

#### Password Login with Captcha

**Step 1: User attempts login (fails 3 times)**
```json
POST /api/auth/login-password
{
  "tenantId": "company-a",
  "username": "john_doe",
  "password": "wrong-password"
}
```
Response after 3rd failure: `CAPTCHA_REQUIRED`

**Step 2: Request captcha with matching identifier**
```json
POST /api/auth/captcha
{
  "tenantId": "company-a",
  "identifier": "john_doe",
  "action": "CAPTCHA_LOGIN_PASSWORD"
}
```
Response:
```json
{
  "captchaId": "uuid-here",
  "question": "3 + 5",
  "captchaImage": "base64-image-data",
  "expiresInSeconds": 300
}
```

**Step 3: Login with captcha**
```json
POST /api/auth/login-password
{
  "tenantId": "company-a",
  "username": "john_doe",
  "password": "correct-password",
  "captchaId": "uuid-here",
  "captcha": "8"
}
```

---

## 中文

### identifier字段是什么？

`identifier`字段在整个认证系统中用于在租户内唯一标识用户，用于限流和跟踪目的。

### 核心概念

1. **用途**：identifier帮助跟踪每个用户的认证操作，防止滥用
2. **作用域**：与`tenantId`结合使用，确保多租户隔离
3. **可变性**：identifier根据使用的认证方式而变化

### 不同认证方式的identifier值

| 认证方式 | identifier值 | 示例 |
|---------|-------------|------|
| 密码登录 | 用户名 | `john_doe` |
| 邮箱登录 | 邮箱地址 | `user@example.com` |
| 手机登录 | 手机号 | `+1234567890` |
| 邮箱验证码 | 邮箱地址 | `user@example.com` |
| 手机验证码 | 手机号 | `+1234567890` |

### 在验证码请求中的使用

请求验证码时，identifier必须与用于登录的凭据匹配：

#### 密码登录示例
```json
POST /api/auth/captcha
{
  "tenantId": "公司A",
  "identifier": "张三",
  "action": "CAPTCHA_LOGIN_PASSWORD"
}
```

#### 邮箱登录示例
```json
POST /api/auth/captcha
{
  "tenantId": "公司A",
  "identifier": "zhangsan@company-a.com",
  "action": "CAPTCHA_LOGIN_EMAIL"
}
```

#### 手机登录示例
```json
POST /api/auth/captcha
{
  "tenantId": "公司A",
  "identifier": "+8613800138000",
  "action": "CAPTCHA_LOGIN_PHONE"
}
```

### identifier的实际应用

#### 1. 限流（Rate Limiting）
identifier用于构建限流键：
```
Redis键: rate:{ACTION}:{TENANT_ID}:{IDENTIFIER}
示例: rate:RATE_LIMIT_LOGIN_PASSWORD:company-a:zhangsan
```

这确保了：
- 每个用户都有自己的限流配额
- 不同租户之间的限流是隔离的
- 一个用户的滥用不会影响其他用户

#### 2. 验证码失败跟踪
与限流类似，验证码要求也是按identifier跟踪的：
```
Redis键: captcha:fail:{ACTION}:{TENANT_ID}:{IDENTIFIER}
示例: captcha:fail:CAPTCHA_LOGIN_PASSWORD:company-a:zhangsan
```

多次登录失败后（默认：3次），该特定用户就需要输入验证码。

### 完整的认证流程示例

#### 带验证码的密码登录

**步骤1：用户尝试登录（失败3次）**
```json
POST /api/auth/login-password
{
  "tenantId": "公司A",
  "username": "张三",
  "password": "错误密码"
}
```
第3次失败后的响应: `CAPTCHA_REQUIRED`（需要验证码）

**步骤2：使用匹配的identifier请求验证码**
```json
POST /api/auth/captcha
{
  "tenantId": "公司A",
  "identifier": "张三",
  "action": "CAPTCHA_LOGIN_PASSWORD"
}
```
响应:
```json
{
  "captchaId": "uuid-这里",
  "question": "3 + 5",
  "captchaImage": "base64图片数据",
  "expiresInSeconds": 300
}
```

**步骤3：带验证码登录**
```json
POST /api/auth/login-password
{
  "tenantId": "公司A",
  "username": "张三",
  "password": "正确密码",
  "captchaId": "uuid-这里",
  "captcha": "8"
}
```

### 常见问题

#### Q: 为什么需要identifier字段？
**A**: identifier字段允许系统对每个用户单独进行限流和验证码跟踪，而不是全局限制。这样可以防止单个用户的滥用行为影响到其他正常用户。

#### Q: 如果identifier填错了会怎样？
**A**: 
- 对于验证码生成：系统现在会对验证码生成本身进行限流，防止滥用
- 对于登录：验证码验证会失败，因为验证码是与特定用户关联的

#### Q: 获取验证码时的频率限制是多少？
**A**: 默认配置是1分钟内最多10次请求。可以通过配置文件调整：
```yaml
security:
  rate-limit:
    max-attempts: 10    # 最大尝试次数
    window-minutes: 1   # 时间窗口（分钟）
```

#### Q: 如果频繁请求验证码会怎样？
**A**: 超过限制后会返回`RATE_LIMITED`错误（HTTP 429），需要等待时间窗口过期后才能继续请求。

### 最佳实践

1. **正确的identifier**: 始终使用与认证方法匹配的identifier
   - 密码登录 → 用户名
   - 邮箱登录/验证码 → 邮箱地址
   - 手机登录/验证码 → 手机号

2. **避免频繁请求**: 不要多次请求同一个验证码，缓存已获取的验证码

3. **处理限流错误**: 实现指数退避策略，在收到`RATE_LIMITED`错误后等待再重试

4. **用户提示**: 向用户清楚说明限流规则和剩余时间
