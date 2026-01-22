package com.mercury.auth.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class PermissionRequests {

    @Data
    public static class CreatePermission {
        @NotBlank(message = "tenant id required")
        private String tenantId;
        
        @NotBlank(message = "permission code required")
        private String code;
        
        @NotBlank(message = "permission name required")
        private String name;
        
        @NotBlank(message = "permission type required")
        private String type;
        
        private String resource;
        private String description;
    }

    @Data
    public static class UpdatePermission {
        @NotNull(message = "permission id required")
        private Long id;
        
        private String name;
        private String type;
        private String resource;
        private String description;
    }

    @Data
    public static class CheckPermission {
        @NotNull(message = "user id required")
        private Long userId;
        
        @NotBlank(message = "permission code required")
        private String permissionCode;
        
        @NotBlank(message = "tenant id required")
        private String tenantId;
    }
}
