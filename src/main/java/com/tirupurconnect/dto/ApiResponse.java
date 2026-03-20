package com.tirupurconnect.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final int     status;
    private final String  message;
    private final T       data;
    private final Map<String, String> errors;
    private final Instant timestamp = Instant.now();

    private ApiResponse(boolean success, int status, String message, T data, Map<String, String> errors) {
        this.success = success;
        this.status  = status;
        this.message = message;
        this.data    = data;
        this.errors  = errors;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, 200, "OK", data, null);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, 200, message, data, null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, 201, "Created", data, null);
    }

    public static ApiResponse<Void> error(int status, String message) {
        return new ApiResponse<>(false, status, message, null, null);
    }

    public static ApiResponse<Map<String, String>> validationError(Map<String, String> fieldErrors) {
        return new ApiResponse<>(false, 400, "Validation failed", null, fieldErrors);
    }
}
