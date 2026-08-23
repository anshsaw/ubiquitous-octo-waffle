package com.portfoliopilot.security;

import com.portfoliopilot.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * The single accessor for "who is calling".
 *
 * <p>Services call {@link #currentUserId()} instead of trusting anything in the
 * request. That is the rule that makes ownership enforcement reliable: a body
 * containing {@code {"userId": "someone-else"}} can never influence a query,
 * because no service ever reads a userId from a DTO.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<UserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    /**
     * @return the authenticated user's id
     * @throws UnauthorizedException if the request is anonymous
     */
    public static String currentUserId() {
        return currentPrincipal()
                .map(UserPrincipal::userId)
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }

    public static UserPrincipal requirePrincipal() {
        return currentPrincipal()
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }

    public static boolean isAdmin() {
        return currentPrincipal().map(UserPrincipal::isAdmin).orElse(false);
    }
}
