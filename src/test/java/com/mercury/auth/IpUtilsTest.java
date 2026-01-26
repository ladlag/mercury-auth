package com.mercury.auth;

import com.mercury.auth.util.IpUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for IpUtils IP extraction
 */
public class IpUtilsTest {

    @Test
    void testGetClientIp_WithRemoteAddr_IPv4() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        
        String ip = IpUtils.getClientIp(request);
        
        assertThat(ip).isEqualTo("192.168.1.100");
    }
    
    @Test
    void testGetClientIp_WithRemoteAddr_IPv4Localhost() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        
        String ip = IpUtils.getClientIp(request);
        
        // Should accept localhost for local development/testing
        assertThat(ip).isEqualTo("127.0.0.1");
    }
    
    @Test
    void testGetClientIp_WithRemoteAddr_IPv6() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        
        String ip = IpUtils.getClientIp(request);
        
        assertThat(ip).isEqualTo("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
    }
    
    @Test
    void testGetClientIp_WithRemoteAddr_IPv6Localhost() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("0:0:0:0:0:0:0:1");
        
        String ip = IpUtils.getClientIp(request);
        
        // Should accept IPv6 localhost for local development/testing
        assertThat(ip).isEqualTo("0:0:0:0:0:0:0:1");
    }
    
    @Test
    void testGetClientIp_WithRemoteAddr_IPv6LocalhostShort() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::1");
        
        String ip = IpUtils.getClientIp(request);
        
        // Should accept short form IPv6 localhost
        assertThat(ip).isEqualTo("::1");
    }
    
    @Test
    void testGetClientIp_WithXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1");
        request.setRemoteAddr("10.0.0.1");
        
        String ip = IpUtils.getClientIp(request);
        
        // Should prefer X-Forwarded-For over remote address
        assertThat(ip).isEqualTo("203.0.113.1");
    }
    
    @Test
    void testGetClientIp_WithXForwardedForMultiple() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1, 198.51.100.1, 192.0.2.1");
        request.setRemoteAddr("10.0.0.1");
        
        String ip = IpUtils.getClientIp(request);
        
        // Should extract first IP (original client) from X-Forwarded-For chain
        assertThat(ip).isEqualTo("203.0.113.1");
    }
    
    @Test
    void testGetClientIp_WithXRealIP() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "203.0.113.5");
        request.setRemoteAddr("10.0.0.1");
        
        String ip = IpUtils.getClientIp(request);
        
        // Should use X-Real-IP when available
        assertThat(ip).isEqualTo("203.0.113.5");
    }
    
    @Test
    void testGetClientIp_WithProxyClientIP() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Proxy-Client-IP", "203.0.113.10");
        request.setRemoteAddr("10.0.0.1");
        
        String ip = IpUtils.getClientIp(request);
        
        assertThat(ip).isEqualTo("203.0.113.10");
    }
    
    @Test
    void testGetClientIp_WithNullRequest() {
        String ip = IpUtils.getClientIp(null);
        
        assertThat(ip).isEqualTo("unknown");
    }
    
    @Test
    void testGetClientIp_WithUnknownHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "unknown");
        request.setRemoteAddr("192.168.1.1");
        
        String ip = IpUtils.getClientIp(request);
        
        // Should fall back to remote address when header is "unknown"
        assertThat(ip).isEqualTo("192.168.1.1");
    }
    
    @Test
    void testGetClientIp_WithInvalidIP() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "not-an-ip");
        request.setRemoteAddr("192.168.1.1");
        
        String ip = IpUtils.getClientIp(request);
        
        // Should fall back to remote address when header contains invalid IP
        assertThat(ip).isEqualTo("192.168.1.1");
    }
    
    @Test
    void testGetClientIp_WithEmptyHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "");
        request.addHeader("X-Real-IP", "");
        request.setRemoteAddr("192.168.1.1");
        
        String ip = IpUtils.getClientIp(request);
        
        // Should fall back to remote address when headers are empty
        assertThat(ip).isEqualTo("192.168.1.1");
    }
    
    @Test
    void testGetClientIp_WithWhitespaceHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "   ");
        request.setRemoteAddr("192.168.1.1");
        
        String ip = IpUtils.getClientIp(request);
        
        // Should fall back to remote address when headers contain only whitespace
        assertThat(ip).isEqualTo("192.168.1.1");
    }
    
    @Test
    void testGetClientIp_PreferenceOrder() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1");
        request.addHeader("X-Real-IP", "203.0.113.2");
        request.addHeader("Proxy-Client-IP", "203.0.113.3");
        request.setRemoteAddr("10.0.0.1");
        
        String ip = IpUtils.getClientIp(request);
        
        // X-Forwarded-For should have highest priority
        assertThat(ip).isEqualTo("203.0.113.1");
    }
    
    @Test
    void testGetClientIp_WithXForwardedForContainingSpaces() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "  203.0.113.1  ,  198.51.100.1  ");
        request.setRemoteAddr("10.0.0.1");
        
        String ip = IpUtils.getClientIp(request);
        
        // Should trim whitespace from IP addresses
        assertThat(ip).isEqualTo("203.0.113.1");
    }
}
