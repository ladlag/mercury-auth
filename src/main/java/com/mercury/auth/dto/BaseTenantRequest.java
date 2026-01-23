package com.mercury.auth.dto;

import lombok.Data;

/**
 * Base class for tenant-scoped requests.
 * 
 * The tenantId field is automatically injected from the X-Tenant-Id HTTP header.
 * 
 * **IMPORTANT**: All endpoints (both public and protected) require X-Tenant-Id header:
 * - Public endpoints (login, register, etc.): Header specifies which tenant to operate on
 * - Protected endpoints (logout, user management, etc.): Header must match JWT token's tenant
 * 
 * Clients should include X-Tenant-Id header in ALL requests and omit tenantId from request body.
 * Any tenantId value in the request body will be ignored and overwritten by the header value.
 */
@Data
public class BaseTenantRequest {
    /**
     * Tenant ID - automatically injected from X-Tenant-Id header.
     * Do not include this field in request body JSON.
     */
    private String tenantId;
}
