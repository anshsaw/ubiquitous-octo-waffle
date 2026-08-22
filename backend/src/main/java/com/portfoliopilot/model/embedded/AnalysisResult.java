package com.portfoliopilot.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code jobAnalyses.analysis} - the scored output of the match engine.
 *
 * <p>The four scores are separate {@code int} fields rather than a nested map so
 * each can be independently indexed, averaged and filtered. They are
 * {@code int} (not {@code Integer}/{@code double}) so the driver writes BSON
 * int32, which is what the collection validator demands.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResult {

    /** Overall weighted score, 0-100. The big progress ring. */
    private int matchScore;

    private int skillsMatch;

    private int projectsMatch;

    private int requirementsMatch;

    /** Display names the user HAS and the job WANTS (green tags). */
    @Builder.Default
    private List<String> strongSkills = new ArrayList<>();

    /** Derived, aggregation-safe form of the above. */
    @Builder.Default
    private List<String> strongSkillsNormalized = new ArrayList<>();

    /** Display names the job WANTS but the user LACKS (amber tags). */
    @Builder.Default
    private List<String> skillGaps = new ArrayList<>();

    /**
     * Derived. THE field the admin "top skill gaps" chart groups on. Without it
     * the chart would count "React", "React.js" and "ReactJS" as three gaps.
     */
    @Builder.Default
    private List<String> skillGapsNormalized = new ArrayList<>();

    @Builder.Default
    private List<ExtractedRequirement> extractedRequirements = new ArrayList<>();

    /**
     * Which scorer produced this record, e.g. {@code "rule-based-v1"}.
     * Essential for comparing scores across an algorithm change - without it,
     * historical scores become meaningless the day the engine is swapped.
     */
    private String engine;
}
