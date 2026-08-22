package com.portfoliopilot.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Create or update a portfolio CONFIGURATION.
 *
 * <p>Note what this record does NOT contain: no bio, no skill objects, no
 * project bodies. A portfolio stores a template choice, section toggles and
 * ORDERING only. Content is resolved from the profile and projects at render
 * time, which is what guarantees that adapting a portfolio for a job never
 * mutates the user's actual data.
 */
public record PortfolioRequest(

        @Size(max = 160)
        @Schema(description = "Internal label, not shown publicly", example = "Java Backend Portfolio")
        String name,

        @Schema(description = "Template id. Supply this or templateKey.")
        String templateId,

        @Schema(description = "Template key. Used when templateId is absent.", example = "MODERN_DEV")
        String templateKey,

        @Schema(description = "Section toggles: {\"about\": true, \"skills\": true, ...}")
        Map<String, Boolean> sections,

        @Schema(description = "Custom render order. Empty means the default order.")
        List<String> sectionOrder,

        @Schema(description = "Normalized skill keys, most relevant first. Unlisted skills are hidden.")
        List<String> orderedSkills,

        @Schema(description = "Project ids, most relevant first")
        List<String> orderedProjects,

        @Size(max = 200)
        @Schema(description = "Job-specific headline. Overrides the profile title for THIS portfolio only.")
        String headlineOverride,

        @Size(max = 2000)
        @Schema(description = "Job-specific About text. The profile bio is untouched.")
        String summaryOverride,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Must be a 6-digit hex colour, e.g. #4F46E5")
        String primaryColor,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Must be a 6-digit hex colour")
        String accentColor,

        Boolean darkMode
) {
}
