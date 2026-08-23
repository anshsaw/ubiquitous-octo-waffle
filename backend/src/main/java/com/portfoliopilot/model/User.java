package com.portfoliopilot.model;

import com.portfoliopilot.model.enums.Role;
import com.portfoliopilot.model.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

/**
 * Maps the existing {@code users} collection.
 *
 * <p>Deliberately thin. It holds only what the login path needs, so
 * {@code findByEmail} stays a tiny document read; all descriptive data lives in
 * {@link Profile}.
 *
 * <p>IMPORTANT - the collection validator declares
 * {@code additionalProperties: false}, so this class must not gain a field that
 * is absent from {@code mongodb/schemas/users.schema.json}. Adding one here
 * without updating the schema makes every write fail with error 121.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    /** Stored as a native ObjectId, exposed to Java (and the API) as a hex string. */
    @MongoId(targetType = FieldType.OBJECT_ID)
    private String id;

    /** URL-safe public handle. Drives {@code /portfolio/{username}}. Unique. */
    private String username;

    /** Display name captured at registration. */
    private String name;

    /** Lowercased, trimmed. Unique login identifier. */
    private String email;

    private Boolean emailVerified;

    /**
     * BCrypt hash ONLY. The collection validator enforces the bcrypt pattern, so
     * a plaintext value is rejected by the database itself, not merely by code.
     * Never serialised into any DTO.
     */
    private String passwordHash;

    private Role role;

    private UserStatus status;

    /** Soft-delete flag. Every read path must filter on {@code deleted = false}. */
    private boolean deleted;

    private Instant deletedAt;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant lastLoginAt;

    /** Reset to 0 on a successful login. Backs the lockout policy. */
    private Integer failedLoginAttempts;

    /** When set and in the future, authentication must be refused. */
    private Instant lockedUntil;

    /** True while a temporary brute-force lockout is in effect. */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /** A user may sign in only when active, not soft-deleted and not locked out. */
    public boolean canAuthenticate() {
        return !deleted && status != null && status.canAuthenticate() && !isLocked();
    }
}
