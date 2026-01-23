package com.mercury.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Base response for authentication operations
 */
@Data
@Builder
@AllArgsConstructor
public class BaseAuthResponse {
    private String tenantId;
    private Long userId;
    private String username;
}
