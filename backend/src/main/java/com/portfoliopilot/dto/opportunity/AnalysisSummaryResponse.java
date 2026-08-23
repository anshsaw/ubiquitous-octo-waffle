package com.portfoliopilot.dto.opportunity;

import com.portfoliopilot.model.JobAnalysis;

import java.time.Instant;

/**
 * Compact row for the dashboard's "Recent Analyses" list and the admin log.
 *
 * <p>A separate, deliberately small shape: sending the full analysis (with every
 * requirement and recommendation) for a five-row list would waste most of the
 * payload.
 */
public record AnalysisSummaryResponse(
        String id,
        String jobTitle,
        String company,
        int matchScore,
        int skillsMatch,
        int projectsMatch,
        int requirementsMatch,
        int skillGapCount,
        boolean hasResume,
        boolean hasPortfolio,
        Instant createdAt
) {

    public static AnalysisSummaryResponse from(JobAnalysis analysis) {
        var result = analysis.getAnalysis();
        var job = analysis.getJob();

        return new AnalysisSummaryResponse(
                analysis.getId(),
                job == null ? null : job.getTitle(),
                job == null ? null : job.getCompany(),
                result == null ? 0 : result.getMatchScore(),
                result == null ? 0 : result.getSkillsMatch(),
                result == null ? 0 : result.getProjectsMatch(),
                result == null ? 0 : result.getRequirementsMatch(),
                result == null || result.getSkillGaps() == null ? 0 : result.getSkillGaps().size(),
                analysis.getResumeId() != null,
                analysis.getPortfolioId() != null,
                analysis.getCreatedAt());
    }
}
