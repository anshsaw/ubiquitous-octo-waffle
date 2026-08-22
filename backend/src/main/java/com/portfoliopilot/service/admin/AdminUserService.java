package com.portfoliopilot.service.admin;

import com.portfoliopilot.dto.admin.AdminLogResponse;
import com.portfoliopilot.dto.admin.AdminUserDetailResponse;
import com.portfoliopilot.dto.admin.AdminUserResponse;
import com.portfoliopilot.dto.common.PageResponse;
import com.portfoliopilot.dto.opportunity.AnalysisSummaryResponse;
import com.portfoliopilot.dto.profile.ProfileResponse;
import com.portfoliopilot.dto.project.ProjectResponse;
import com.portfoliopilot.exception.BusinessValidationException;
import com.portfoliopilot.exception.ResourceNotFoundException;
import com.portfoliopilot.model.User;
import com.portfoliopilot.model.enums.AdminAction;
import com.portfoliopilot.model.enums.Role;
import com.portfoliopilot.model.enums.UserStatus;
import com.portfoliopilot.repository.AdminLogRepository;
import com.portfoliopilot.repository.JobAnalysisRepository;
import com.portfoliopilot.repository.PortfolioRepository;
import com.portfoliopilot.repository.ProfileRepository;
import com.portfoliopilot.repository.ProjectRepository;
import com.portfoliopilot.repository.UserRepository;
import com.portfoliopilot.security.SecurityUtils;
import com.portfoliopilot.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Admin user management: search, inspect, suspend, activate, delete.
 *
 * <p>Two protections are enforced here regardless of what the client sends:
 * an admin cannot act on their own account (no accidental self-lockout), and an
 * admin cannot suspend or delete another ADMIN through this API - privilege
 * changes belong in a deliberate, out-of-band process.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ProjectRepository projectRepository;
    private final JobAnalysisRepository jobAnalysisRepository;
    private final PortfolioRepository portfolioRepository;
    private final AdminLogRepository adminLogRepository;
    private final MongoTemplate mongoTemplate;
    private final AuditService auditService;
    private final AuthService authService;

    /**
     * Paginated search with optional filters.
     *
     * <p>The search term is matched with an anchored, QUOTED regex.
     * {@code Pattern.quote} matters: without it a user searching for
     * {@code ".*"} would trigger a catastrophic scan, and a crafted term could
     * become a ReDoS vector.
     */
    public PageResponse<AdminUserResponse> search(String term, UserStatus status, Role role, Pageable pageable) {
        Criteria criteria = Criteria.where("deleted").is(false);

        if (status != null) {
            criteria = criteria.and("status").is(status.name());
        }
        if (role != null) {
            criteria = criteria.and("role").is(role.name());
        }
        if (term != null && !term.isBlank()) {
            String safe = Pattern.quote(term.trim());
            criteria = criteria.orOperator(
                    Criteria.where("name").regex(safe, "i"),
                    Criteria.where("email").regex(safe, "i"),
                    Criteria.where("username").regex(safe, "i"));
        }

        Query query = new Query(criteria);
        long total = mongoTemplate.count(query, User.class);

        List<User> users = mongoTemplate.find(query.with(pageable), User.class);
        Page<User> page = new PageImpl<>(users, pageable, total);

        return PageResponse.from(page, this::toRow);
    }

    public AdminUserDetailResponse detail(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User"));

        ProfileResponse profile = profileRepository.findByUserId(userId)
                .map(p -> ProfileResponse.from(p, user))
                .orElse(null);

        List<ProjectResponse> projects = projectRepository
                .findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId).stream()
                .map(ProjectResponse::from)
                .toList();

        List<AnalysisSummaryResponse> analyses = jobAnalysisRepository
                .findTop5ByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId).stream()
                .map(AnalysisSummaryResponse::from)
                .toList();

        String publicUrl = portfolioRepository.findByUserIdAndPublishedTrueAndDeletedFalse(userId)
                .map(p -> "/portfolio/" + p.getUsername())
                .orElse(null);

        List<AdminLogResponse> trail = adminLogRepository
                .findTop20ByTargetUserIdOrderByCreatedAtDesc(userId).stream()
                .map(AdminLogResponse::from)
                .toList();

        // Viewing another person's account is itself worth recording.
        auditService.record(AdminAction.VIEW_USER_DETAIL, userId, Map.of());

        return new AdminUserDetailResponse(toRow(user), profile, projects, analyses, publicUrl, trail);
    }

    /**
     * Suspends an account: blocks sign-in, revokes every session, and takes any
     * published portfolio offline.
     *
     * <p>Revoking sessions is essential. Without it the suspended user keeps a
     * valid access token for up to fifteen minutes and the suspension appears not
     * to work.
     */
    public AdminUserResponse suspend(String userId, String reason) {
        User user = requireActionable(userId);

        user.setStatus(UserStatus.SUSPENDED);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        authService.revokeAllSessions(userId);
        unpublishPortfolios(userId);

        auditService.record(AdminAction.SUSPEND_USER, userId,
                Map.of("reason", reason == null ? "unspecified" : reason, "previousStatus", "ACTIVE"));

        return toRow(user);
    }

    public AdminUserResponse activate(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User"));

        user.setStatus(UserStatus.ACTIVE);
        user.setDeleted(false);
        user.setDeletedAt(null);
        // Clear any lockout so the user is not blocked twice over.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        auditService.record(AdminAction.ACTIVATE_USER, userId, Map.of());
        return toRow(user);
    }

    /**
     * SOFT delete with a cascade to the user's own documents.
     *
     * <p>Nothing is hard-deleted. A hard delete would orphan projects, analyses,
     * resumes, portfolios and audit rows in a single irreversible step; the soft
     * flag keeps everything restorable and keeps the audit trail meaningful.
     * A separate, deliberate purge job handles genuine erasure requests.
     */
    public void softDelete(String userId, String reason) {
        User user = requireActionable(userId);

        Instant now = Instant.now();
        user.setDeleted(true);
        user.setDeletedAt(now);
        user.setStatus(UserStatus.DELETED);
        user.setUpdatedAt(now);
        userRepository.save(user);

        authService.revokeAllSessions(userId);
        unpublishPortfolios(userId);

        cascadeSoftDelete("projects", userId, now);
        cascadeSoftDelete("jobAnalyses", userId, now);
        cascadeSoftDelete("resumes", userId, now);
        cascadeSoftDelete("portfolios", userId, now);

        auditService.record(AdminAction.SOFT_DELETE_USER, userId,
                Map.of("reason", reason == null ? "unspecified" : reason));

        log.warn("Admin soft-deleted user {} and cascaded to their documents", userId);
    }

    // ------------------------------------------------------------ internals

    /** Blocks self-action and action against other admins. */
    private User requireActionable(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User"));

        if (userId.equals(SecurityUtils.currentUserId())) {
            throw new BusinessValidationException("You cannot perform this action on your own account");
        }
        if (user.getRole() == Role.ADMIN) {
            throw new BusinessValidationException(
                    "Administrator accounts cannot be suspended or deleted through this API");
        }
        return user;
    }

    private void unpublishPortfolios(String userId) {
        portfolioRepository.findByUserIdAndDeletedFalse(userId).stream()
                .filter(com.portfoliopilot.model.Portfolio::isPublished)
                .forEach(portfolio -> {
                    portfolio.setPublished(false);
                    portfolio.setUpdatedAt(Instant.now());
                    portfolioRepository.save(portfolio);
                    auditService.record(AdminAction.UNPUBLISH_PORTFOLIO, userId,
                            "portfolios", portfolio.getId(), Map.of());
                });
    }

    /** One multi-update per collection rather than loading documents into the JVM. */
    private void cascadeSoftDelete(String collection, String userId, Instant now) {
        mongoTemplate.updateMulti(
                new Query(Criteria.where("userId").is(new org.bson.types.ObjectId(userId))
                        .and("deleted").is(false)),
                new org.springframework.data.mongodb.core.query.Update()
                        .set("deleted", true)
                        .set("deletedAt", now),
                collection);
    }

    private AdminUserResponse toRow(User user) {
        return AdminUserResponse.from(
                user,
                portfolioRepository.countByUserIdAndDeletedFalse(user.getId()),
                projectRepository.countByUserIdAndDeletedFalse(user.getId()),
                jobAnalysisRepository.countByUserIdAndDeletedFalse(user.getId()));
    }
}
