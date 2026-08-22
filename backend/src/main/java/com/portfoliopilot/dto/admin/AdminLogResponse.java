package com.portfoliopilot.dto.admin;

import com.portfoliopilot.model.AdminLog;
import com.portfoliopilot.model.enums.AdminAction;

import java.time.Instant;
import java.util.Map;

/** One audit-trail entry. */
public record AdminLogResponse(
        String id,
        String adminId,
        String adminEmail,
        AdminAction action,
        String targetUserId,
        Map<String, Object> metadata,
        Instant createdAt
) {

    public static AdminLogResponse from(AdminLog log) {
        return new AdminLogResponse(
                log.getId(),
                log.getAdminId(),
                log.getAdminEmail(),
                log.getAction(),
                log.getTargetUserId(),
                log.getMetadata(),
                log.getCreatedAt());
    }
}
