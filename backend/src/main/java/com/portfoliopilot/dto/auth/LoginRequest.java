package com.portfoliopilot.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** {@code POST /api/auth/login}. */
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Schema(example = "demo@portfoliopilot.local")
        String email,

        // No @Size here on purpose: rejecting a short password at validation
        // time tells an attacker the policy without them ever holding an
        // account. Length is enforced at registration, verified at login.
        @NotBlank(message = "Password is required")
        @Schema(example = "DemoPass123!")
        String password
) {
}
