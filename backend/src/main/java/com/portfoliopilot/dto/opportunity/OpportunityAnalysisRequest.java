package com.portfoliopilot.dto.opportunity;

import com.portfoliopilot.model.enums.EmploymentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/opportunities/analyze} - the Opportunity Analyzer form.
 *
 * <p>{@code jobDescription} has a 50-character floor: below that there is
 * nothing to extract, and returning a confident-looking score from three words
 * would be misleading. The 30 000 ceiling matches the collection validator.
 */
public record OpportunityAnalysisRequest(

        @NotBlank(message = "Job title is required")
        @Size(max = 200, message = "Job title must be 200 characters or fewer")
        @Schema(example = "Java Backend Developer")
        String jobTitle,

        @Size(max = 160)
        @Schema(example = "Nexora Labs")
        String company,

        @NotBlank(message = "Job description is required")
        @Size(min = 50, max = 30000,
                message = "Paste the full job description (at least 50 characters)")
        @Schema(description = "The raw pasted posting. Stored verbatim so the analysis can be audited or re-scored.")
        String jobDescription,

        @Size(max = 160)
        String location,

        @Schema(example = "FULL_TIME")
        EmploymentType employmentType,

        @Size(max = 2048)
        String sourceUrl
) {
}
