package com.mercury.auth.dto;

import com.mercury.auth.util.ValidationPatterns;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class TenantRequests {

    @Data
    public static class Create {
        @NotBlank(message = "{validation.tenantId.required}")
        @Pattern(regexp = ValidationPatterns.TENANT_ID, message = "{validation.tenantId.invalid}")
        private String tenantId;
        @NotBlank(message = "{validation.tenantName.required}")
        @Pattern(regexp = ValidationPatterns.TENANT_NAME, message = "{validation.tenantName.invalid}")
        private String name;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UpdateStatus extends BaseTenantRequest {
        private boolean enabled;
    }
}
