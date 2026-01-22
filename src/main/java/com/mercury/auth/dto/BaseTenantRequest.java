package com.mercury.auth.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class BaseTenantRequest {
    @NotBlank
    private String tenantId;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
