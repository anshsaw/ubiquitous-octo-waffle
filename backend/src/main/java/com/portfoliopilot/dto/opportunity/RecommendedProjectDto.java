package com.portfoliopilot.dto.opportunity;

import com.portfoliopilot.model.embedded.RecommendedProject;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A ranked project recommendation.
 *
 * <p>A reference plus a score - never an embedded copy of the project. The
 * client already has the project list, or can fetch it by id.
 */
public record RecommendedProjectDto(
        String projectId,
        @Schema(description = "Title as it was when the analysis ran") String title,
        @Schema(example = "92") int relevanceScore,
        @Schema(example = "Direct overlap on Java, Spring Boot.") String reason,
        List<String> matchedSkills
) {

    public static RecommendedProjectDto from(RecommendedProject source) {
        return new RecommendedProjectDto(
                source.getProjectId(),
                source.getTitleSnapshot(),
                source.getRelevanceScore(),
                source.getReason(),
                source.getMatchedSkills() == null ? List.of() : source.getMatchedSkills());
    }
}
