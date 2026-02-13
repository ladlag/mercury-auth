package com.mercury.auth.controller;

import com.mercury.auth.dto.ApiResponse;
import com.mercury.auth.dto.TenantRequests;
import com.mercury.auth.dto.TenantResponse;
import com.mercury.auth.entity.Tenant;
import com.mercury.auth.service.RsaKeyService;
import com.mercury.auth.service.TenantService;
import com.mercury.auth.util.XssSanitizer;
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
        return ResponseEntity.ok(ApiResponse.success(buildTenantResponse(tenant, false)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Tenant>>> listTenants() {
        return ResponseEntity.ok(ApiResponse.success(tenantService.list()));
    }

    @PostMapping("/status")
    public ResponseEntity<ApiResponse<TenantResponse>> updateStatus(@Validated @RequestBody TenantRequests.UpdateStatus req) {
        Tenant tenant = tenantService.updateStatus(req);
        return ResponseEntity.ok(ApiResponse.success(buildTenantResponse(tenant, false)));
    }

    @PostMapping("/password-encryption/enable")
    public ResponseEntity<ApiResponse<TenantResponse>> enablePasswordEncryption(@Validated @RequestBody TenantRequests.UpdateStatus req) {
        Tenant tenant = rsaKeyService.enableEncryption(req.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(buildTenantResponse(tenant, true)));
    }

    @PostMapping("/password-encryption/disable")
    public ResponseEntity<ApiResponse<TenantResponse>> disablePasswordEncryption(@Validated @RequestBody TenantRequests.UpdateStatus req) {
        Tenant tenant = rsaKeyService.disableEncryption(req.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(buildTenantResponse(tenant, true)));
    }

    private TenantResponse buildTenantResponse(Tenant tenant, boolean includeEncryptionStatus) {
        TenantResponse.TenantResponseBuilder builder = TenantResponse.builder()
                .tenantId(XssSanitizer.sanitize(tenant.getTenantId()))
                .name(XssSanitizer.sanitize(tenant.getName()))
                .enabled(tenant.getEnabled());
        if (includeEncryptionStatus) {
            builder.passwordEncryptionEnabled(tenant.getPasswordEncryptionEnabled());
        }
        return builder.build();
    }
}
