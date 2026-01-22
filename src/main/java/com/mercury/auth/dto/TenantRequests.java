package com.mercury.auth.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

public class TenantRequests {

    @Data
    public static class Create {
        @NotBlank(message = "Tenant ID is required")
        private String tenantId;
        @NotBlank(message = "Tenant name is required")
        private String name;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UpdateStatus extends BaseTenantRequest {
        private boolean enabled;
    }
}
