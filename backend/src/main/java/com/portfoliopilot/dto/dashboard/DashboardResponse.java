package com.portfoliopilot.dto.dashboard;

import com.portfoliopilot.dto.opportunity.AnalysisSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * {@code GET /api/dashboard} - everything the user dashboard renders, in one call.
 *
 * <p>Aggregating four stat cards and a list into a single response is deliberate:
 * the dashboard is the first authenticated screen, and five parallel round trips
 * would make it feel slow for no benefit.
 *
 * <p>{@code profileHealth} is read from a cached field on the profile rather than
 * recomputed here, so this endpoint runs no aggregation at all.
 */
public record DashboardResponse(
        @Schema(example = "Demo Student") String name,
        @Schema(example = "demo-student") String username,
        @Schema(description = "Cached 0-100 profile completeness", example = "85") int profileHealth,
        @Schema(example = "4") long totalProjects,
        @Schema(example = "17") int skillsCount,
        @Schema(example = "10") long totalAnalyses,
        @Schema(example = "1") long portfoliosPublished,
        @Schema(description = "Public URL of the live portfolio, or null", example = "/portfolio/demo-student")
        String publishedPortfolioUrl,
        @Schema(description = "Five most recent analyses, newest first")
        List<AnalysisSummaryResponse> recentAnalyses
) {
}
