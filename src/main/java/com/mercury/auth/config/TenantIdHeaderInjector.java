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
 * For protected endpoints (those requiring JWT authentication):
 * - X-Tenant-Id header is mandatory and validated by JwtAuthenticationFilter
 * - Header value is automatically injected into request body's tenantId field
 * - Any tenantId value in the request body JSON is IGNORED and overwritten
 * 
 * For public endpoints (those not requiring authentication):
 * - X-Tenant-Id header is optional
 * - If header is present, it's injected into the body
 * - If header is absent, body value is used (backward compatible)
 * 
 * This ensures consistent tenant isolation - the authenticated tenant from JWT
 * is always enforced via the header injection mechanism.
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
            
            // Inject tenantId from header
            // For protected endpoints: header is mandatory (enforced by JwtAuthenticationFilter)
            // For public endpoints: header is optional, fall back to body value if absent
            if (StringUtils.hasText(headerTenantId)) {
                tenantRequest.setTenantId(headerTenantId);
            }
            // Note: If header is absent and body tenantId is also null/empty,
            // the service layer will handle the validation error
        }
        
        return body;
    }
}
