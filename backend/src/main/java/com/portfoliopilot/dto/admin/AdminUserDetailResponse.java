package com.portfoliopilot.dto.admin;

import com.portfoliopilot.dto.opportunity.AnalysisSummaryResponse;
import com.portfoliopilot.dto.profile.ProfileResponse;
import com.portfoliopilot.dto.project.ProjectResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The admin user-detail screen.
 *
 * <p>Shows profile, projects, analyses and the published portfolio link, because
 * moderation decisions need context. It deliberately does NOT include generated
 * resumes or draft portfolios - those are private working documents, and an
 * abuse report about a public page does not justify reading them.
 */
public record AdminUserDetailResponse(
        AdminUserResponse user,
        ProfileResponse profile,
        List<ProjectResponse> projects,
        List<AnalysisSummaryResponse> recentAnalyses,
        @Schema(example = "/portfolio/demo-student") String publishedPortfolioUrl,
        @Schema(description = "Recent administrative actions taken against this account")
        List<AdminLogResponse> auditTrail
) {
}
