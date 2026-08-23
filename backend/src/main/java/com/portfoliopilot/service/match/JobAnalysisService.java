package com.portfoliopilot.service.match;

/**
 * The scoring abstraction.
 *
 * <p>The product should eventually be able to use an LLM, but it must never
 * DEPEND on one: an outage, a rate limit or an empty billing account cannot be
 * allowed to break the core feature. So the interface is defined here and the
 * shipped implementation is {@link RuleBasedJobAnalysisService} - deterministic,
 * free, offline and explainable.
 *
 * <p>A future {@code AiJobAnalysisService} can either replace it or wrap it
 * (rule-based scores as the floor, model output as an enhancement) without any
 * caller changing. {@code JobAnalysis.analysis.engine} records which
 * implementation produced each stored score, so results stay comparable across
 * an engine change.
 *
 * <p>Implementations must be deterministic. The original client-side mock used
 * {@code Math.random()} in three places, which meant re-analysing the same job
 * gave a different score every time - impossible to test and impossible for a
 * user to trust.
 */
public interface JobAnalysisService {

    /**
     * Scores a candidate against a job posting.
     *
     * @param posting   the job, as pasted by the user
     * @param candidate the user's profile and projects
     * @return a fully populated, deterministic result
     */
    MatchResult generateMatch(JobPosting posting, CandidateSnapshot candidate);

    /** Identifier stored on every analysis, e.g. {@code "rule-based-v1"}. */
    String engineName();
}
