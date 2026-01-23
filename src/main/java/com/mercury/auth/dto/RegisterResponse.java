package com.mercury.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Response for registration operations
 */
@Data
@Builder
@AllArgsConstructor
public class RegisterResponse {
    private String tenantId;
    private Long userId;
    private String username;
}
