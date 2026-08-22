package com.portfoliopilot.dto.admin;

import com.portfoliopilot.model.User;
import com.portfoliopilot.model.enums.Role;
import com.portfoliopilot.model.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * A row in the admin Users table.
 *
 * <p>Still excludes {@code passwordHash}: an administrator has no legitimate use
 * for a password hash, and exposing it would put every user's credential in the
 * blast radius of one compromised admin session.
 */
public record AdminUserResponse(
        String id,
        String name,
        String email,
        String username,
        Role role,
        UserStatus status,
        @Schema(description = "Count of published portfolios") long portfolios,
        long projects,
        long analyses,
        @Schema(description = "Alias of createdAt, for the table's \"Joined\" column") Instant joinDate,
        Instant createdAt,
        Instant lastLoginAt
) {

    public static AdminUserResponse from(User user, long portfolios, long projects, long analyses) {
        return new AdminUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.getStatus(),
                portfolios,
                projects,
                analyses,
                user.getCreatedAt(),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }
}
