package com.portfoliopilot.service.jobs;

import java.util.List;

/**
 * A listing plus its explainable Portfolio Match Score.
 *
 * <p>Named "Portfolio Match" deliberately. It measures how well the published
 * portfolio EVIDENCES what the posting asks for — it is not a prediction of
 * being hired, and nothing in the UI claims otherwise.
 *
 * @param matchedSkills      demanded skills the portfolio demonstrates
 * @param partialSkills      demanded skills that are listed but not evidenced by
 *                           a project or role
 * @param missingSkills      demanded skills absent from the portfolio entirely
 * @param breakdown          per-component contribution, so the score is auditable
 * @param explanation        one honest sentence describing the fit
 */
public record ScoredOpportunity(
        JobListing listing,
        int matchScore,
        List<String> matchedSkills,
        List<String> partialSkills,
        List<String> missingSkills,
        Breakdown breakdown,
        String explanation
) {

    /** Each value is the 0-100 sub-score before weighting. */
    public record Breakdown(
            int skills,
            int role,
            int experience,
            int projects,
            int education,
            int location
    ) {
    }
}
