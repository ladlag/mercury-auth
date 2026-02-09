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
        String longName = new String(new char[51]).replace('\0', 'a');
        String[] invalidNames = {"<script>", " tenant", "tenant ", "!!!", longName};
        String expectedMessage = "Tenant name must be 1-50 characters and contain only letters, numbers, spaces, hyphens, or underscores";

        for (String name : invalidNames) {
            TenantRequests.Create request = new TenantRequests.Create();
            request.setTenantId("tenant-1");
            request.setName(name);

            Set<ConstraintViolation<TenantRequests.Create>> violations = validator.validate(request);

            assertThat(violations)
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("name");
            assertThat(violations)
                    .anyMatch(violation -> "name".equals(violation.getPropertyPath().toString())
                            && expectedMessage.equals(violation.getMessage()));
        }
    }

    @Test
    void createTenant_accepts_valid_tenantNames() {
        String exactLengthName = new String(new char[50]).replace('\0', 'a');
        String[] validNames = {"A", "Mercury Auth", "tenant_001", "tenant-001", "租户一号", exactLengthName};

        for (String name : validNames) {
            TenantRequests.Create request = new TenantRequests.Create();
            request.setTenantId("tenant-1");
            request.setName(name);

            Set<ConstraintViolation<TenantRequests.Create>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }
}
