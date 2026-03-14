package com.mercury.auth.dto;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * User type enumeration for categorizing users within a tenant.
 */
public enum UserType {

    /** Regular user */
    USER("USER"),

    /** Tenant administrator with management privileges */
    TENANT_ADMIN("TENANT_ADMIN");

    @EnumValue
    private final String value;

    UserType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
