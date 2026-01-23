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

CREATE TABLE IF NOT EXISTS user_groups (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_tenant_group_name (tenant_id, name)
);

CREATE TABLE IF NOT EXISTS user_group_members (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  group_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_user_group (user_id, group_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (group_id) REFERENCES user_groups(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS permissions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id VARCHAR(64) NOT NULL,
  code VARCHAR(100) NOT NULL,
  name VARCHAR(150) NOT NULL,
  type VARCHAR(50) NOT NULL COMMENT 'MENU, API, DATA, BUTTON',
  resource VARCHAR(300) COMMENT 'Resource identifier (URL, menu path, etc)',
  description VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_tenant_permission_code (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS user_group_permissions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  group_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uniq_group_permission (group_id, permission_id),
  FOREIGN KEY (group_id) REFERENCES user_groups(id) ON DELETE CASCADE,
  FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);
