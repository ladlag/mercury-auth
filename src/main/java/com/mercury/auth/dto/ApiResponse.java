package com.mercury.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private String code;
    private String message;
    private T data;

    /**
     * Create a success response with data
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("200000", "success", data);
    }

    /**
     * Create a success response with data and custom message
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>("200000", message, data);
    }

    /**
     * Create a success response with only message (no data)
     */
    public static <T> ApiResponse<T> successWithMessage(String message) {
        return new ApiResponse<>("200000", message, null);
    }
}
