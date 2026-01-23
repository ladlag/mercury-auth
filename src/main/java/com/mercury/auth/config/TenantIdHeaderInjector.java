package com.mercury.auth.config;

import com.mercury.auth.dto.BaseTenantRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Type;

/**
 * Intercepts request body deserialization and injects tenantId from HTTP header
 * (X-Tenant-Id) into BaseTenantRequest instances if present.
 * 
 * Priority: Header value takes precedence over body/parameter value.
 * If header is not present, body/parameter value is used (maintains backward compatibility).
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
            
            // If header contains tenantId, use it (takes precedence)
            // Otherwise, keep the value from body (backward compatible)
            if (StringUtils.hasText(headerTenantId)) {
                tenantRequest.setTenantId(headerTenantId);
            }
        }
        
        return body;
    }
}
