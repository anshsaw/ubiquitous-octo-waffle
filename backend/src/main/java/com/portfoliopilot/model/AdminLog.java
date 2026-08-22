package com.portfoliopilot.model;

import com.portfoliopilot.model.enums.AdminAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.util.Map;

/**
 * Maps the existing {@code adminLogs} collection - an append-only audit trail.
 *
 * <p>The admin panel can suspend and delete accounts, so there must be an
 * immutable record of who did what to whom. Rows are never edited (hence no
 * {@code updatedAt}) and are bounded by a TTL index.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "adminLogs")
public class AdminLog {

    @MongoId(targetType = FieldType.OBJECT_ID)
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String adminId;

    /** Snapshot - an audit log must stay readable even if the admin is later purged. */
    private String adminEmail;

    private AdminAction action;

    @Field(targetType = FieldType.OBJECT_ID)
    private String targetUserId;

    private String targetCollection;

    @Field(targetType = FieldType.OBJECT_ID)
    private String targetId;

    /**
     * Free-form context (reason, before/after values). Intentionally schemaless
     * because audit payloads differ per action.
     * MUST NEVER contain passwords, hashes or tokens.
     */
    private Map<String, Object> metadata;

    private String ipAddress;

    private String userAgent;

    private Instant createdAt;
}
