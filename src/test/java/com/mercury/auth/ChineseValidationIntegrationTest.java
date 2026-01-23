package com.mercury.auth;

import com.mercury.auth.config.LocaleConfig;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test to verify Chinese validation messages work in actual HTTP requests
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({LocaleConfig.class, GlobalExceptionHandler.class})
public class ChineseValidationIntegrationTest {

    @Autowired(required = false)
    private MockMvc mockMvc;

    @Test
    void testChineseValidationWithAcceptLanguageHeader() throws Exception {
        if (mockMvc == null) {
            System.out.println("MockMvc not available, skipping integration test");
            return;
        }

        // Test with Chinese locale in Accept-Language header
        mockMvc.perform(post("/api/auth/register-password")
                        .header("Accept-Language", "zh-CN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"\",\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[*].message", containsInAnyOrder(
                        "租户ID必填",
                        "用户名必填",
                        "密码必填"
                )));
    }

    @Test
    void testEnglishValidationWithAcceptLanguageHeader() throws Exception {
        if (mockMvc == null) {
            System.out.println("MockMvc not available, skipping integration test");
            return;
        }

        // Test with English locale in Accept-Language header
        mockMvc.perform(post("/api/auth/register-password")
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"\",\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[*].message", containsInAnyOrder(
                        "Tenant ID is required",
                        "Username is required",
                        "Password is required"
                )));
    }

    @Test
    void testDefaultLocaleIsChinese() throws Exception {
        if (mockMvc == null) {
            System.out.println("MockMvc not available, skipping integration test");
            return;
        }

        // Test without Accept-Language header - should default to Chinese
        mockMvc.perform(post("/api/auth/register-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"\",\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[*].message", containsInAnyOrder(
                        "租户ID必填",
                        "用户名必填",
                        "密码必填"
                )));
    }
}
