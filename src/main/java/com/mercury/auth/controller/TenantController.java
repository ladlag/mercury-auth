package com.mercury.auth.controller;

import com.mercury.auth.dto.TenantRequests;
import com.mercury.auth.dto.TenantResponse;
import com.mercury.auth.entity.Tenant;
import com.mercury.auth.service.RsaKeyService;
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
    private final RsaKeyService rsaKeyService;

    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(@Validated @RequestBody TenantRequests.Create req) {
        Tenant tenant = tenantService.create(req);
        return ResponseEntity.ok(TenantResponse.builder()
                .tenantId(tenant.getTenantId())
                .name(tenant.getName())
                .enabled(tenant.getEnabled())
                .build());
    }

    @GetMapping
    public ResponseEntity<List<Tenant>> listTenants() {
        return ResponseEntity.ok(tenantService.list());
    }

    @PostMapping("/status")
    public ResponseEntity<TenantResponse> updateStatus(@Validated @RequestBody TenantRequests.UpdateStatus req) {
        Tenant tenant = tenantService.updateStatus(req);
        return ResponseEntity.ok(TenantResponse.builder()
                .tenantId(tenant.getTenantId())
                .name(tenant.getName())
                .enabled(tenant.getEnabled())
                .build());
    }

    @PostMapping("/password-encryption/enable")
    public ResponseEntity<TenantResponse> enablePasswordEncryption(@Validated @RequestBody TenantRequests.UpdateStatus req) {
        try {
            rsaKeyService.enableEncryption(req.getTenantId());
            Tenant tenant = tenantService.getById(req.getTenantId());
            return ResponseEntity.ok(TenantResponse.builder()
                    .tenantId(tenant.getTenantId())
                    .name(tenant.getName())
                    .enabled(tenant.getEnabled())
                    .passwordEncryptionEnabled(tenant.getPasswordEncryptionEnabled())
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to enable password encryption", e);
        }
    }

    @PostMapping("/password-encryption/disable")
    public ResponseEntity<TenantResponse> disablePasswordEncryption(@Validated @RequestBody TenantRequests.UpdateStatus req) {
        rsaKeyService.disableEncryption(req.getTenantId());
        Tenant tenant = tenantService.getById(req.getTenantId());
        return ResponseEntity.ok(TenantResponse.builder()
                .tenantId(tenant.getTenantId())
                .name(tenant.getName())
                .enabled(tenant.getEnabled())
                .passwordEncryptionEnabled(tenant.getPasswordEncryptionEnabled())
                .build());
    }
}
