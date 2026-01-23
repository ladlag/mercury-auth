package com.mercury.auth.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Base class for tenant-scoped requests.
 * 
 * For PUBLIC endpoints (login, register, etc.):
 * - tenantId is required in request body and will be validated
 * - X-Tenant-Id header is optional
 * 
 * For PROTECTED endpoints (logout, user management, etc.):
 * - X-Tenant-Id header is MANDATORY and must match JWT token's tenant
 * - tenantId in request body is OPTIONAL - if present, it will be overwritten by header value
 * - Recommendation: Do not include tenantId in request body for protected endpoints
 */
@Data
public class BaseTenantRequest {
    @NotBlank(message = "{validation.tenantId.required}")
    private String tenantId;
}
