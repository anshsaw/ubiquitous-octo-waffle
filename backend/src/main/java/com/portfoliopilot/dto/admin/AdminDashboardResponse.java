package com.portfoliopilot.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** {@code GET /api/admin/dashboard/stats} - the four stat cards plus supporting breakdowns. */
public record AdminDashboardResponse(
        @Schema(example = "1284") long totalUsers,
        @Schema(example = "892") long portfoliosPublished,
        @Schema(example = "47") long jobsAnalyzedToday,
        @Schema(description = "Rolling 30-day average, 0-100", example = "78") int avgMatchScore,
        long totalProjects,
        long totalAnalyses,
        long totalResumes,
        @Schema(description = "Average of each sub-score over the same window") SubScores avgSubScores,
        @Schema(description = "How match scores are distributed - the real product-health signal")
        List<Bucket> matchScoreDistribution,
        List<StatusCount> usersByStatus
) {

    public record SubScores(int skillsMatch, int projectsMatch, int requirementsMatch) {
    }

    /** @param bucket a human-readable band, e.g. {@code "75-89 strong"} */
    public record Bucket(String bucket, long count) {
    }

    public record StatusCount(String status, long count) {
    }
}
