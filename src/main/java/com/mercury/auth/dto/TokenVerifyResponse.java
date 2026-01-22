package com.mercury.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenVerifyResponse {
    private String tenantId;
    private Long userId;
    private String username;
    private String email;
    private String phone;
}
