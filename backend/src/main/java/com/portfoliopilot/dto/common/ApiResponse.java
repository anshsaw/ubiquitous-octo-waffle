package com.portfoliopilot.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * The single response envelope used by every {@code /api} endpoint.
 *
 * <pre>
 * success: { "success": true,  "message": "...", "data": { ... } }
 * failure: { "success": false, "message": "...", "errors": { "field": "..." } }
 * </pre>
 *
 * <p>Null members are omitted, so a success response carries no empty
 * {@code errors} key and vice versa.
 *
 * <p>Chosen over returning bare payloads because the frontend has no existing
 * API contract to preserve (it had no HTTP layer at all), and one predictable
 * shape means the client needs exactly one error-handling path.
 *
 * @param <T> payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Map<String, String> errors,
        Instant timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null, Instant.now());
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(true, message, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message, Map<String, String> errors) {
        return new ApiResponse<>(false, message, null, errors, Instant.now());
    }
}
