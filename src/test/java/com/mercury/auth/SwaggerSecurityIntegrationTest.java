package com.mercury.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test to verify Swagger UI and API documentation endpoints
 * are accessible without authentication (public endpoints).
 * 
 * This test ensures that the security configuration correctly allows
 * Swagger UI and API documentation to be accessed without JWT tokens.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SwaggerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testSwaggerUiHtmlIsAccessibleWithoutAuth() throws Exception {
        // Test the main Swagger UI entry point
        // It typically redirects (302) to /swagger-ui/index.html
        // The key is it should NOT require authentication (401/403)
        MvcResult result = mockMvc.perform(get("/swagger-ui.html"))
                .andReturn();
        
        int status = result.getResponse().getStatus();
        assertThat(status)
                .as("Swagger UI should be accessible without authentication")
                .isNotIn(401, 403);
    }

    @Test
    void testSwaggerUiIndexIsAccessibleWithoutAuth() throws Exception {
        // Test that Swagger UI index doesn't require authentication
        MvcResult result = mockMvc.perform(get("/swagger-ui/index.html"))
                .andReturn();
        
        int status = result.getResponse().getStatus();
        assertThat(status)
                .as("Swagger UI index should be accessible without authentication")
                .isNotIn(401, 403);
    }

    @Test
    void testApiDocsIsAccessibleWithoutAuth() throws Exception {
        // Test the OpenAPI docs endpoint is accessible without authentication
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andReturn();
        
        int status = result.getResponse().getStatus();
        assertThat(status)
                .as("API docs should be accessible without authentication")
                .isNotIn(401, 403);
    }

    @Test
    void testSwaggerUiStylesheetsAreAccessibleWithoutAuth() throws Exception {
        // Test that Swagger UI static resources don't require authentication
        // Swagger UI typically loads CSS/JS from /swagger-ui/ path
        MvcResult result = mockMvc.perform(get("/swagger-ui/swagger-ui.css"))
                .andReturn();
        
        int status = result.getResponse().getStatus();
        // Should not be 401 or 403 (may be 404 if file doesn't exist, which is OK)
        assertThat(status)
                .as("Swagger UI resources should be accessible without authentication")
                .isNotIn(401, 403);
    }

    @Test
    void testProtectedEndpointRequiresAuth() throws Exception {
        // Verify that protected endpoints require authentication
        // Should return 401 (unauthorized) or 403 (forbidden)
        mockMvc.perform(get("/api/auth/logout"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testTenantEndpointRequiresAuth() throws Exception {
        // Verify that tenant endpoints require authentication
        // Should return 401 (unauthorized) or 403 (forbidden)
        mockMvc.perform(get("/api/tenants/list"))
                .andExpect(status().is4xxClientError());
    }
}
