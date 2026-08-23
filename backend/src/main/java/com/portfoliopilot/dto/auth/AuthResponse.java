package com.portfoliopilot.dto.auth;

/**
 * Returned by register, login and refresh.
 *
 * @param accessToken  short-lived signed JWT for the {@code Authorization} header
 * @param refreshToken long-lived opaque token, exchangeable for a new access token
 * @param tokenType    always {@code Bearer}
 * @param expiresIn    access-token lifetime in seconds, so the client can refresh
 *                     pre-emptively instead of waiting for a 401
 * @param user         the authenticated account
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserSummary user
) {

    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, UserSummary user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
