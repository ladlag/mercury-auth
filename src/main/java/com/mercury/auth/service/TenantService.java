package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.TenantRequests;
import com.mercury.auth.entity.Tenant;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.store.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantMapper tenantMapper;

    public void create(TenantRequests.Create req) {
        if (tenantMapper.selectById(req.getTenantId()) != null) {
            throw new ApiException(ErrorCodes.DUPLICATE_TENANT, "tenant exists");
        }
        Tenant tenant = new Tenant();
        tenant.setTenantId(req.getTenantId());
        tenant.setName(req.getName());
        tenant.setEnabled(true);
        tenantMapper.insert(tenant);
    }

    public List<Tenant> list() {
        return tenantMapper.selectList(new QueryWrapper<>());
    }

    public void updateStatus(TenantRequests.UpdateStatus req) {
        Tenant tenant = tenantMapper.selectById(req.getTenantId());
        if (tenant == null) {
            throw new ApiException(ErrorCodes.TENANT_NOT_FOUND, "tenant not found");
        }
        tenant.setEnabled(req.isEnabled());
        tenantMapper.updateById(tenant);
    }

    public Tenant requireEnabled(String tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new ApiException(ErrorCodes.TENANT_NOT_FOUND, "tenant not found");
        }
        if (Boolean.FALSE.equals(tenant.getEnabled())) {
            throw new ApiException(ErrorCodes.TENANT_DISABLED, "tenant disabled");
        }
        return tenant;
    }
}
