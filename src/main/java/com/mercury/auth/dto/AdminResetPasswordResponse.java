package com.mercury.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Response for admin reset password operation.
 * Contains the system-generated random password for the tenant admin to share with the user.
 */
@Data
@Builder
@AllArgsConstructor
public class AdminResetPasswordResponse {
    private String tenantId;
    private Long userId;
    private String username;
    private String nickname;
    private UserType userType;
    private String newPassword;
}
