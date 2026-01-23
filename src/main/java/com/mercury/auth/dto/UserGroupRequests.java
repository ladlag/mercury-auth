package com.mercury.auth.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

public class UserGroupRequests {

    @Data
    public static class CreateGroup {
        @NotBlank(message = "tenant id required")
        private String tenantId;
        
        @NotBlank(message = "group name required")
        private String name;
        
        private String description;
    }

    @Data
    public static class UpdateGroup {
        @NotNull(message = "group id required")
        private Long id;
        
        private String name;
        private String description;
        private Boolean enabled;
    }

    @Data
    public static class AddUserToGroup {
        @NotNull(message = "user id required")
        private Long userId;
        
        @NotNull(message = "group id required")
        private Long groupId;
    }

    @Data
    public static class RemoveUserFromGroup {
        @NotNull(message = "user id required")
        private Long userId;
        
        @NotNull(message = "group id required")
        private Long groupId;
    }

    @Data
    public static class AssignPermissions {
        @NotNull(message = "group id required")
        private Long groupId;
        
        @NotNull(message = "permission ids required")
        private List<Long> permissionIds;
    }
}
