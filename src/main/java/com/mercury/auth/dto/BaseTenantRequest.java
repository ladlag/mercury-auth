package com.mercury.auth.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class BaseTenantRequest {
    @NotBlank(message = "Tenant ID is required")
    private String tenantId;
}
