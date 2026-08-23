package com.portfoliopilot.service.jobs;

import java.util.List;

/**
 * What to search for, already derived from the candidate's portfolio.
 *
 * @param roleQueries     5–10 role phrases, most specific first
 * @param skills          canonical skill keys, used by providers that filter on tags
 * @param location        free text, or null for anywhere
 * @param workType        {@code REMOTE} / {@code HYBRID} / {@code ONSITE} / null
 * @param experienceLevel {@code INTERNSHIP} / {@code ENTRY} / {@code JUNIOR} /
 *                        {@code MID} / {@code SENIOR} / null
 * @param limit           max listings to return after ranking
 */
public record JobQuery(
        List<String> roleQueries,
        List<String> skills,
        String location,
        String workType,
        String experienceLevel,
        int limit
) {

    public String primaryQuery() {
        return roleQueries == null || roleQueries.isEmpty() ? "" : roleQueries.get(0);
    }
}
