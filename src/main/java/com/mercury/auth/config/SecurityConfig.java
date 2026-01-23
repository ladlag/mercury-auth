package com.mercury.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the Mercury Auth API service.
 * This is a stateless REST API using JWT tokens for authentication.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF protection is disabled because:
        // 1. This is a stateless REST API using JWT tokens (not session cookies)
        // 2. All authentication is done via Bearer tokens in Authorization header
        // 3. The API is not rendered in a browser with forms
        // For stateless JWT authentication, CSRF protection is not required
        http.csrf().disable()
                .authorizeRequests()
                // Public endpoints - authentication APIs
                .antMatchers("/api/auth/**").permitAll()
                // Swagger/OpenAPI endpoints - configured via springdoc.swagger-ui.enabled
                .antMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Actuator health endpoints
                .antMatchers("/actuator/**").permitAll()
                // Tenant management APIs - require authentication
                // These are administrative operations that should not be publicly accessible
                .antMatchers("/api/tenants/**").authenticated()
                // All other endpoints require authentication
                .anyRequest().authenticated()
                .and()
                .httpBasic();  // Enable HTTP Basic authentication as fallback
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
