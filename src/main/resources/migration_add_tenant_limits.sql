-- Migration: Add tenant user limits and registration controls
-- Date: 2026-02-09
-- Description: 
--   1. Add max_users field to tenants table (NULL means unlimited)
--   2. This supports requirement 1: limit max users per tenant
--   3. Requirement 2 (daily registration limits per tenant/IP) is handled via configuration and Redis

-- Add max_users column to tenants table
ALTER TABLE tenants ADD COLUMN max_users INT NULL COMMENT 'Maximum number of users allowed for this tenant. NULL means unlimited.';

-- Add index for performance when counting users per tenant
-- This index already exists implicitly via UNIQUE KEY, but making it explicit for clarity
-- CREATE INDEX idx_tenant_id ON users(tenant_id);
