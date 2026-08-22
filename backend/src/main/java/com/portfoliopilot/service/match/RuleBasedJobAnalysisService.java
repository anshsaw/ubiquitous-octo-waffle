package com.portfoliopilot.service.match;

import com.portfoliopilot.config.MatchingProperties;
import com.portfoliopilot.model.Project;
import com.portfoliopilot.service.SkillDictionaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The shipped scoring engine: transparent, deterministic, offline.
 *
 * <pre>
 *   finalScore = skillsMatch       * 0.45
 *              + projectsMatch     * 0.30
 *              + requirementsMatch * 0.25          (weights are configurable)
 * </pre>
 *
 * <p>Three properties were treated as non-negotiable:
 * <ul>
 *   <li><b>Deterministic</b> - the same input always yields the same score.
 *       Scores are persisted and compared over time, so randomness would make
 *       the whole history meaningless.</li>
 *   <li><b>Explainable</b> - every number traces back to a listed skill or a
 *       quoted requirement line. A user shown "63%" can see exactly why.</li>
 *   <li><b>Honest</b> - no floor is applied. The original client-side mock
 *       clamped every score to a minimum of 45%, which flattered every
 *       candidate and made the number worthless.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleBasedJobAnalysisService implements JobAnalysisService {

    private static final String ENGINE = "rule-based-v1";

    /** Credit for a skill the candidate does not have but has an adjacent one for. */
    private static final double RELATED_SKILL_CREDIT = 0.4;

    /** How much of a project's score comes from job coverage vs project focus. */
    private static final double COVERAGE_WEIGHT = 0.7;
    private static final double FOCUS_WEIGHT = 0.3;

    private final JobSkillExtractor extractor;
    private final SkillDictionaryService skillDictionary;
    private final MatchingProperties properties;

    @Override
    public String engineName() {
        return ENGINE;
    }

    @Override
    public MatchResult generateMatch(JobPosting posting, CandidateSnapshot candidate) {
        JobSkillExtractor.ExtractedJob job = extractor.extract(posting.title(), posting.description());

        Set<String> candidateSkills = candidate.allNormalizedSkills();

        // ---- 1. Skills -----------------------------------------------------
        SkillScore skillScore = scoreSkills(job, candidateSkills);

        // ---- 2. Projects ---------------------------------------------------
        List<MatchResult.ProjectMatch> projectMatches = rankProjects(job, candidate);
        int projectsMatch = scoreProjects(projectMatches);

        // ---- 3. Requirements -----------------------------------------------
        List<MatchResult.RequirementMatch> requirements =
                buildRequirements(job, candidateSkills, candidate);
        int requirementsMatch = scoreRequirements(requirements);

        // ---- 4. Weighted total ----------------------------------------------
        int matchScore = clamp(
                skillScore.score() * properties.skillsWeight()
                        + projectsMatch * properties.projectsWeight()
                        + requirementsMatch * properties.requirementsWeight());

        String summary = buildTailoredSummary(posting, candidate, skillScore, projectMatches, matchScore);

        log.debug("Match for '{}': overall={} skills={} projects={} requirements={}",
                posting.title(), matchScore, skillScore.score(), projectsMatch, requirementsMatch);

        return new MatchResult(
                matchScore,
                skillScore.score(),
                projectsMatch,
                requirementsMatch,
                display(skillScore.strong()),
                List.copyOf(skillScore.strong()),
                display(skillScore.gaps()),
                List.copyOf(skillScore.gaps()),
                requirements,
                projectMatches,
                summary,
                ENGINE);
    }

    // ------------------------------------------------------------- skills

    private record SkillScore(int score, List<String> strong, List<String> gaps) {
    }

    /**
     * Required skills count double; nice-to-haves are discounted by
     * {@code niceToHaveFactor}. A missing skill can still earn partial credit
     * when the candidate has a dictionary-declared related skill - somebody who
     * knows Spring Boot is not a total stranger to Spring.
     */
    private SkillScore scoreSkills(JobSkillExtractor.ExtractedJob job, Set<String> candidateSkills) {
        List<String> strong = new ArrayList<>();
        List<String> gaps = new ArrayList<>();

        double earned = 0;
        double possible = 0;

        for (String skill : job.allDemanded()) {
            double weight = job.required().contains(skill) ? 1.0 : properties.niceToHaveFactor();
            possible += weight;

            if (candidateSkills.contains(skill)) {
                strong.add(skill);
                earned += weight;
            } else {
                gaps.add(skill);
                if (hasRelatedSkill(skill, candidateSkills)) {
                    earned += weight * RELATED_SKILL_CREDIT;
                }
            }
        }

        // A description with no recognisable skills tells us nothing. Returning
        // 0 would be a lie; so would 100. Report a neutral score and let the
        // other two components carry the result.
        int score = possible == 0 ? 50 : clamp(earned / possible * 100);

        return new SkillScore(score, strong, gaps);
    }

    private boolean hasRelatedSkill(String missingSkill, Set<String> candidateSkills) {
        for (String related : skillDictionary.relatedSkills(missingSkill)) {
            if (candidateSkills.contains(related)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ projects

    /**
     * Ranks each project on two axes:
     * <ul>
     *   <li><b>coverage</b> - how much of the JOB this project evidences;</li>
     *   <li><b>focus</b> - how much of the PROJECT is relevant. This penalises a
     *       kitchen-sink stack that happens to include one matching item.</li>
     * </ul>
     */
    private List<MatchResult.ProjectMatch> rankProjects(JobSkillExtractor.ExtractedJob job,
                                                        CandidateSnapshot candidate) {
        Set<String> demanded = job.allDemanded();
        if (demanded.isEmpty() || candidate.projects() == null) {
            return List.of();
        }

        List<MatchResult.ProjectMatch> ranked = new ArrayList<>();

        for (Project project : candidate.projects()) {
            List<String> tech = project.getTechStackNormalized() == null
                    ? List.of()
                    : project.getTechStackNormalized();

            List<String> overlap = tech.stream().filter(demanded::contains).toList();
            if (overlap.isEmpty()) {
                continue;
            }

            double coverage = (double) overlap.size() / demanded.size() * 100;
            double focus = (double) overlap.size() / Math.max(1, tech.size()) * 100;
            int relevance = clamp(coverage * COVERAGE_WEIGHT + focus * FOCUS_WEIGHT);

            List<String> matchedDisplay = display(overlap);

            ranked.add(new MatchResult.ProjectMatch(
                    project.getId(),
                    project.getTitle(),
                    relevance,
                    "Direct overlap on " + String.join(", ", matchedDisplay.subList(0, Math.min(3, matchedDisplay.size()))) + ".",
                    matchedDisplay));
        }

        return ranked.stream()
                .sorted(Comparator.comparingInt(MatchResult.ProjectMatch::relevanceScore).reversed())
                .limit(properties.maxRecommendedProjects())
                .toList();
    }

    /**
     * Averages the top two rather than taking the best or the mean of all.
     *
     * <p>Taking the single best would let one lucky project imply a perfect
     * portfolio; averaging everything would let a pile of unrelated side
     * projects drown out one excellent match.
     */
    private int scoreProjects(List<MatchResult.ProjectMatch> matches) {
        if (matches.isEmpty()) {
            return 0;
        }
        int considered = Math.min(2, matches.size());
        double total = matches.stream()
                .limit(considered)
                .mapToInt(MatchResult.ProjectMatch::relevanceScore)
                .sum();
        return clamp(total / considered);
    }

    // -------------------------------------------------------- requirements

    /**
     * Builds the explainable requirement list. Parsed bullet lines are marked met
     * when every skill mentioned in the line is one the candidate has; synthetic
     * entries cover education and portfolio evidence.
     */
    private List<MatchResult.RequirementMatch> buildRequirements(JobSkillExtractor.ExtractedJob job,
                                                                 Set<String> candidateSkills,
                                                                 CandidateSnapshot candidate) {
        List<MatchResult.RequirementMatch> requirements = new ArrayList<>();

        for (String skill : job.required()) {
            requirements.add(new MatchResult.RequirementMatch(
                    "Hands-on experience with " + skillDictionary.displayNameFor(skill),
                    candidateSkills.contains(skill),
                    1.0));
        }
        for (String skill : job.niceToHave()) {
            requirements.add(new MatchResult.RequirementMatch(
                    "Exposure to " + skillDictionary.displayNameFor(skill) + " is a plus",
                    candidateSkills.contains(skill),
                    properties.niceToHaveFactor()));
        }

        requirements.add(new MatchResult.RequirementMatch(
                "Relevant degree or equivalent practical experience",
                candidate.hasEducation(),
                0.8));

        requirements.add(new MatchResult.RequirementMatch(
                "Portfolio of shipped projects",
                candidate.projectCount() > 0,
                0.8));

        return requirements;
    }

    private int scoreRequirements(List<MatchResult.RequirementMatch> requirements) {
        double possible = requirements.stream().mapToDouble(MatchResult.RequirementMatch::weight).sum();
        if (possible == 0) {
            return 50;
        }
        double earned = requirements.stream()
                .filter(MatchResult.RequirementMatch::met)
                .mapToDouble(MatchResult.RequirementMatch::weight)
                .sum();
        return clamp(earned / possible * 100);
    }

    // ------------------------------------------------------------- summary

    /**
     * Writes the role-specific summary line.
     *
     * <p>Assembled strictly from facts already in the profile - it names skills
     * the user listed and a project they built. Nothing is invented, which is
     * the same rule the resume generator follows.
     */
    private String buildTailoredSummary(JobPosting posting,
                                        CandidateSnapshot candidate,
                                        SkillScore skillScore,
                                        List<MatchResult.ProjectMatch> projects,
                                        int matchScore) {

        List<String> headline = display(skillScore.strong().stream().limit(4).toList());
        String skills = headline.isEmpty() ? "modern web technologies" : String.join(", ", headline);

        String target = posting.company() == null || posting.company().isBlank()
                ? "the " + posting.safeTitle() + " role"
                : "the " + posting.safeTitle() + " role at " + posting.company().trim();

        String strength = matchScore >= 80 ? "Strong" : matchScore >= 60 ? "Solid" : "Developing";
        int projectCount = candidate.projectCount();

        StringBuilder summary = new StringBuilder()
                .append(strength).append(' ')
                .append(candidate.professionalTitle())
                .append(" targeting ").append(target).append(". ")
                .append("Practical experience across ").append(skills)
                .append(", demonstrated through ").append(projectCount)
                .append(projectCount == 1 ? " shipped project." : " shipped projects.");

        if (!projects.isEmpty()) {
            summary.append(" Most relevant work: ").append(projects.get(0).title()).append('.');
        }
        return summary.toString();
    }

    // ------------------------------------------------------------- helpers

    /** Canonical keys -> display names, preserving order and dropping duplicates. */
    private List<String> display(List<String> normalized) {
        return new ArrayList<>(normalized.stream()
                .map(skillDictionary::displayNameFor)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /** Rounds to an int in 0..100 - the range the collection validator enforces. */
    private int clamp(double value) {
        return (int) Math.max(0, Math.min(100, Math.round(value)));
    }
}
