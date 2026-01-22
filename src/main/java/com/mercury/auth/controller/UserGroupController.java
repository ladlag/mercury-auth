package com.mercury.auth.controller;

import com.mercury.auth.dto.UserGroupRequests;
import com.mercury.auth.entity.User;
import com.mercury.auth.entity.UserGroup;
import com.mercury.auth.service.UserGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user-groups")
@RequiredArgsConstructor
public class UserGroupController {

    private final UserGroupService userGroupService;

    @PostMapping("/create")
    public ResponseEntity<UserGroup> createGroup(@Validated @RequestBody UserGroupRequests.CreateGroup req) {
        return ResponseEntity.ok(userGroupService.createGroup(req));
    }

    @PostMapping("/update")
    public ResponseEntity<UserGroup> updateGroup(@Validated @RequestBody UserGroupRequests.UpdateGroup req) {
        return ResponseEntity.ok(userGroupService.updateGroup(req));
    }

    @GetMapping("/list")
    public ResponseEntity<List<UserGroup>> listGroups(@RequestParam String tenantId) {
        return ResponseEntity.ok(userGroupService.listGroups(tenantId));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<UserGroup> getGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(userGroupService.getGroup(groupId));
    }

    @PostMapping("/add-user")
    public ResponseEntity<Void> addUserToGroup(@Validated @RequestBody UserGroupRequests.AddUserToGroup req) {
        userGroupService.addUserToGroup(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/remove-user")
    public ResponseEntity<Void> removeUserFromGroup(@Validated @RequestBody UserGroupRequests.RemoveUserFromGroup req) {
        userGroupService.removeUserFromGroup(req);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserGroup>> getUserGroups(@PathVariable Long userId) {
        return ResponseEntity.ok(userGroupService.getUserGroups(userId));
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<User>> getGroupMembers(@PathVariable Long groupId) {
        return ResponseEntity.ok(userGroupService.getGroupMembers(groupId));
    }
}
