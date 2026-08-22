package com.portfoliopilot.dto.profile;

import com.portfoliopilot.model.embedded.Skill;
import com.portfoliopilot.model.enums.Proficiency;
import com.portfoliopilot.model.enums.SkillCategory;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A skill on the wire.
 *
 * <p>{@code level} is a LEGACY DISPLAY ALIAS. The database stores a four-value
 * {@link Proficiency} enum, but the existing frontend renders a 1-100 slider.
 * Rather than loosen the schema (which would break the analytics that group on
 * proficiency), the value is projected both ways here, in one place. Clients may
 * send either {@code proficiency} or {@code level}.
 *
 * @param name           display name, exactly as the user typed it
 * @param normalizedName canonical key - read-only, assigned by the server
 * @param proficiency    canonical strength
 * @param level          1-100 projection of {@code proficiency}
 */
public record SkillDto(
        @Schema(example = "Spring Boot") String name,
        @Schema(description = "Server-assigned canonical key", example = "spring boot") String normalizedName,
        @Schema(example = "ADVANCED") Proficiency proficiency,
        @Schema(description = "Legacy 1-100 alias of proficiency", example = "78") Integer level,
        Double yearsOfExperience,
        SkillCategory category
) {

    public static SkillDto from(Skill skill) {
        Proficiency proficiency = skill.getProficiency() == null
                ? Proficiency.INTERMEDIATE
                : skill.getProficiency();

        return new SkillDto(
                skill.getName(),
                skill.getNormalizedName(),
                proficiency,
                proficiency.toLevel(),
                skill.getYearsOfExperience(),
                skill.getCategory());
    }
}
