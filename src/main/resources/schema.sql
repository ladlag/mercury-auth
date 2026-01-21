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
