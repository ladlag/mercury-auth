package com.mercury.auth.controller;

import com.mercury.auth.dto.ApiResponse;
import com.mercury.auth.entity.IpBlacklist;
import com.mercury.auth.security.JwtAuthenticationFilter;
import com.mercury.auth.service.BlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Admin endpoints for managing multi-dimensional blacklists
 * 
 * Blacklist trigger conditions:
 * 1. Token blacklist: Automatically triggered by logout, token refresh
 * 2. IP blacklist: Can be triggered by:
 *    - Multiple failed login attempts (auto-blacklist)
 *    - Rate limit violations (auto-blacklist)
 *    - Admin manual blacklist for security policy enforcement
 *    - Suspicious activity patterns detected by security monitoring
 * 
 * Supported blacklist dimensions:
 * - Token blacklist (managed by TokenService)
 * - IP blacklist (global or tenant-specific)
 */
@RestController
@RequestMapping("/api/v1/admin/blacklist")
@RequiredArgsConstructor
@Tag(name = "Blacklist Management", description = "Admin endpoints for managing authentication blacklists")
public class BlacklistController {
    
    private final BlacklistService blacklistService;
    
    @PostMapping("/ip")
    @Operation(summary = "Add IP to blacklist", 
               description = "Add an IP address to blacklist (global or tenant-specific). " +
                           "Use null tenantId for global blacklist.")
    public ResponseEntity<ApiResponse<String>> addIpBlacklist(@Valid @RequestBody AddIpBlacklistRequest request) {
        // Get authenticated user from security context (or use "SYSTEM" for unauthenticated)
        String createdBy = getCurrentUsername().orElse("SYSTEM");
        
        blacklistService.addIpBlacklist(
            request.getIpAddress(),
            request.getTenantId(),
            request.getReason(),
            request.getExpiresAt(),
            createdBy
        );
        return ResponseEntity.ok(ApiResponse.success("IP blacklist added successfully"));
    }
    
    @DeleteMapping("/ip")
    @Operation(summary = "Remove IP from blacklist",
               description = "Remove an IP address from blacklist")
    public ResponseEntity<ApiResponse<String>> removeIpBlacklist(
            @Parameter(description = "IP address to remove") @RequestParam String ipAddress,
            @Parameter(description = "Tenant ID (null for global)") @RequestParam(required = false) String tenantId) {
        blacklistService.removeIpBlacklist(ipAddress, tenantId);
        return ResponseEntity.ok(ApiResponse.success("IP blacklist removed successfully"));
    }
    
    @GetMapping("/ip")
    @Operation(summary = "List IP blacklists",
               description = "List all IP blacklist entries for a tenant or global")
    public ResponseEntity<ApiResponse<List<IpBlacklist>>> listIpBlacklist(
            @Parameter(description = "Tenant ID (null for global)") @RequestParam(required = false) String tenantId) {
        List<IpBlacklist> entries = blacklistService.listIpBlacklist(tenantId);
        return ResponseEntity.ok(ApiResponse.success(entries));
    }
    
    @GetMapping("/ip/check")
    @Operation(summary = "Check if IP is blacklisted",
               description = "Check if an IP address is blacklisted (global or tenant-specific)")
    public ResponseEntity<ApiResponse<IpBlacklistCheckResponse>> checkIpBlacklist(
            @Parameter(description = "IP address to check") @RequestParam String ipAddress,
            @Parameter(description = "Tenant ID (null for global only)") @RequestParam(required = false) String tenantId) {
        
        boolean globalBlacklisted = blacklistService.isIpBlacklisted(ipAddress, null);
        boolean tenantBlacklisted = tenantId != null && blacklistService.isIpBlacklisted(ipAddress, tenantId);
        
        IpBlacklistCheckResponse response = new IpBlacklistCheckResponse();
        response.setIpAddress(ipAddress);
        response.setGlobalBlacklisted(globalBlacklisted);
        response.setTenantBlacklisted(tenantBlacklisted);
        response.setBlacklisted(globalBlacklisted || tenantBlacklisted);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @DeleteMapping("/ip/cleanup")
    @Operation(summary = "Clean up expired IP blacklist entries",
               description = "Remove expired IP blacklist entries from database")
    public ResponseEntity<ApiResponse<String>> cleanupExpiredIpBlacklist() {
        int count = blacklistService.cleanupExpiredIpBlacklist();
        return ResponseEntity.ok(ApiResponse.success(String.format("Cleaned up %d expired entries", count)));
    }
    
    /**
     * Get the current authenticated username from security context
     * Supports multiple authentication types for flexibility
     */
    private Optional<String> getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() != null) {
                Object principal = authentication.getPrincipal();
                
                // Handle JwtUserDetails from JWT authentication
                if (principal instanceof JwtAuthenticationFilter.JwtUserDetails) {
                    return Optional.of(((JwtAuthenticationFilter.JwtUserDetails) principal).getUsername());
                }
                
                // Handle Spring Security UserDetails
                if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                    return Optional.of(((org.springframework.security.core.userdetails.UserDetails) principal).getUsername());
                }
                
                // Fallback to principal's toString() for other authentication types
                String principalString = principal.toString();
                if (principalString != null && !principalString.isEmpty() && !"anonymousUser".equals(principalString)) {
                    return Optional.of(principalString);
                }
            }
        } catch (Exception e) {
            // Failed to get username, will use default
        }
        return Optional.empty();
    }
    
    // DTOs
    
    @Data
    public static class AddIpBlacklistRequest {
        @NotBlank(message = "IP address is required")
        private String ipAddress;
        
        private String tenantId; // null for global blacklist
        
        @NotBlank(message = "Reason is required")
        private String reason;
        
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        private LocalDateTime expiresAt; // null for permanent blacklist
    }
    
    @Data
    public static class IpBlacklistCheckResponse {
        private String ipAddress;
        private boolean blacklisted;
        private boolean globalBlacklisted;
        private boolean tenantBlacklisted;
    }
}
