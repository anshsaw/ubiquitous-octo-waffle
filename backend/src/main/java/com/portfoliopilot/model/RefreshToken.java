package com.portfoliopilot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

/**
 * Maps the existing {@code refreshTokens} collection.
 *
 * <p>Exists so a session can be REVOKED - something a stateless access token
 * cannot do. It is what makes "log out everywhere" and forced invalidation on
 * admin suspension actually work.
 *
 * <p>Only the SHA-256 hash of the token is stored, never the token itself: a
 * database dump must not yield usable sessions. Rows self-expire through a TTL
 * index on {@code expiresAt}, so there is no cleanup job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "refreshTokens")
public class RefreshToken {

    @MongoId(targetType = FieldType.OBJECT_ID)
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String userId;

    /** SHA-256 hex of the opaque refresh token. */
    private String tokenHash;

    /** TTL anchor - mongod deletes the row once this passes. */
    private Instant expiresAt;

    /** Set on explicit logout or admin suspension; a non-null value fails validation. */
    private Instant revokedAt;

    /** Supports rotation and reuse detection. */
    private String replacedByTokenHash;

    private String userAgent;

    private String ipAddress;

    private Instant createdAt;

    public boolean isActive() {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(Instant.now());
    }
}
