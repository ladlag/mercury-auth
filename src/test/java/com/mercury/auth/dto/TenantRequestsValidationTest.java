package com.mercury.auth.dto;

import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TenantRequestsValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createTenant_rejects_invalid_tenantId() {
        TenantRequests.Create request = new TenantRequests.Create();
        request.setTenantId("<script>");
        request.setName("tenant-name");

        Set<ConstraintViolation<TenantRequests.Create>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("tenantId");
    }

    @Test
    void createTenant_rejects_invalid_tenantName() {
        TenantRequests.Create request = new TenantRequests.Create();
        request.setTenantId("tenant-1");
        request.setName("<script>");

        Set<ConstraintViolation<TenantRequests.Create>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
    }

    @Test
    void createTenant_accepts_valid_tenantNames() {
        String[] validNames = {"Mercury Auth", "tenant_001", "tenant-001", "租户一号"};

        for (String name : validNames) {
            TenantRequests.Create request = new TenantRequests.Create();
            request.setTenantId("tenant-1");
            request.setName(name);

            Set<ConstraintViolation<TenantRequests.Create>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }
}
