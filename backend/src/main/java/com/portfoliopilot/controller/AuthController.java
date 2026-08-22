package com.portfoliopilot.controller;

import com.portfoliopilot.dto.auth.AuthResponse;
import com.portfoliopilot.dto.auth.LoginRequest;
import com.portfoliopilot.dto.auth.RefreshTokenRequest;
import com.portfoliopilot.dto.auth.RegisterRequest;
import com.portfoliopilot.dto.auth.UserSummary;
import com.portfoliopilot.dto.common.ApiResponse;
import com.portfoliopilot.security.SecurityUtils;
import com.portfoliopilot.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/auth} - registration, login, refresh, logout, whoami.
 *
 * <p>Thin by design: every rule lives in {@link AuthService}. The controller's
 * only real job beyond delegation is capturing the User-Agent and client IP so a
 * session row can be attributed to a device.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, sign in, refresh and revoke sessions")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(
            summary = "Create an account",
            description = """
                    Creates the user AND its 1:1 profile, then returns a token pair.
                    A unique username is derived from the name when one is not supplied.
                    """)
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                              HttpServletRequest servletRequest) {
        AuthResponse response = authService.register(
                request, servletRequest.getHeader("User-Agent"), clientIp(servletRequest));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Account created successfully", response));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
            summary = "Sign in",
            description = """
                    Returns a short-lived access token (15 min) and a long-lived refresh token.

                    Failures are intentionally indistinguishable: an unknown email and a wrong
                    password produce the same 401 and the same message, so this endpoint cannot
                    be used to enumerate accounts.
                    """)
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest servletRequest) {
        AuthResponse response = authService.login(
                request, servletRequest.getHeader("User-Agent"), clientIp(servletRequest));

        return ResponseEntity.ok(ApiResponse.ok("Signed in successfully", response));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(
            summary = "Exchange a refresh token for a new token pair",
            description = """
                    The presented refresh token is consumed (rotation). Presenting an already-rotated
                    token is treated as theft and revokes every session for that account.
                    """)
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                             HttpServletRequest servletRequest) {
        AuthResponse response = authService.refresh(
                request.refreshToken(), servletRequest.getHeader("User-Agent"), clientIp(servletRequest));

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Revoke the current session", description = "Idempotent - logging out twice is not an error.")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.ok(ApiResponse.message("Signed out"));
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Revoke every session for the current user")
    public ResponseEntity<ApiResponse<Void>> logoutEverywhere() {
        authService.revokeAllSessions(SecurityUtils.currentUserId());
        return ResponseEntity.ok(ApiResponse.message("All sessions revoked"));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "The authenticated account", description = "Identity is read from the token, never from a parameter.")
    public ResponseEntity<ApiResponse<UserSummary>> me() {
        return ResponseEntity.ok(ApiResponse.ok(authService.currentUser(SecurityUtils.currentUserId())));
    }

    /**
     * Best-effort client IP.
     *
     * <p>{@code X-Forwarded-For} is spoofable unless a trusted proxy sets it, so
     * this value is used only for audit context - never for any security
     * decision.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
