package com.mercury.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercury.auth.dto.ApiResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSuccessWithData() throws Exception {
        // Arrange
        String testData = "test data";
        
        // Act
        ApiResponse<String> response = ApiResponse.success(testData);
        
        // Assert
        assertThat(response.getCode()).isEqualTo("200000");
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isEqualTo(testData);
        
        // Verify JSON serialization
        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"code\":\"200000\"");
        assertThat(json).contains("\"message\":\"success\"");
        assertThat(json).contains("\"data\":\"test data\"");
    }

    @Test
    void testSuccessWithDataAndMessage() throws Exception {
        // Arrange
        String testData = "test data";
        String customMessage = "custom success message";
        
        // Act
        ApiResponse<String> response = ApiResponse.success(testData, customMessage);
        
        // Assert
        assertThat(response.getCode()).isEqualTo("200000");
        assertThat(response.getMessage()).isEqualTo(customMessage);
        assertThat(response.getData()).isEqualTo(testData);
    }

    @Test
    void testSuccessWithMessageOnly() throws Exception {
        // Arrange
        String message = "operation completed";
        
        // Act
        ApiResponse<Void> response = ApiResponse.successWithMessage(message);
        
        // Assert
        assertThat(response.getCode()).isEqualTo("200000");
        assertThat(response.getMessage()).isEqualTo(message);
        assertThat(response.getData()).isNull();
        
        // Verify JSON serialization excludes null data field
        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"code\":\"200000\"");
        assertThat(json).contains("\"message\":\"operation completed\"");
        // data field should be excluded when null (due to @JsonInclude(NON_NULL))
        assertThat(json).doesNotContain("\"data\"");
    }

    @Test
    void testSuccessWithComplexData() throws Exception {
        // Arrange
        TestData testData = new TestData("user123", "test@example.com");
        
        // Act
        ApiResponse<TestData> response = ApiResponse.success(testData);
        
        // Assert
        assertThat(response.getCode()).isEqualTo("200000");
        assertThat(response.getData().userId).isEqualTo("user123");
        assertThat(response.getData().email).isEqualTo("test@example.com");
        
        // Verify JSON serialization
        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"userId\":\"user123\"");
        assertThat(json).contains("\"email\":\"test@example.com\"");
    }

    // Test data class
    static class TestData {
        public String userId;
        public String email;
        
        public TestData(String userId, String email) {
            this.userId = userId;
            this.email = email;
        }
    }
}
