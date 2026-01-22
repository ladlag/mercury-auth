package com.mercury.auth;

import com.mercury.auth.dto.ApiError;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.BaseTenantRequest;
import com.mercury.auth.dto.TenantRequests;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class DtoGetterTests {

    @Test
    void innerClassGettersAreAvailable() throws Exception {
        for (Class<?> clazz : innerRequestTypes().collect(Collectors.toList())) {
            assertGetters(clazz);
        }
    }

    private Stream<Class<?>> innerRequestTypes() {
        return Stream.of(AuthRequests.class, TenantRequests.class, ApiError.class)
                .flatMap(parent -> Arrays.stream(parent.getDeclaredClasses()))
                .filter(clazz -> !clazz.isEnum());
    }

    private void assertGetters(Class<?> clazz) throws Exception {
        Set<String> properties = Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());
        if (BaseTenantRequest.class.isAssignableFrom(clazz)) {
            properties.add("tenantId");
        }
        Map<String, PropertyDescriptor> descriptors = Arrays.stream(Introspector.getBeanInfo(clazz).getPropertyDescriptors())
                .collect(Collectors.toMap(PropertyDescriptor::getName, Function.identity()));
        for (String property : properties) {
            PropertyDescriptor descriptor = descriptors.get(property);
            assertThat(descriptor)
                    .as("Missing getter for %s.%s", clazz.getSimpleName(), property)
                    .isNotNull();
            assertThat(descriptor.getReadMethod())
                    .as("Missing getter for %s.%s", clazz.getSimpleName(), property)
                    .isNotNull();
        }
    }
}
