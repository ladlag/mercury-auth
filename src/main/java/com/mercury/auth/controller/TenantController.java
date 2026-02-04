package com.mercury.auth.controller;

import com.mercury.auth.dto.ApiResponse;
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
    public ResponseEntity<ApiResponse<TenantResponse>> createTenant(@Validated @RequestBody TenantRequests.Create req) {
        Tenant tenant = tenantService.create(req);
        TenantResponse data = TenantResponse.builder()
                .tenantId(tenant.getTenantId())
                .name(tenant.getName())
                .enabled(tenant.getEnabled())
                .build();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Tenant>>> listTenants() {
        return ResponseEntity.ok(ApiResponse.success(tenantService.list()));
    }

    @PostMapping("/status")
    public ResponseEntity<ApiResponse<TenantResponse>> updateStatus(@Validated @RequestBody TenantRequests.UpdateStatus req) {
        Tenant tenant = tenantService.updateStatus(req);
        TenantResponse data = TenantResponse.builder()
                .tenantId(tenant.getTenantId())
                .name(tenant.getName())
                .enabled(tenant.getEnabled())
                .build();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/password-encryption/enable")
    public ResponseEntity<ApiResponse<TenantResponse>> enablePasswordEncryption(@Validated @RequestBody TenantRequests.UpdateStatus req) {
        Tenant tenant = rsaKeyService.enableEncryption(req.getTenantId());
        TenantResponse data = TenantResponse.builder()
                .tenantId(tenant.getTenantId())
                .name(tenant.getName())
                .enabled(tenant.getEnabled())
                .passwordEncryptionEnabled(tenant.getPasswordEncryptionEnabled())
                .build();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/password-encryption/disable")
    public ResponseEntity<ApiResponse<TenantResponse>> disablePasswordEncryption(@Validated @RequestBody TenantRequests.UpdateStatus req) {
        Tenant tenant = rsaKeyService.disableEncryption(req.getTenantId());
        TenantResponse data = TenantResponse.builder()
                .tenantId(tenant.getTenantId())
                .name(tenant.getName())
                .enabled(tenant.getEnabled())
                .passwordEncryptionEnabled(tenant.getPasswordEncryptionEnabled())
                .build();
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
