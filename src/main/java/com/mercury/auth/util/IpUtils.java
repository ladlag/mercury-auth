package com.mercury.auth.util;

import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Utility class for extracting client IP addresses from HTTP requests
 * 
 * SECURITY NOTICE:
 * This utility trusts proxy headers (X-Forwarded-For, X-Real-IP, etc.) which can be
 * spoofed by clients if the application is not behind a trusted reverse proxy.
 * 
 * For production deployments:
 * 1. Ensure application is behind a trusted reverse proxy (nginx, HAProxy, AWS ALB, etc.)
 * 2. Configure proxy to strip/override client-provided X-Forwarded-For headers
 * 3. Use firewall rules to restrict direct access to application ports
 * 4. Monitor audit logs for suspicious IP patterns
 * 
 * Example nginx configuration to prevent IP spoofing:
 * ```
 * proxy_set_header X-Real-IP $remote_addr;
 * proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
 * ```
 */
public final class IpUtils {

    private IpUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Get the real client IP address from the current HTTP request.
     * Handles proxy headers like X-Forwarded-For, X-Real-IP, etc.
     *
     * @return Client IP address, or "unknown" if not available
     */
    public static String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "unknown";
            }
            HttpServletRequest request = attributes.getRequest();
            return getClientIp(request);
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Get the real client IP address from an HTTP request.
     * Handles proxy headers like X-Forwarded-For, X-Real-IP, etc.
     * 
     * SECURITY NOTICE:
     * This method trusts proxy headers which can be spoofed if not behind a trusted proxy.
     * For production: Deploy behind nginx/HAProxy/ALB that strips client X-Forwarded-For.
     * 
     * Priority order:
     * 1. X-Forwarded-For (standard, takes first IP = original client)
     * 2. X-Real-IP (nginx standard)
     * 3. Proxy-Client-IP / WL-Proxy-Client-IP (Apache/WebLogic)
     * 4. HTTP_CLIENT_IP / HTTP_X_FORWARDED_FOR (legacy)
     * 5. request.getRemoteAddr() (direct connection)
     *
     * @param request HTTP request
     * @return Client IP address, or "unknown" if not available
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        // Check common proxy headers in order of preference
        String ip = getIpFromHeader(request, "X-Forwarded-For");
        if (isValidIp(ip)) {
            // X-Forwarded-For format: "client-ip, proxy1-ip, proxy2-ip"
            // Take the first IP (original client)
            int commaIndex = ip.indexOf(',');
            if (commaIndex > 0) {
                ip = ip.substring(0, commaIndex).trim();
            }
            // Additional validation to prevent injection
            return isValidIpAddress(ip) ? ip : "unknown";
        }

        ip = getIpFromHeader(request, "X-Real-IP");
        if (isValidIp(ip) && isValidIpAddress(ip)) {
            return ip;
        }

        ip = getIpFromHeader(request, "Proxy-Client-IP");
        if (isValidIp(ip) && isValidIpAddress(ip)) {
            return ip;
        }

        ip = getIpFromHeader(request, "WL-Proxy-Client-IP");
        if (isValidIp(ip) && isValidIpAddress(ip)) {
            return ip;
        }

        ip = getIpFromHeader(request, "HTTP_CLIENT_IP");
        if (isValidIp(ip) && isValidIpAddress(ip)) {
            return ip;
        }

        ip = getIpFromHeader(request, "HTTP_X_FORWARDED_FOR");
        if (isValidIp(ip) && isValidIpAddress(ip)) {
            return ip;
        }

        // Fall back to remote address (direct connection)
        ip = request.getRemoteAddr();
        return isValidIp(ip) && isValidIpAddress(ip) ? ip : "unknown";
    }

    private static String getIpFromHeader(HttpServletRequest request, String header) {
        String ip = request.getHeader(header);
        return StringUtils.hasText(ip) ? ip.trim() : null;
    }

    private static boolean isValidIp(String ip) {
        return StringUtils.hasText(ip) 
                && !"unknown".equalsIgnoreCase(ip);
    }
    
    /**
     * Validate IP address format to prevent injection attacks
     * Supports both IPv4 and IPv6 formats
     * 
     * @param ip IP address string to validate
     * @return true if IP format is valid, false otherwise
     */
    private static boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        try {
            // Use InetAddress for proper IPv4/IPv6 validation
            // This handles edge cases like ':::' or '...' that regex might miss
            java.net.InetAddress.getByName(ip);
            return true;
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }
}
