package com.portfoliopilot.dto.project;

import com.portfoliopilot.model.Project;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * A project on the wire.
 *
 * <p>{@code tech}, {@code repo} and {@code live} are LEGACY DISPLAY ALIASES so
 * the existing HTML templates render unchanged. {@code techStackNormalized} is
 * exposed read-only because the Match Analysis screen highlights which
 * technologies matched a job.
 *
 * <p>{@code userId} is deliberately NOT exposed - a client never needs it, and
 * omitting it removes any temptation to send it back.
 */
public record ProjectResponse(
        String id,
        String title,
        String description,
        List<String> techStack,
        @Schema(description = "Legacy alias of techStack") List<String> tech,
        @Schema(description = "Server-derived canonical technology keys") List<String> techStackNormalized,
        String repositoryUrl,
        @Schema(description = "Legacy alias of repositoryUrl") String repo,
        String liveDemoUrl,
        @Schema(description = "Legacy alias of liveDemoUrl") String live,
        String imageUrl,
        List<String> images,
        String role,
        List<String> features,
        List<String> achievements,
        Instant startDate,
        Instant endDate,
        boolean featured,
        boolean includeInPortfolio,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProjectResponse from(Project project) {
        List<String> techStack = nullSafe(project.getTechStack());

        return new ProjectResponse(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                techStack,
                techStack,
                nullSafe(project.getTechStackNormalized()),
                project.getRepositoryUrl(),
                project.getRepositoryUrl(),
                project.getLiveDemoUrl(),
                project.getLiveDemoUrl(),
                project.getImageUrl(),
                nullSafe(project.getImages()),
                project.getRole(),
                nullSafe(project.getFeatures()),
                nullSafe(project.getAchievements()),
                project.getStartDate(),
                project.getEndDate(),
                project.isFeatured(),
                project.isIncludeInPortfolio(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
