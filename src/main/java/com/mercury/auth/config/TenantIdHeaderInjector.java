package com.mercury.auth.config;

import com.mercury.auth.dto.BaseTenantRequest;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.service.TenantService;
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
 * - Validates X-Tenant-Id header format (alphanumeric, underscore, hyphen, 1-50 chars)
 * - Validates tenant exists and is enabled in the database (security critical)
 * - Protected endpoints: JwtAuthenticationFilter additionally validates header == JWT token's tenantId
 * 
 * Benefits:
 * - Consistent API design - same header requirement for all endpoints
 * - Simpler client code - always include X-Tenant-Id header
 * - Better security - tenant context is explicit and validated
 * - Early validation prevents processing requests for non-existent/disabled tenants
 * 
 * If X-Tenant-Id header is missing, an ApiException is thrown with MISSING_TENANT_HEADER error code.
 */
@ControllerAdvice
public class TenantIdHeaderInjector extends RequestBodyAdviceAdapter {

    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    // Pattern for valid tenant IDs: alphanumeric, underscore, hyphen, 1-50 chars
    private static final String TENANT_ID_PATTERN = "^[a-zA-Z0-9_-]{1,50}$";
    
    private final HttpServletRequest request;
    private final TenantService tenantService;

    public TenantIdHeaderInjector(HttpServletRequest request, TenantService tenantService) {
        this.request = request;
        this.tenantService = tenantService;
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
            
            // Validate tenant ID format to prevent injection attacks
            if (!headerTenantId.matches(TENANT_ID_PATTERN)) {
                throw new ApiException(ErrorCodes.VALIDATION_FAILED, 
                    "X-Tenant-Id header contains invalid characters");
            }
            
            // SECURITY: Validate tenant exists and is enabled BEFORE processing any request
            // This prevents attacks using non-existent tenant IDs to bypass rate limiting
            // or enumerate tenants. This is a critical security control.
            tenantService.requireEnabled(headerTenantId);
            
            // Inject tenantId from header (overwrites any value from body)
            tenantRequest.setTenantId(headerTenantId);
        }
        
        return body;
    }
}
