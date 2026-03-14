-- Migration: Add user_type and nickname columns to users table
-- user_type: User category (USER, TENANT_ADMIN)
-- nickname: User display name

ALTER TABLE users ADD COLUMN nickname VARCHAR(100) COMMENT 'User display name' AFTER username;
ALTER TABLE users ADD COLUMN user_type VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'User category: USER, TENANT_ADMIN' AFTER nickname;
