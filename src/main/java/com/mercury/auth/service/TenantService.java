package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.TenantRequests;
import com.mercury.auth.entity.Tenant;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.store.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantMapper tenantMapper;
    @Lazy
    private final TokenCacheService tokenCacheService;

    public Tenant create(TenantRequests.Create req) {
        if (tenantMapper.selectById(req.getTenantId()) != null) {
            throw new ApiException(ErrorCodes.DUPLICATE_TENANT, "tenant exists");
        }
        Tenant tenant = new Tenant();
        tenant.setTenantId(req.getTenantId());
        tenant.setName(req.getName());
        tenant.setEnabled(true);
        tenantMapper.insert(tenant);
        return tenant;
    }

    public List<Tenant> list() {
        return tenantMapper.selectList(new QueryWrapper<>());
    }

    public Tenant updateStatus(TenantRequests.UpdateStatus req) {
        Tenant tenant = tenantMapper.selectById(req.getTenantId());
        if (tenant == null) {
            throw new ApiException(ErrorCodes.TENANT_NOT_FOUND, "tenant not found");
        }
        tenant.setEnabled(req.isEnabled());
        tenantMapper.updateById(tenant);
        
        // SECURITY: Evict all token caches when tenant status changes
        // This prevents disabled tenants from continuing to use cached tokens
        tokenCacheService.evictAllForTenantStatusChange(req.getTenantId());
        
        return tenant;
    }

    public Tenant requireEnabled(String tenantId) {
        Tenant cached = tokenCacheService.getCachedTenant(tenantId);
        if (cached != null) {
            if (Boolean.FALSE.equals(cached.getEnabled())) {
                tokenCacheService.evictTenantStatus(tenantId);
                throw new ApiException(ErrorCodes.TENANT_DISABLED, "tenant disabled");
            }
            return cached;
        }
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new ApiException(ErrorCodes.TENANT_NOT_FOUND, "tenant not found");
        }
        if (Boolean.FALSE.equals(tenant.getEnabled())) {
            throw new ApiException(ErrorCodes.TENANT_DISABLED, "tenant disabled");
        }
        tokenCacheService.cacheTenant(tenantId, tenant);
        return tenant;
    }

    public Tenant getById(String tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new ApiException(ErrorCodes.TENANT_NOT_FOUND, "tenant not found");
        }
        return tenant;
    }
}
