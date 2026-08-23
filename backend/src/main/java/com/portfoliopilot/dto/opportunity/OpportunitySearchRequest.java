package com.portfoliopilot.dto.opportunity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/opportunities/search} — the Opportunity Analyzer form.
 *
 * <p>Every field is optional. With an empty body the search is driven entirely
 * by the user's published portfolio, which is the intended default.
 */
public record OpportunitySearchRequest(

        @Size(max = 120)
        @Schema(description = "Overrides the role auto-detected from the portfolio.",
                example = "Java Backend Developer")
        String targetRole,

        @Size(max = 120)
        @Schema(example = "Remote")
        String location,

        @Schema(description = "ANY, REMOTE, HYBRID or ONSITE", example = "REMOTE")
        String workType,

        @Schema(description = "ANY, INTERNSHIP, ENTRY, JUNIOR, MID or SENIOR", example = "ENTRY")
        String experienceLevel,

        @Min(0) @Max(100)
        @Schema(description = "Hide opportunities below this Portfolio Match Score.", example = "60")
        Integer minimumMatch,

        @Min(1) @Max(50)
        @Schema(example = "20")
        Integer limit
) {

    public int resolvedMinimum() {
        return minimumMatch == null ? 0 : minimumMatch;
    }

    public int resolvedLimit() {
        return limit == null ? 20 : limit;
    }

    /** {@code ANY} is treated as "no filter" rather than a literal value. */
    public String resolvedWorkType() {
        return workType == null || workType.isBlank() || "ANY".equalsIgnoreCase(workType) ? null : workType;
    }

    public String resolvedExperienceLevel() {
        return experienceLevel == null || experienceLevel.isBlank() || "ANY".equalsIgnoreCase(experienceLevel)
                ? null : experienceLevel;
    }
}
