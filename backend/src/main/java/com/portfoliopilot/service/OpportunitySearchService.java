package com.portfoliopilot.service;

import com.portfoliopilot.dto.opportunity.OpportunitySearchRequest;
import com.portfoliopilot.dto.opportunity.OpportunitySearchResponse;
import com.portfoliopilot.exception.BusinessValidationException;
import com.portfoliopilot.service.jobs.CandidateProfile;
import com.portfoliopilot.service.jobs.CandidateProfileService;
import com.portfoliopilot.service.jobs.JobQuery;
import com.portfoliopilot.service.jobs.JobQueryGenerator;
import com.portfoliopilot.service.jobs.JobSearchService;
import com.portfoliopilot.service.jobs.OpportunityRankingService;
import com.portfoliopilot.service.jobs.ScoredOpportunity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the Opportunity Analyzer:
 *
 * <pre>
 *   published portfolio -> candidate profile -> role queries
 *                       -> real providers -> rank -> response
 * </pre>
 *
 * <p>Each step is a separate collaborator so each is independently testable and
 * replaceable — swapping the provider or the ranking formula touches one class.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpportunitySearchService {

    private final CandidateProfileService candidateProfileService;
    private final JobQueryGenerator queryGenerator;
    private final JobSearchService jobSearchService;
    private final OpportunityRankingService rankingService;

    public OpportunitySearchResponse search(String userId, OpportunitySearchRequest request) {
        CandidateProfile candidate = candidateProfileService.build(userId);

        // Without any skills or projects there is nothing to match on, and a
        // search would return noise dressed up as recommendations.
        if (!candidate.hasAnySignal()) {
            throw new BusinessValidationException(
                    "Add some skills or projects before searching. The analyzer matches opportunities "
                            + "against what your portfolio actually demonstrates.",
                    Map.of("profile", "No skills or projects found"));
        }

        List<String> queries = queryGenerator.generate(
                candidate, request.targetRole(), request.resolvedExperienceLevel());

        JobQuery jobQuery = new JobQuery(
                queries,
                candidate.normalizedSkills(),
                request.location(),
                request.resolvedWorkType(),
                request.resolvedExperienceLevel(),
                request.resolvedLimit());

        JobSearchService.SearchOutcome outcome = jobSearchService.search(jobQuery);

        List<ScoredOpportunity> ranked = rankingService.rank(
                outcome.listings(), candidate, jobQuery, request.resolvedMinimum());

        log.info("User {} searched opportunities: {} raw -> {} above {}% match",
                userId, outcome.listings().size(), ranked.size(), request.resolvedMinimum());

        return new OpportunitySearchResponse(
                ranked.stream().map(OpportunitySearchResponse.OpportunityDto::from).toList(),
                OpportunitySearchResponse.CandidateSummary.from(candidate),
                queries,
                outcome.statuses().stream().map(OpportunitySearchResponse.ProviderStatusDto::from).toList(),
                outcome.listings().size(),
                ranked.size(),
                Instant.now());
    }

    /** Candidate preview shown before the first search, so the user sees what will be used. */
    public OpportunitySearchResponse.CandidateSummary candidateSummary(String userId) {
        return OpportunitySearchResponse.CandidateSummary.from(candidateProfileService.build(userId));
    }

    public List<OpportunitySearchResponse.ProviderStatusDto> providerStatuses() {
        return jobSearchService.statuses().stream()
                .map(OpportunitySearchResponse.ProviderStatusDto::from)
                .toList();
    }

    /** The role phrases that would be searched, for the "auto-detected" hint. */
    public List<String> previewQueries(String userId, String overrideRole, String experienceLevel) {
        CandidateProfile candidate = candidateProfileService.build(userId);
        return queryGenerator.generate(candidate, overrideRole, experienceLevel);
    }
}
