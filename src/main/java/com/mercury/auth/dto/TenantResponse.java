package com.mercury.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Response for tenant operations
 */
@Data
@Builder
@AllArgsConstructor
public class TenantResponse {
    private String tenantId;
    private String name;
    private Boolean enabled;
}
