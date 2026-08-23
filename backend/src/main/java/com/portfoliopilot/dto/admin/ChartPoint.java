package com.portfoliopilot.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Chart-friendly shapes for the admin dashboard.
 *
 * <p>Kept as flat {@code {label, value}} records because that is what a charting
 * library consumes directly - nesting would force the client to reshape the data
 * before every render.
 */
public final class ChartPoint {

    private ChartPoint() {
    }

    /**
     * One day on the signup / analysis trend lines.
     *
     * @param date  {@code YYYY-MM-DD}
     * @param label short weekday label for a compact x-axis, e.g. {@code "Mon"}
     */
    public record DailyPoint(
            @Schema(example = "2026-08-19") String date,
            @Schema(example = "Wed") String label,
            long value
    ) {
    }

    /**
     * One bar on the "top skill gaps" chart.
     *
     * @param usersAffected distinct users missing this skill - this, not
     *                      {@code occurrences}, is what the chart ranks by, so one
     *                      power user cannot dominate the result
     */
    public record SkillGapPoint(
            @Schema(example = "Docker") String skill,
            long occurrences,
            long usersAffected
    ) {
    }

    /**
     * One row of the "most requested skills" table.
     *
     * @param gapRatio percentage of demand the candidate pool cannot meet
     */
    public record SkillDemandPoint(
            String skill,
            long demandCount,
            long gapCount,
            @Schema(example = "100") int gapRatio
    ) {
    }

    /** One row of "most analysed job roles". */
    public record RolePoint(
            @Schema(example = "Java Backend Developer") String role,
            long count,
            @Schema(example = "42") int avgMatchScore
    ) {
    }
}
