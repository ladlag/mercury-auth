package com.mercury.auth.controller;

import com.mercury.auth.dto.PermissionRequests;
import com.mercury.auth.dto.UserGroupRequests;
import com.mercury.auth.entity.Permission;
import com.mercury.auth.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @PostMapping("/create")
    public ResponseEntity<Permission> createPermission(@Validated @RequestBody PermissionRequests.CreatePermission req) {
        return ResponseEntity.ok(permissionService.createPermission(req));
    }

    @PostMapping("/update")
    public ResponseEntity<Permission> updatePermission(@Validated @RequestBody PermissionRequests.UpdatePermission req) {
        return ResponseEntity.ok(permissionService.updatePermission(req));
    }

    @GetMapping("/list")
    public ResponseEntity<List<Permission>> listPermissions(@RequestParam String tenantId) {
        return ResponseEntity.ok(permissionService.listPermissions(tenantId));
    }

    @GetMapping("/{permissionId}")
    public ResponseEntity<Permission> getPermission(@PathVariable Long permissionId) {
        return ResponseEntity.ok(permissionService.getPermission(permissionId));
    }

    @PostMapping("/assign-to-group")
    public ResponseEntity<Void> assignPermissionsToGroup(@Validated @RequestBody UserGroupRequests.AssignPermissions req) {
        permissionService.assignPermissionsToGroup(req.getGroupId(), req.getPermissionIds());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<Permission>> getGroupPermissions(@PathVariable Long groupId) {
        return ResponseEntity.ok(permissionService.getGroupPermissions(groupId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Permission>> getUserPermissions(
            @PathVariable Long userId,
            @RequestParam String tenantId) {
        return ResponseEntity.ok(permissionService.getUserPermissions(userId, tenantId));
    }

    @PostMapping("/check")
    public ResponseEntity<Map<String, Boolean>> checkPermission(@Validated @RequestBody PermissionRequests.CheckPermission req) {
        boolean hasPermission = permissionService.hasPermission(
                req.getUserId(), 
                req.getPermissionCode(), 
                req.getTenantId()
        );
        return ResponseEntity.ok(Map.of("hasPermission", hasPermission));
    }
}
