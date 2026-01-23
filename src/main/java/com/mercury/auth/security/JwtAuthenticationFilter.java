package com.mercury.auth.security;

import com.mercury.auth.config.SecurityConstants;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.service.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * JWT authentication filter that validates Bearer tokens on protected endpoints.
 * Extracts JWT from Authorization header, validates it, and sets authentication context.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Skip JWT authentication for public endpoints
        String requestPath = request.getServletPath();
        if (SecurityConstants.isPublicEndpoint(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        
        // If no Authorization header or already authenticated, continue
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX) || 
            SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(BEARER_PREFIX.length());
            
            // Parse and validate token
            Claims claims = jwtService.parse(token);
            
            // Extract user information
            Object tenantIdObj = claims.get("tenantId");
            Object userIdObj = claims.get("userId");
            Object usernameObj = claims.get("username");
            
            if (tenantIdObj == null || userIdObj == null || usernameObj == null) {
                logger.warn("JWT missing required claims");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"INVALID_TOKEN\",\"message\":\"invalid token\"}");
                return;
            }
            
            String tokenTenantId = String.valueOf(tenantIdObj);
            
            // CRITICAL: Validate tenant match with X-Tenant-Id header
            // The header is mandatory for multi-tenant isolation
            String headerTenantId = request.getHeader(TENANT_ID_HEADER);
            if (headerTenantId == null) {
                logger.warn("Missing X-Tenant-Id header for authenticated request from user={}", usernameObj);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"MISSING_TENANT_HEADER\",\"message\":\"X-Tenant-Id header is required\"}");
                return;
            }
            
            if (!headerTenantId.equals(tokenTenantId)) {
                logger.warn("Tenant mismatch: header={} token={}", headerTenantId, tokenTenantId);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"TENANT_MISMATCH\",\"message\":\"tenant mismatch\"}");
                return;
            }
            
            Long userId = Long.valueOf(String.valueOf(userIdObj));
            String username = String.valueOf(usernameObj);
            
            // Create authentication token with user details
            JwtUserDetails userDetails = new JwtUserDetails(tokenTenantId, userId, username);
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(userDetails, null, Collections.emptyList());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            
            // Set authentication in security context
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            logger.debug("JWT authentication successful for user={} tenant={}", username, tokenTenantId);
            
        } catch (JwtException e) {
            logger.warn("JWT validation failed: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"INVALID_TOKEN\",\"message\":\"invalid token\"}");
            return;
        } catch (Exception e) {
            logger.error("JWT filter error: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"AUTHENTICATION_FAILED\",\"message\":\"authentication failed\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
    
    /**
     * Simple user details class to hold JWT claims
     */
    public static class JwtUserDetails {
        private final String tenantId;
        private final Long userId;
        private final String username;

        public JwtUserDetails(String tenantId, Long userId, String username) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.username = username;
        }

        public String getTenantId() {
            return tenantId;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }
    }
}
