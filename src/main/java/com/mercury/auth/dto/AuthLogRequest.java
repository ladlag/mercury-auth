package com.mercury.auth.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

@Data
@EqualsAndHashCode(callSuper = true)
public class AuthLogRequest extends BaseTenantRequest {
    private Long userId;
    @NotBlank
    private String action;
    private boolean success;
    private String ip;
}
