-- Migration script to add password encryption fields to tenants table
-- Run this script if you have an existing tenants table

ALTER TABLE tenants 
ADD COLUMN password_encryption_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否开启密码加密',
ADD COLUMN rsa_public_key TEXT COMMENT '公钥',
ADD COLUMN rsa_private_key TEXT COMMENT '私钥';
