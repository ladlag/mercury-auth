package com.mercury.auth.dto;

import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

public class TenantRequests {

    @EqualsAndHashCode(callSuper = true)
    public static class Create extends BaseTenantRequest {
        @NotBlank
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

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
