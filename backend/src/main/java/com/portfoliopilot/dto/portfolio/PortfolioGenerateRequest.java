package com.portfoliopilot.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/portfolio/generate} - "Adapt Portfolio for this Job".
 *
 * <p>The unique differentiator. Supplying {@code jobAnalysisId} produces a
 * portfolio whose projects and skills are ordered by relevance to that specific
 * opportunity; omitting it produces a generic portfolio ordered by the user's
 * own preferences.
 */
public record PortfolioGenerateRequest(

        @Schema(description = "Omit for a generic portfolio; supply it to adapt for a specific job")
        String jobAnalysisId,

        @Schema(description = "Template to use. Defaults to the first active template.")
        String templateId,

        @Schema(example = "MODERN_DEV")
        String templateKey,

        @Size(max = 160)
        @Schema(description = "Defaults to \"<Job title> Portfolio\", or \"My Portfolio\"")
        String name
) {
}
