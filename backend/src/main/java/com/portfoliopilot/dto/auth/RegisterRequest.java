package com.portfoliopilot.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/register}.
 *
 * @param username optional. When omitted, a unique handle is derived from the
 *                 name (the existing frontend's register form collects only
 *                 name, email and password).
 */
public record RegisterRequest(

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters")
        @Schema(example = "Demo Student")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 254)
        @Schema(example = "demo@portfoliopilot.local")
        String email,

        /*
         * Minimum 8 characters with at least one letter and one digit.
         * Deliberately not a maximum-complexity rule: length beats symbol
         * gymnastics, and an over-strict policy pushes people toward reuse.
         */
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one number")
        @Schema(example = "DemoPass123!")
        String password,

        @Size(min = 3, max = 30)
        @Pattern(
                regexp = "^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$",
                message = "Username may contain lowercase letters, digits and inner hyphens only")
        @Schema(description = "Optional. Derived from the name when omitted.", example = "demo-student")
        String username
) {
}
