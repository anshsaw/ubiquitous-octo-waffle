package com.portfoliopilot.service.jobs;

import java.util.List;
import java.util.Set;

/**
 * The normalised view of a candidate that drives search and matching.
 *
 * <p>Built from the PUBLISHED PORTFOLIO when one exists, because that is what
 * the product treats as the source of truth — republishing changes future
 * matches. It falls back to the raw profile only when nothing is published yet,
 * so a new user still gets results.
 *
 * @param source            {@code PUBLISHED_PORTFOLIO} or {@code PROFILE}, surfaced in the UI
 * @param title             professional title, or the strongest inferred role
 * @param normalizedSkills  canonical keys — what matching compares
 * @param displaySkills     correctly-cased names — what the UI shows
 * @param experienceLevel   inferred band, overridable by the user
 * @param evidence          normalized keys the portfolio actually DEMONSTRATES
 *                          (a skill used in a shipped project or a real role),
 *                          as distinct from merely listed
 */
public record CandidateProfile(
        String source,
        String title,
        List<String> normalizedSkills,
        List<String> displaySkills,
        String experienceLevel,
        double yearsOfExperience,
        List<String> education,
        List<ProjectSummary> projects,
        Set<String> evidence,
        String location
) {

    /** @param technologies canonical keys for this project's stack */
    public record ProjectSummary(
            String id,
            String name,
            String description,
            List<String> technologies,
            List<String> displayTechnologies
    ) {
    }

    public boolean hasAnySignal() {
        return !normalizedSkills.isEmpty() || !projects.isEmpty();
    }
}
