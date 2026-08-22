package com.portfoliopilot.dto.profile;

import com.portfoliopilot.model.enums.EmploymentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Creating or updating one experience entry. Never auto-populated - fake experience is never invented. */
public record ExperienceRequest(

        @NotBlank(message = "Company is required")
        @Size(max = 160)
        String company,

        @NotBlank(message = "Role is required")
        @Size(max = 160)
        String role,

        @Size(max = 160)
        String location,

        @NotNull(message = "Employment type is required")
        @Schema(example = "INTERNSHIP")
        EmploymentType employmentType,

        @NotNull(message = "Start date is required")
        Instant startDate,

        @Schema(description = "Omit if this is your current role")
        Instant endDate,

        @Size(max = 2000)
        String description,

        @Size(max = 20, message = "At most 20 responsibilities")
        List<@Size(max = 400) String> responsibilities,

        @Size(max = 40, message = "At most 40 technologies")
        List<@Size(max = 60) String> technologies
) {
}
