package com.portfoliopilot.dto.admin;

import com.portfoliopilot.model.JobAnalysis;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * A row in the admin Job Analyses Log.
 *
 * <p>The raw job description is omitted: rows are listed 25 at a time and each
 * description can be 30 000 characters, so including it would make the endpoint
 * enormous for no benefit.
 */
public record AdminJobAnalysisResponse(
        String id,
        @Schema(example = "Java Backend Developer") String jobTitle,
        String company,
        @Schema(description = "Overall match score", example = "83") int matchScore,
        @Schema(description = "Legacy alias of matchScore used by the existing admin table") int match,
        int skillsMatch,
        int projectsMatch,
        int requirementsMatch,
        List<String> skillGaps,
        @Schema(description = "Owning user id") String userId,
        @Schema(description = "Owning user display name") String userName,
        @Schema(description = "Which scorer produced this", example = "rule-based-v1") String engine,
        Instant createdAt,
        @Schema(description = "Alias of createdAt for the table's Date column") Instant date
) {

    public static AdminJobAnalysisResponse from(JobAnalysis analysis, String userName) {
        var result = analysis.getAnalysis();
        var job = analysis.getJob();
        int score = result == null ? 0 : result.getMatchScore();

        return new AdminJobAnalysisResponse(
                analysis.getId(),
                job == null ? null : job.getTitle(),
                job == null ? null : job.getCompany(),
                score,
                score,
                result == null ? 0 : result.getSkillsMatch(),
                result == null ? 0 : result.getProjectsMatch(),
                result == null ? 0 : result.getRequirementsMatch(),
                result == null || result.getSkillGaps() == null ? List.of() : result.getSkillGaps(),
                analysis.getUserId(),
                userName,
                result == null ? null : result.getEngine(),
                analysis.getCreatedAt(),
                analysis.getCreatedAt());
    }
}
