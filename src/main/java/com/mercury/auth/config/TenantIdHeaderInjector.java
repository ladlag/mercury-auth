package com.mercury.auth.config;

import com.mercury.auth.dto.BaseTenantRequest;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Type;

/**
 * Intercepts request body deserialization and injects tenantId from X-Tenant-Id HTTP header
 * into BaseTenantRequest instances.
 * 
 * Design Philosophy:
 * - ALL endpoints (both public and protected) require X-Tenant-Id header for consistency
 * - The header value is always injected into request body's tenantId field
 * - Any tenantId in request body JSON is IGNORED and overwritten
 * 
 * Validation:
 * - Public endpoints (login, register, etc.): Just validate header exists
 * - Protected endpoints (logout, user management, etc.): JwtAuthenticationFilter validates header == JWT token's tenantId
 * 
 * Benefits:
 * - Consistent API design - same header requirement for all endpoints
 * - Simpler client code - always include X-Tenant-Id header
 * - Better security - tenant context is explicit and validated
 * 
 * If X-Tenant-Id header is missing, an ApiException is thrown with MISSING_TENANT_HEADER error code.
 */
@ControllerAdvice
public class TenantIdHeaderInjector extends RequestBodyAdviceAdapter {

    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    
    private final HttpServletRequest request;

    public TenantIdHeaderInjector(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, 
                           Type targetType,
                           Class<? extends HttpMessageConverter<?>> converterType) {
        // Support BaseTenantRequest and all its subclasses
        return methodParameter.getParameterType() != null &&
               BaseTenantRequest.class.isAssignableFrom(methodParameter.getParameterType());
    }

    @Override
    public Object afterBodyRead(Object body, 
                               HttpInputMessage inputMessage,
                               MethodParameter parameter, 
                               Type targetType,
                               Class<? extends HttpMessageConverter<?>> converterType) {
        
        if (body instanceof BaseTenantRequest) {
            BaseTenantRequest tenantRequest = (BaseTenantRequest) body;
            String headerTenantId = request.getHeader(TENANT_ID_HEADER);
            
            // X-Tenant-Id header is REQUIRED for ALL endpoints
            if (!StringUtils.hasText(headerTenantId)) {
                throw new ApiException(ErrorCodes.MISSING_TENANT_HEADER, 
                    "X-Tenant-Id header is required for all requests");
            }
            
            // Inject tenantId from header (overwrites any value from body)
            tenantRequest.setTenantId(headerTenantId);
        }
        
        return body;
    }
}
