-- Migration script to add password encryption fields to tenants table
-- Run this script if you have an existing tenants table

ALTER TABLE tenants 
ADD COLUMN password_encryption_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether RSA password encryption is enabled for this tenant',
ADD COLUMN rsa_public_key TEXT COMMENT 'Base64 encoded RSA public key for password encryption',
ADD COLUMN rsa_private_key TEXT COMMENT 'Base64 encoded RSA private key for password decryption';
