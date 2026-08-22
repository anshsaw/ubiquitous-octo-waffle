package com.portfoliopilot.service.match;

import java.util.List;

/**
 * The output contract of {@code generateMatch(jobDescription, userProfile)}.
 *
 * <p>A typed record, not a {@code Map<String, Object>}: the shape is a genuine
 * API contract shared by the analyzer screen, the resume generator and the
 * portfolio adapter, and a map would let any of them silently drift.
 *
 * @param matchScore          overall weighted score, 0-100
 * @param skillsMatch         0-100
 * @param projectsMatch       0-100
 * @param requirementsMatch   0-100
 * @param strongSkills        display names the candidate has and the job wants
 * @param strongSkillsNormalized canonical form of the above
 * @param skillGaps           display names the job wants and the candidate lacks
 * @param skillGapsNormalized canonical form of the above
 * @param requirements        every parsed requirement, marked met/unmet
 * @param recommendedProjects ranked project references with a reason
 * @param tailoredSummary     role-specific summary line for the resume
 * @param engine              which scorer produced this, e.g. {@code rule-based-v1}
 */
public record MatchResult(
        int matchScore,
        int skillsMatch,
        int projectsMatch,
        int requirementsMatch,
        List<String> strongSkills,
        List<String> strongSkillsNormalized,
        List<String> skillGaps,
        List<String> skillGapsNormalized,
        List<RequirementMatch> requirements,
        List<ProjectMatch> recommendedProjects,
        String tailoredSummary,
        String engine
) {

    /** One parsed requirement line and whether the candidate satisfies it. */
    public record RequirementMatch(String text, boolean met, double weight) {
    }

    /** A ranked project recommendation. Holds only a reference, never a copy. */
    public record ProjectMatch(
            String projectId,
            String title,
            int relevanceScore,
            String reason,
            List<String> matchedSkills
    ) {
    }
}
