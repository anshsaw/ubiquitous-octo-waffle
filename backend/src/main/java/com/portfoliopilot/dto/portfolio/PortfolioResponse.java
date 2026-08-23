package com.portfoliopilot.dto.portfolio;

import com.portfoliopilot.model.Portfolio;
import com.portfoliopilot.model.embedded.ThemeSettings;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A portfolio configuration as owned by the user (the /builder and /preview view). */
public record PortfolioResponse(
        String id,
        String username,
        String name,
        String templateId,
        String templateKey,
        @Schema(description = "Non-null when this portfolio was adapted for a specific job")
        String sourceJobAnalysisId,
        Map<String, Boolean> sections,
        List<String> sectionOrder,
        List<String> orderedSkills,
        List<String> orderedProjects,
        String headlineOverride,
        String summaryOverride,
        String primaryColor,
        String accentColor,
        Boolean darkMode,
        @Schema(description = "Custom template surface colours") String backgroundColor,
        String surfaceColor,
        String inkColor,
        String resumeId,
        boolean published,
        Instant publishedAt,
        @Schema(description = "Public URL path once published", example = "/portfolio/demo-student")
        String publicUrl,
        Integer viewCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static PortfolioResponse from(Portfolio portfolio) {
        ThemeSettings theme = portfolio.getTheme();

        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getUsername(),
                portfolio.getName(),
                portfolio.getTemplateId(),
                portfolio.getTemplateKey(),
                portfolio.getSourceJobAnalysisId(),
                portfolio.getSections() == null ? Map.of() : portfolio.getSections().asMap(),
                nullSafe(portfolio.getSectionOrder()),
                nullSafe(portfolio.getOrderedSkills()),
                nullSafe(portfolio.getOrderedProjects()),
                portfolio.getHeadlineOverride(),
                portfolio.getSummaryOverride(),
                theme == null ? null : theme.getPrimaryColor(),
                theme == null ? null : theme.getAccentColor(),
                theme == null ? null : theme.getDarkMode(),
                theme == null ? null : theme.getBackgroundColor(),
                theme == null ? null : theme.getSurfaceColor(),
                theme == null ? null : theme.getInkColor(),
                portfolio.getResumeId(),
                portfolio.isPublished(),
                portfolio.getPublishedAt(),
                portfolio.isPublished() ? "/portfolio/" + portfolio.getUsername() : null,
                portfolio.getViewCount(),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt());
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
