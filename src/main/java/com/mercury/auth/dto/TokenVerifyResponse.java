package com.mercury.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class TokenVerifyResponse {
    private String tenantId;
    private Long userId;
    private String userName;
    private String email;
    private String phone;
    private Date expiresAt;
}
