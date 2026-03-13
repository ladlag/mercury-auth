package com.mercury.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO for tenant user list items.
 * Used in tenant user management queries.
 */
@Data
@Builder
@AllArgsConstructor
public class TenantUserItem {
    private Long userId;
    private String username;
    private String nickname;
    private String userType;
    private String email;
    private String phone;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
