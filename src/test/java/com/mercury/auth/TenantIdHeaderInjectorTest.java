package com.mercury.auth;

import com.mercury.auth.config.TenantIdHeaderInjector;
import com.mercury.auth.dto.AuthRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Type;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TenantIdHeaderInjector
 */
public class TenantIdHeaderInjectorTest {

    private HttpServletRequest request;
    private TenantIdHeaderInjector injector;
    private MethodParameter methodParameter;
    private HttpInputMessage inputMessage;

    @BeforeEach
    void setup() {
        request = Mockito.mock(HttpServletRequest.class);
        injector = new TenantIdHeaderInjector(request);
        methodParameter = Mockito.mock(MethodParameter.class);
        inputMessage = Mockito.mock(HttpInputMessage.class);
    }

    @Test
    void supports_BaseTenantRequest() {
        Mockito.when(methodParameter.getParameterType())
                .thenReturn((Class) AuthRequests.PasswordLogin.class);
        
        boolean result = injector.supports(
                methodParameter,
                AuthRequests.PasswordLogin.class,
                null);
        
        assertThat(result).isTrue();
    }

    @Test
    void afterBodyRead_injectsHeaderValue() {
        Mockito.when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-from-header");
        
        AuthRequests.PasswordLogin body = new AuthRequests.PasswordLogin();
        body.setTenantId("tenant-from-body");
        body.setUsername("user");
        body.setPassword("pass");
        
        Object result = injector.afterBodyRead(
                body,
                inputMessage,
                methodParameter,
                AuthRequests.PasswordLogin.class,
                null);
        
        assertThat(result).isInstanceOf(AuthRequests.PasswordLogin.class);
        AuthRequests.PasswordLogin modifiedBody = (AuthRequests.PasswordLogin) result;
        assertThat(modifiedBody.getTenantId()).isEqualTo("tenant-from-header");
        assertThat(modifiedBody.getUsername()).isEqualTo("user");
        assertThat(modifiedBody.getPassword()).isEqualTo("pass");
    }

    @Test
    void afterBodyRead_keepsBodyValueWhenNoHeader() {
        Mockito.when(request.getHeader("X-Tenant-Id")).thenReturn(null);
        
        AuthRequests.PasswordLogin body = new AuthRequests.PasswordLogin();
        body.setTenantId("tenant-from-body");
        body.setUsername("user");
        body.setPassword("pass");
        
        Object result = injector.afterBodyRead(
                body,
                inputMessage,
                methodParameter,
                AuthRequests.PasswordLogin.class,
                null);
        
        assertThat(result).isInstanceOf(AuthRequests.PasswordLogin.class);
        AuthRequests.PasswordLogin modifiedBody = (AuthRequests.PasswordLogin) result;
        assertThat(modifiedBody.getTenantId()).isEqualTo("tenant-from-body");
    }

    @Test
    void afterBodyRead_keepsBodyValueWhenHeaderIsEmpty() {
        Mockito.when(request.getHeader("X-Tenant-Id")).thenReturn("");
        
        AuthRequests.PasswordLogin body = new AuthRequests.PasswordLogin();
        body.setTenantId("tenant-from-body");
        body.setUsername("user");
        body.setPassword("pass");
        
        Object result = injector.afterBodyRead(
                body,
                inputMessage,
                methodParameter,
                AuthRequests.PasswordLogin.class,
                null);
        
        assertThat(result).isInstanceOf(AuthRequests.PasswordLogin.class);
        AuthRequests.PasswordLogin modifiedBody = (AuthRequests.PasswordLogin) result;
        assertThat(modifiedBody.getTenantId()).isEqualTo("tenant-from-body");
    }

    @Test
    void afterBodyRead_injectsHeaderValueEvenWhenBodyIsNull() {
        Mockito.when(request.getHeader("X-Tenant-Id")).thenReturn("tenant-from-header");
        
        AuthRequests.PasswordLogin body = new AuthRequests.PasswordLogin();
        body.setUsername("user");
        body.setPassword("pass");
        // tenantId is null in body
        
        Object result = injector.afterBodyRead(
                body,
                inputMessage,
                methodParameter,
                AuthRequests.PasswordLogin.class,
                null);
        
        assertThat(result).isInstanceOf(AuthRequests.PasswordLogin.class);
        AuthRequests.PasswordLogin modifiedBody = (AuthRequests.PasswordLogin) result;
        assertThat(modifiedBody.getTenantId()).isEqualTo("tenant-from-header");
    }
}
