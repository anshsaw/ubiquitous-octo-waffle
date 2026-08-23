package com.portfoliopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable weights for the match engine, bound from {@code app.matching.*}.
 *
 * <pre>
 *   finalScore = skillsMatch       * skillsWeight
 *              + projectsMatch     * projectsWeight
 *              + requirementsMatch * requirementsWeight
 * </pre>
 *
 * <p>Making these configuration rather than constants is deliberate: scoring
 * policy is a product decision that will be tuned without a code change, and
 * tests can pin exact weights instead of depending on today's defaults.
 *
 * @param skillsWeight           default 0.45
 * @param projectsWeight         default 0.30
 * @param requirementsWeight     default 0.25
 * @param niceToHaveFactor       weight of a "nice to have" skill vs a required one
 * @param maxRecommendedProjects cap on recommendations returned per analysis
 */
@ConfigurationProperties(prefix = "app.matching")
public record MatchingProperties(
        double skillsWeight,
        double projectsWeight,
        double requirementsWeight,
        double niceToHaveFactor,
        int maxRecommendedProjects
) {
    /** Sum of the three weights, used by the startup validation check. */
    public double weightSum() {
        return skillsWeight + projectsWeight + requirementsWeight;
    }
}
