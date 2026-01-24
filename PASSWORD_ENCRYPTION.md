# 密码加密功能使用指南

## 概述

本系统支持两种密码传输模式，**以租户为粒度**进行配置：
1. **明文模式**：密码通过HTTPS明文传输（默认）
2. **加密模式**：前端使用RSA公钥加密密码后传输，后端自动解密

## 特性

- ✅ 租户级别的配置：每个租户可独立选择加密模式
- ✅ RSA 2048位加密
- ✅ 密钥对存储在数据库中
- ✅ 后端自动判断：根据租户配置决定是否解密
- ✅ 向后兼容：默认为明文模式

## 数据库Schema

```sql
ALTER TABLE tenants 
ADD COLUMN password_encryption_enabled TINYINT(1) NOT NULL DEFAULT 0,
ADD COLUMN rsa_public_key TEXT,
ADD COLUMN rsa_private_key TEXT;
```

## API使用

### 1. 获取租户的加密配置和公钥

前端在用户注册/登录前，需要先获取该租户的加密配置：

```bash
curl -X GET http://localhost:10000/auth-api/api/auth/public-key \
  -H "X-Tenant-Id: tenant1"
```

响应示例（加密已启用）：
```json
{
  "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "encryptionEnabled": true,
  "keySize": 2048
}
```

响应示例（加密未启用）：
```json
{
  "publicKey": null,
  "encryptionEnabled": false,
  "keySize": 0
}
```

### 2. 前端逻辑

```javascript
// 1. 获取租户的加密配置
const response = await fetch('/auth-api/api/auth/public-key', {
  headers: { 'X-Tenant-Id': 'tenant1' }
});
const config = await response.json();

// 2. 根据配置决定如何处理密码
let passwordToSend = userInputPassword;

if (config.encryptionEnabled && config.publicKey) {
  // 使用RSA公钥加密密码
  passwordToSend = await encryptPassword(userInputPassword, config.publicKey);
}

// 3. 发送登录/注册请求
await fetch('/auth-api/api/auth/login-password', {
  method: 'POST',
  headers: {
    'X-Tenant-Id': 'tenant1',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    username: 'user1',
    password: passwordToSend  // 根据配置，可能是明文或密文
  })
});
```

### 3. RSA加密示例 (JavaScript)

```javascript
// 使用 JSEncrypt 库
async function encryptPassword(password, publicKeyBase64) {
  const JSEncrypt = require('jsencrypt').default;
  const encrypt = new JSEncrypt();
  encrypt.setPublicKey(atob(publicKeyBase64));
  return encrypt.encrypt(password);
}
```

或使用 Node.js crypto:
```javascript
const crypto = require('crypto');

function encryptPassword(password, publicKeyBase64) {
  const publicKey = Buffer.from(publicKeyBase64, 'base64');
  const encrypted = crypto.publicEncrypt(
    {
      key: publicKey,
      padding: crypto.constants.RSA_PKCS1_PADDING
    },
    Buffer.from(password, 'utf8')
  );
  return encrypted.toString('base64');
}
```

## 管理员API

### 为租户启用密码加密

```bash
curl -X POST http://localhost:10000/auth-api/api/tenants/password-encryption/enable \
  -H "Authorization: ****** \
  -H "X-Tenant-Id: admin-tenant" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant1"
  }'
```

此操作会：
1. 自动生成2048位RSA密钥对
2. 将密钥对存储到数据库
3. 启用该租户的密码加密功能

### 为租户禁用密码加密

```bash
curl -X POST http://localhost:10000/auth-api/api/tenants/password-encryption/disable \
  -H "Authorization: ****** \
  -H "X-Tenant-Id: admin-tenant" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant1"
  }'
```

注意：禁用加密不会删除密钥，可以随时重新启用。

## 后端自动处理

后端会根据租户的 `password_encryption_enabled` 配置自动处理：

```java
// 后端服务自动判断
String plaintextPassword = passwordEncryptionService.processPassword(
    tenantId,    // 租户ID
    password     // 前端传来的密码（明文或密文）
);

// 如果租户启用了加密 → 自动解密
// 如果租户未启用加密 → 直接返回原值
```

## 影响的端点

以下端点都支持根据租户配置自动处理密码：

- `POST /api/auth/register-password` - 密码注册
- `POST /api/auth/login-password` - 密码登录  
- `POST /api/auth/register-email` - 邮箱注册（带密码）
- `POST /api/auth/change-password` - 修改密码
- `POST /api/auth/reset-password` - 重置密码

## 安全建议

1. **生产环境强烈建议启用加密模式**
2. 定期轮换密钥（TODO: 未来版本支持）
3. 使用HTTPS确保整体传输安全
4. 管理员端点需要严格的访问控制
5. 监控异常的解密失败日志

## 故障排查

### 问题：登录时提示 "invalid password format"

**原因：** 租户启用了加密，但前端发送了明文密码

**解决：** 
1. 确认前端正确调用了 `/api/auth/public-key` 端点
2. 确认前端根据返回的 `encryptionEnabled` 字段决定是否加密
3. 检查RSA加密实现是否正确

### 问题：解密失败

**可能原因：**
1. 前端使用了错误的公钥
2. 加密算法或padding不匹配（应使用 RSA/ECB/PKCS1Padding）
3. Base64编码问题

**排查步骤：**
1. 检查后端日志中的详细错误信息
2. 验证前端获取的公钥与数据库中的公钥一致
3. 确认前端加密后的结果是Base64编码的字符串

## 迁移指南

### 从明文模式迁移到加密模式

1. **准备阶段**
   - 更新前端代码，添加加密逻辑
   - 测试环境验证

2. **启用加密**
   ```bash
   # 为租户启用加密
   curl -X POST .../api/tenants/password-encryption/enable \
     -d '{"tenantId": "your-tenant-id"}'
   ```

3. **验证**
   - 测试用户注册、登录功能
   - 检查后端日志，确认密码正确解密

4. **回滚方案**
   ```bash
   # 如遇问题，可立即禁用加密
   curl -X POST .../api/tenants/password-encryption/disable \
     -d '{"tenantId": "your-tenant-id"}'
   ```

## 性能影响

- RSA解密操作：约1-2ms per request
- 密钥从数据库读取：有租户缓存可进一步优化（TODO）
- 对高并发场景影响较小

## 未来改进

- [ ] 密钥轮换支持
- [ ] 租户配置缓存
- [ ] 支持其他加密算法（如ECC）
- [ ] 管理界面
