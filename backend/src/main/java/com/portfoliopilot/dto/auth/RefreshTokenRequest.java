package com.portfoliopilot.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /api/auth/refresh} and {@code POST /api/auth/logout}. */
public record RefreshTokenRequest(

        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
}
