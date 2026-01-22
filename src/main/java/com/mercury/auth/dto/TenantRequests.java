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

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UpdateStatus extends BaseTenantRequest {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
