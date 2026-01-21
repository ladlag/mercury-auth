package com.mercury.auth.controller;

import com.mercury.auth.dto.TenantRequests;
import com.mercury.auth.entity.Tenant;
import com.mercury.auth.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    public ResponseEntity<Void> createTenant(@Validated @RequestBody TenantRequests.Create req) {
        tenantService.create(req);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<Tenant>> listTenants() {
        return ResponseEntity.ok(tenantService.list());
    }

    @PostMapping("/status")
    public ResponseEntity<Void> updateStatus(@Validated @RequestBody TenantRequests.UpdateStatus req) {
        tenantService.updateStatus(req);
        return ResponseEntity.ok().build();
    }
}
