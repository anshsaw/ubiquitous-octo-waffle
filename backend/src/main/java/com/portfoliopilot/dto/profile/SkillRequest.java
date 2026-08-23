package com.portfoliopilot.dto.profile;

import com.portfoliopilot.model.enums.Proficiency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Adding or updating one skill.
 *
 * <p>Supply {@code proficiency} OR {@code level} - {@code proficiency} wins when
 * both are present. {@code normalizedName} is never accepted from the client;
 * the server derives it so the canonical key cannot be spoofed.
 */
public record SkillRequest(

        @NotBlank(message = "Skill name is required")
        @Size(max = 60, message = "Skill name must be 60 characters or fewer")
        @Schema(example = "React.js")
        String name,

        @Schema(example = "ADVANCED")
        Proficiency proficiency,

        @Min(value = 1, message = "Level must be between 1 and 100")
        @Max(value = 100, message = "Level must be between 1 and 100")
        @Schema(description = "Legacy 1-100 alias. Ignored when proficiency is supplied.", example = "85")
        Integer level,

        @DecimalMin(value = "0", message = "Years of experience cannot be negative")
        @DecimalMax(value = "60", message = "Years of experience looks implausible")
        Double yearsOfExperience
) {

    /** Resolves the two possible strength inputs into one canonical value. */
    public Proficiency resolveProficiency() {
        if (proficiency != null) {
            return proficiency;
        }
        return Proficiency.fromLevel(level);
    }
}
