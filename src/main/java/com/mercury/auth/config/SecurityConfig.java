package com.mercury.auth.config;

import com.mercury.auth.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the Mercury Auth API service.
 * This is a stateless REST API using JWT tokens for authentication.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF protection is disabled because:
        // 1. This is a stateless REST API using JWT tokens (not session cookies)
        // 2. All authentication is done via Bearer tokens in Authorization header
        // 3. The API is not rendered in a browser with forms
        // For stateless JWT authentication, CSRF protection is not required
        http.csrf().disable()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                // Public endpoints - authentication APIs
                // Login, registration, and code sending endpoints are public
                .antMatchers("/api/auth/login-**").permitAll()
                .antMatchers("/api/auth/register-**").permitAll()
                .antMatchers("/api/auth/send-**").permitAll()
                .antMatchers("/api/auth/verify-**").permitAll()
                .antMatchers("/api/auth/wechat-**").permitAll()
                .antMatchers("/api/auth/refresh-token").permitAll()
                .antMatchers("/api/auth/verify-token").permitAll()
                .antMatchers("/api/auth/captcha").permitAll()
                .antMatchers("/api/auth/forgot-password").permitAll()
                .antMatchers("/api/auth/reset-password").permitAll()
                // Swagger/OpenAPI endpoints - configured via springdoc.swagger-ui.enabled
                .antMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Actuator health endpoints
                .antMatchers("/actuator/**").permitAll()
                // Protected endpoints requiring JWT authentication
                // Logout requires valid JWT token
                .antMatchers("/api/auth/logout").authenticated()
                .antMatchers("/api/auth/change-password").authenticated()
                .antMatchers("/api/auth/user-status").authenticated()
                // Tenant management APIs - require authentication
                // These are administrative operations that should not be publicly accessible
                .antMatchers("/api/tenants/**").authenticated()
                // All other /api/** endpoints require authentication
                .anyRequest().authenticated()
                .and()
                // Add JWT authentication filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
