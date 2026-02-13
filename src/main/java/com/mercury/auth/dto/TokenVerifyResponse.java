package com.mercury.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenVerifyResponse {
    private String tenantId;
    private Long userId;
    private String userName;
    private String email;
    private String phone;
    private Long expiresAt;  // Unix timestamp in milliseconds

    @JsonIgnore
    private Long issuedAt;   // Internal use only: for cached revocation checks, not exposed in API response
}
