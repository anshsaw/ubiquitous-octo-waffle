package com.portfoliopilot.dto.profile;

import com.portfoliopilot.model.embedded.Education;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An education entry on the wire.
 *
 * <p>{@code school} and {@code year} are LEGACY DISPLAY ALIASES kept so the
 * existing HTML templates keep rendering without modification. The canonical
 * fields are {@code institution}, {@code startYear} and {@code endYear} - the
 * database stores real integers, which is what makes "graduating this year"
 * style filtering possible at all.
 */
public record EducationDto(
        String id,
        @Schema(example = "MSc Information Technology") String degree,
        @Schema(example = "Fergusson College") String institution,
        @Schema(description = "Legacy alias of institution") String school,
        String fieldOfStudy,
        @Schema(example = "2026") Integer startYear,
        @Schema(example = "2028") Integer endYear,
        @Schema(description = "Legacy display alias, e.g. \"2026 - 2028\"") String year,
        String grade,
        String description
) {

    public static EducationDto from(Education education) {
        return new EducationDto(
                education.getId(),
                education.getDegree(),
                education.getInstitution(),
                education.getInstitution(),
                education.getFieldOfStudy(),
                education.getStartYear(),
                education.getEndYear(),
                displayYear(education.getStartYear(), education.getEndYear()),
                education.getGrade(),
                education.getDescription());
    }

    private static String displayYear(Integer start, Integer end) {
        if (start == null && end == null) {
            return null;
        }
        if (start == null) {
            return String.valueOf(end);
        }
        if (end == null) {
            return start + " - Present";
        }
        return start.equals(end) ? String.valueOf(end) : start + " - " + end;
    }
}
