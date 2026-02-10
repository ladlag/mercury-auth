CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(64) NOT NULL,
  username VARCHAR(100) NOT NULL,
  email VARCHAR(150),
  phone VARCHAR(50),
  password_hash VARCHAR(200) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  UNIQUE KEY uniq_tenant_username (tenant_id, username),
  UNIQUE KEY uniq_tenant_email (tenant_id, email),
  UNIQUE KEY uniq_tenant_phone (tenant_id, phone)
);

CREATE TABLE IF NOT EXISTS tenants (
  tenant_id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  password_encryption_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether RSA password encryption is enabled for this tenant',
  rsa_public_key TEXT COMMENT 'Base64 encoded RSA public key for password encryption',
  rsa_private_key TEXT COMMENT 'Base64 encoded RSA private key for password decryption',
  max_users INT NULL COMMENT 'Maximum number of users allowed for this tenant. NULL means unlimited',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(64) NOT NULL,
  user_id BIGINT,
  action VARCHAR(64) NOT NULL,
  success TINYINT(1) NOT NULL,
  ip VARCHAR(64),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS token_blacklist (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  token_hash VARCHAR(128) NOT NULL,
  tenant_id VARCHAR(64) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_token_hash (token_hash)
);

CREATE TABLE IF NOT EXISTS ip_blacklist (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ip_address VARCHAR(64) NOT NULL,
  tenant_id VARCHAR(64) COMMENT 'NULL for global blacklist, specific tenant_id for tenant-specific blacklist',
  reason VARCHAR(500),
  expires_at TIMESTAMP NULL COMMENT 'NULL for permanent blacklist',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by VARCHAR(100) COMMENT 'Admin username or system',
  INDEX idx_ip_tenant (ip_address, tenant_id),
  INDEX idx_expires (expires_at)
);
