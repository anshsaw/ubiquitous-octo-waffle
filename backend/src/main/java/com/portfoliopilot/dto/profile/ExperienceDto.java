package com.portfoliopilot.dto.profile;

import com.portfoliopilot.model.embedded.Experience;
import com.portfoliopilot.model.enums.EmploymentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * An experience entry on the wire.
 *
 * <p>{@code period} is a LEGACY DISPLAY ALIAS ("May 2025 - Jul 2025") for the
 * existing templates. The canonical fields are real {@code Instant}s, which is
 * what allows chronological sorting and duration maths.
 */
public record ExperienceDto(
        String id,
        String company,
        String role,
        String location,
        EmploymentType employmentType,
        Instant startDate,
        Instant endDate,
        @Schema(description = "Legacy display alias, e.g. \"May 2025 - Jul 2025\"") String period,
        String description,
        List<String> responsibilities,
        List<String> technologies
) {

    private static final DateTimeFormatter MONTH_YEAR =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    public static ExperienceDto from(Experience experience) {
        return new ExperienceDto(
                experience.getId(),
                experience.getCompany(),
                experience.getRole(),
                experience.getLocation(),
                experience.getEmploymentType(),
                experience.getStartDate(),
                experience.getEndDate(),
                displayPeriod(experience.getStartDate(), experience.getEndDate()),
                experience.getDescription(),
                experience.getResponsibilities() == null ? List.of() : experience.getResponsibilities(),
                experience.getTechnologies() == null ? List.of() : experience.getTechnologies());
    }

    private static String displayPeriod(Instant start, Instant end) {
        if (start == null) {
            return null;
        }
        String from = MONTH_YEAR.format(start);
        String to = end == null ? "Present" : MONTH_YEAR.format(end);
        return from + " - " + to;
    }
}
