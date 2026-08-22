package com.portfoliopilot.dto.resume;

import com.portfoliopilot.model.enums.ResumeTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/resumes/generate}.
 *
 * <p>Input is a {@code jobAnalysisId}, not a job description. A resume is
 * derived from an analysis that already happened - that link is what makes it
 * tailored, and it guarantees the resume and the match screen agree about which
 * skills and projects are relevant.
 */
public record ResumeGenerationRequest(

        @NotBlank(message = "jobAnalysisId is required")
        @Schema(description = "The analysis this resume is built from")
        String jobAnalysisId,

        @Schema(description = "Defaults to ATS_CLASSIC. All options are single-column and parser-safe.",
                example = "ATS_CLASSIC")
        ResumeTemplate template
) {

    public ResumeTemplate resolveTemplate() {
        return template == null ? ResumeTemplate.ATS_CLASSIC : template;
    }
}
