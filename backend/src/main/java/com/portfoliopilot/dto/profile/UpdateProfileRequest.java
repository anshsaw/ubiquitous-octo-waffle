package com.portfoliopilot.dto.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/profile} (replace personal block) and
 * {@code PATCH /api/profile} (partial update).
 *
 * <p>Every field is optional so one record serves both verbs; PATCH ignores
 * nulls, PUT treats them as "clear this field". Collections (skills, education,
 * experience, certificates) are NOT here - they have their own sub-resource
 * endpoints, so editing a bio can never accidentally wipe a skill list.
 */
public record UpdateProfileRequest(

        @Size(max = 120, message = "Name must be 120 characters or fewer")
        String name,

        @Size(max = 120)
        @Schema(example = "Full-Stack Developer (Java + React)")
        String professionalTitle,

        @Size(max = 120)
        @Schema(description = "Legacy alias of professionalTitle. Used only when professionalTitle is absent.")
        String title,

        @Size(max = 2000, message = "Bio must be 2000 characters or fewer")
        String bio,

        @Size(max = 160)
        String location,

        @Size(max = 2048)
        @Schema(description = "URL to object storage. Binary uploads are not accepted by this endpoint.")
        String avatarUrl,

        @Valid
        ContactDto contact
) {

    /** {@code professionalTitle} wins; {@code title} is the legacy fallback. */
    public String resolveTitle() {
        return professionalTitle != null ? professionalTitle : title;
    }
}
