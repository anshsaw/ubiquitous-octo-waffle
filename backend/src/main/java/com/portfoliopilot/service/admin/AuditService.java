package com.portfoliopilot.service.admin;

import com.portfoliopilot.model.AdminLog;
import com.portfoliopilot.model.enums.AdminAction;
import com.portfoliopilot.repository.AdminLogRepository;
import com.portfoliopilot.security.SecurityUtils;
import com.portfoliopilot.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Writes the administrative audit trail.
 *
 * <p>Called on every privileged mutation. Failures are logged but never
 * propagated: an audit write must not be able to roll back the action it is
 * recording. The trade-off is accepted deliberately - a missing audit row is
 * bad, but a failed suspension because the log was unavailable is worse.
 *
 * <p>{@code metadata} must never carry a password, hash or token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AdminLogRepository adminLogRepository;

    public void record(AdminAction action, String targetUserId, Map<String, Object> metadata) {
        record(action, targetUserId, null, null, metadata);
    }

    public void record(AdminAction action,
                       String targetUserId,
                       String targetCollection,
                       String targetId,
                       Map<String, Object> metadata) {
        try {
            UserPrincipal admin = SecurityUtils.requirePrincipal();

            adminLogRepository.save(AdminLog.builder()
                    .adminId(admin.userId())
                    // Snapshot the email so the trail stays readable even if the
                    // admin account is later purged.
                    .adminEmail(admin.email())
                    .action(action)
                    .targetUserId(targetUserId)
                    .targetCollection(targetCollection)
                    .targetId(targetId)
                    .metadata(metadata)
                    .createdAt(Instant.now())
                    .build());

            log.info("AUDIT {} by admin {} target={}", action, admin.userId(), targetUserId);
        } catch (RuntimeException ex) {
            log.error("Failed to write audit log for action {} (target {}): {}",
                    action, targetUserId, ex.getMessage());
        }
    }
}
