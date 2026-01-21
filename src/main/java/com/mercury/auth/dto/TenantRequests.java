package com.mercury.auth.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

public class TenantRequests {

    @Data
    public static class Create {
        @NotBlank
        private String tenantId;
        @NotBlank
        private String name;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UpdateStatus extends BaseTenantRequest {
        private boolean enabled;
    }
}
