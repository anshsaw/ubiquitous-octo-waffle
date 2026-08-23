package com.portfoliopilot.dto.opportunity;

import com.portfoliopilot.model.JobAnalysis;
import com.portfoliopilot.model.embedded.AnalysisResult;
import com.portfoliopilot.model.embedded.JobDetails;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * The full Match Analysis payload - everything /match-analysis renders.
 *
 * <p>The raw job description is intentionally NOT returned: it can be 30 000
 * characters and the screen never displays it. Fetch the single analysis by id
 * if the original text is needed.
 */
public record OpportunityAnalysisResponse(
        String id,
        @Schema(example = "Java Backend Developer") String jobTitle,
        String company,
        String location,

        @Schema(example = "83", description = "Overall weighted score") int matchScore,
        @Schema(example = "88") int skillsMatch,
        @Schema(example = "80") int projectsMatch,
        @Schema(example = "81") int requirementsMatch,

        @Schema(example = "[\"Java\", \"Spring Boot\", \"REST API\"]") List<String> strongSkills,
        @Schema(example = "[\"Docker\", \"AWS\"]") List<String> skillGaps,

        List<RequirementDto> requirements,
        List<RecommendedProjectDto> recommendedProjects,

        String tailoredSummary,

        @Schema(description = "Set once a resume has been generated for this analysis") String resumeId,
        @Schema(description = "Set once a portfolio has been adapted for this analysis") String portfolioId,

        @Schema(description = "Which scorer produced these numbers", example = "rule-based-v1") String engine,
        Instant createdAt
) {

    public static OpportunityAnalysisResponse from(JobAnalysis analysis) {
        JobDetails job = analysis.getJob();
        AnalysisResult result = analysis.getAnalysis();

        return new OpportunityAnalysisResponse(
                analysis.getId(),
                job == null ? null : job.getTitle(),
                job == null ? null : job.getCompany(),
                job == null ? null : job.getLocation(),
                result == null ? 0 : result.getMatchScore(),
                result == null ? 0 : result.getSkillsMatch(),
                result == null ? 0 : result.getProjectsMatch(),
                result == null ? 0 : result.getRequirementsMatch(),
                result == null ? List.of() : nullSafe(result.getStrongSkills()),
                result == null ? List.of() : nullSafe(result.getSkillGaps()),
                result == null || result.getExtractedRequirements() == null
                        ? List.of()
                        : result.getExtractedRequirements().stream().map(RequirementDto::from).toList(),
                analysis.getRecommendedProjects() == null
                        ? List.of()
                        : analysis.getRecommendedProjects().stream().map(RecommendedProjectDto::from).toList(),
                analysis.getTailoredSummary(),
                analysis.getResumeId(),
                analysis.getPortfolioId(),
                result == null ? null : result.getEngine(),
                analysis.getCreatedAt());
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
