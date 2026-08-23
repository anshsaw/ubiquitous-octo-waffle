package com.portfoliopilot.dto.auth;

import com.portfoliopilot.model.User;
import com.portfoliopilot.model.enums.Role;
import com.portfoliopilot.model.enums.UserStatus;

import java.time.Instant;

/**
 * The safe public view of an account.
 *
 * <p>A dedicated record rather than the {@link User} document, so
 * {@code passwordHash}, {@code failedLoginAttempts} and {@code lockedUntil} can
 * never be serialised by accident. This is the only user shape any endpoint
 * returns.
 */
public record UserSummary(
        String id,
        String name,
        String email,
        String username,
        Role role,
        UserStatus status,
        Instant createdAt,
        Instant lastLoginAt
) {

    public static UserSummary from(User user) {
        return new UserSummary(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }
}
