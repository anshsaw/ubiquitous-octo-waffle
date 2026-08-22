package com.portfoliopilot.dto.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creating or updating one education entry.
 *
 * <p>{@code endYear >= startYear} cannot be expressed with Bean Validation
 * (it spans two fields), so it is enforced in {@code ProfileService} and
 * reported as a {@code BusinessValidationException}.
 */
public record EducationRequest(

        @NotBlank(message = "Degree is required")
        @Size(max = 160)
        @Schema(example = "BSc Computer Science")
        String degree,

        @NotBlank(message = "Institution is required")
        @Size(max = 160)
        @Schema(example = "Savitribai Phule Pune University")
        String institution,

        @Size(max = 160)
        String fieldOfStudy,

        @Min(value = 1950, message = "Start year looks implausible")
        @Max(value = 2100, message = "Start year looks implausible")
        @Schema(example = "2023")
        Integer startYear,

        @Min(value = 1950, message = "End year looks implausible")
        @Max(value = 2100, message = "End year looks implausible")
        @Schema(description = "Omit for an ongoing course", example = "2026")
        Integer endYear,

        @Size(max = 40)
        String grade,

        @Size(max = 1000)
        String description
) {
}
