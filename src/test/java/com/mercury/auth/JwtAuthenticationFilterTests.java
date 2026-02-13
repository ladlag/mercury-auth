package com.mercury.auth;

import com.mercury.auth.security.JwtAuthenticationFilter;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.service.BlacklistService;
import com.mercury.auth.service.TokenCacheService;
import com.mercury.auth.service.TokenService;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.FilterChain;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JwtAuthenticationFilterTests {

    private static final long EXPIRATION_OFFSET_SECONDS = 30;

    @Test
    public void testCachedExpiredTokenIsRejected() throws Exception {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                "tokenCache",
                "tokenVerifyCache",
                "tenantStatusCache",
                "userStatusCache"
        );
        TokenCacheService tokenCacheService = new TokenCacheService(cacheManager);

        String token = "expired-token";
        String tokenHash = tokenCacheService.hashToken(token);

        DefaultClaims claims = new DefaultClaims();
        claims.put("tenantId", "tenant1");
        claims.put("userId", 1L);
        claims.put("username", "user1");
        claims.setExpiration(Date.from(Instant.now().minusSeconds(EXPIRATION_OFFSET_SECONDS)));
        tokenCacheService.cacheClaims(tokenHash, claims);

        JwtService jwtService = mock(JwtService.class);
        TokenService tokenService = mock(TokenService.class);
        BlacklistService blacklistService = mock(BlacklistService.class);
        when(tokenService.isTokenHashBlacklisted(tokenHash)).thenReturn(false);
        when(jwtService.isExpired(any())).thenReturn(true);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, tokenService, tokenCacheService, blacklistService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/tenants/list");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Tenant-Id", "tenant1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try {
            filter.doFilter(request, response, chain);
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertEquals(401, response.getStatus());
        assertNull(tokenCacheService.getCachedClaims(tokenHash));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, never()).doFilter(any(), any());
        verify(jwtService, never()).parse(anyString());
    }

    @Test
    public void testBlacklistedTokenIsRejected() throws Exception {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                "tokenCache",
                "tokenVerifyCache",
                "tenantStatusCache",
                "userStatusCache"
        );
        TokenCacheService tokenCacheService = new TokenCacheService(cacheManager);

        String token = "blacklisted-token";
        String tokenHash = tokenCacheService.hashToken(token);

        JwtService jwtService = mock(JwtService.class);
        TokenService tokenService = mock(TokenService.class);
        BlacklistService blacklistService = mock(BlacklistService.class);
        when(tokenService.isTokenHashBlacklisted(tokenHash)).thenReturn(true);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, tokenService, tokenCacheService, blacklistService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/tenants/list");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Tenant-Id", "tenant1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try {
            filter.doFilter(request, response, chain);
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, never()).doFilter(any(), any());
        verify(jwtService, never()).parse(anyString());
    }

    @Test
    public void testBlacklistedTokenAllowedForLogout() throws Exception {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                "tokenCache",
                "tokenVerifyCache",
                "tenantStatusCache",
                "userStatusCache"
        );
        TokenCacheService tokenCacheService = new TokenCacheService(cacheManager);

        String token = "blacklisted-token";
        String tokenHash = tokenCacheService.hashToken(token);

        DefaultClaims claims = new DefaultClaims();
        claims.put("tenantId", "tenant1");
        claims.put("userId", 1L);
        claims.put("username", "user1");
        claims.setIssuedAt(Date.from(Instant.now().minusSeconds(60)));
        claims.setExpiration(Date.from(Instant.now().plusSeconds(3600)));

        JwtService jwtService = mock(JwtService.class);
        TokenService tokenService = mock(TokenService.class);
        BlacklistService blacklistService = mock(BlacklistService.class);
        when(tokenService.isTokenHashBlacklisted(tokenHash)).thenReturn(true);
        when(jwtService.parse(token)).thenReturn(claims);
        when(jwtService.isExpired(any())).thenReturn(false);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, tokenService, tokenCacheService, blacklistService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/auth/logout");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Tenant-Id", "tenant1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try {
            filter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus());
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            verify(chain).doFilter(any(), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    public void testValidTokenPassesAllChecks() throws Exception {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                "tokenCache",
                "tokenVerifyCache",
                "tenantStatusCache",
                "userStatusCache"
        );
        TokenCacheService tokenCacheService = new TokenCacheService(cacheManager);

        String token = "valid-token";
        String tokenHash = tokenCacheService.hashToken(token);

        DefaultClaims claims = new DefaultClaims();
        claims.put("tenantId", "tenant1");
        claims.put("userId", 1L);
        claims.put("username", "user1");
        claims.setIssuedAt(Date.from(Instant.now().minusSeconds(60)));
        claims.setExpiration(Date.from(Instant.now().plusSeconds(3600)));

        JwtService jwtService = mock(JwtService.class);
        TokenService tokenService = mock(TokenService.class);
        BlacklistService blacklistService = mock(BlacklistService.class);
        when(tokenService.isTokenHashBlacklisted(tokenHash)).thenReturn(false);
        when(jwtService.parse(token)).thenReturn(claims);
        when(jwtService.isExpired(any())).thenReturn(false);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, tokenService, tokenCacheService, blacklistService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/tenants/list");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Tenant-Id", "tenant1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try {
            filter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus());
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            verify(chain).doFilter(any(), any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
