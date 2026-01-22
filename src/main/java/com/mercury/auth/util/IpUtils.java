package com.mercury.auth.util;

import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Utility class for extracting client IP addresses from HTTP requests
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
            // X-Forwarded-For can contain multiple IPs, take the first one (original client)
            int commaIndex = ip.indexOf(',');
            if (commaIndex > 0) {
                ip = ip.substring(0, commaIndex).trim();
            }
            return ip;
        }

        ip = getIpFromHeader(request, "X-Real-IP");
        if (isValidIp(ip)) {
            return ip;
        }

        ip = getIpFromHeader(request, "Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip;
        }

        ip = getIpFromHeader(request, "WL-Proxy-Client-IP");
        if (isValidIp(ip)) {
            return ip;
        }

        ip = getIpFromHeader(request, "HTTP_CLIENT_IP");
        if (isValidIp(ip)) {
            return ip;
        }

        ip = getIpFromHeader(request, "HTTP_X_FORWARDED_FOR");
        if (isValidIp(ip)) {
            return ip;
        }

        // Fall back to remote address
        ip = request.getRemoteAddr();
        return isValidIp(ip) ? ip : "unknown";
    }

    private static String getIpFromHeader(HttpServletRequest request, String header) {
        String ip = request.getHeader(header);
        return StringUtils.hasText(ip) ? ip.trim() : null;
    }

    private static boolean isValidIp(String ip) {
        return StringUtils.hasText(ip) 
                && !"unknown".equalsIgnoreCase(ip)
                && !"0:0:0:0:0:0:0:1".equals(ip);  // IPv6 localhost
    }
}
