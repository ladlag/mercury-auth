-- Migration: Add timestamps to users table and token_hash to auth_logs table
-- Date: 2026-02-13

-- Add created_at and updated_at to users table
ALTER TABLE users ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- Add token_hash to auth_logs table for correlating audit entries with specific tokens
ALTER TABLE auth_logs ADD COLUMN token_hash VARCHAR(128);
