package com.portfoliopilot.service;

import com.portfoliopilot.dto.common.PageResponse;
import com.portfoliopilot.dto.opportunity.AnalysisSummaryResponse;
import com.portfoliopilot.dto.opportunity.OpportunityAnalysisRequest;
import com.portfoliopilot.dto.opportunity.OpportunityAnalysisResponse;
import com.portfoliopilot.exception.ResourceNotFoundException;
import com.portfoliopilot.model.JobAnalysis;
import com.portfoliopilot.model.Profile;
import com.portfoliopilot.model.Project;
import com.portfoliopilot.model.embedded.AnalysisResult;
import com.portfoliopilot.model.embedded.ExtractedRequirement;
import com.portfoliopilot.model.embedded.JobDetails;
import com.portfoliopilot.model.embedded.RecommendedProject;
import com.portfoliopilot.model.enums.JobSource;
import com.portfoliopilot.repository.JobAnalysisRepository;
import com.portfoliopilot.service.match.CandidateSnapshot;
import com.portfoliopilot.service.match.JobAnalysisService;
import com.portfoliopilot.service.match.JobPosting;
import com.portfoliopilot.service.match.MatchResult;
import com.portfoliopilot.util.SkillNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Orchestrates the Opportunity Analyzer.
 *
 * <p>Sequence for {@code POST /api/opportunities/analyze}:
 * <pre>
 *   authenticate -> load profile -> load projects -> run the match engine
 *                -> persist an immutable jobAnalyses document -> return it
 * </pre>
 *
 * <p>Persistence is not optional. Storing every analysis is what turns a
 * one-shot calculator into the product's history: the dashboard's recent list,
 * the admin log, and the source record every resume and adapted portfolio must
 * reference.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpportunityService {

    private final JobAnalysisRepository jobAnalysisRepository;
    private final ProfileService profileService;
    private final ProjectService projectService;
    private final JobAnalysisService matchEngine;

    /** Runs an analysis and stores it. */
    public OpportunityAnalysisResponse analyze(String userId, OpportunityAnalysisRequest request) {
        Profile profile = profileService.requireProfile(userId);
        List<Project> projects = projectService.ownedProjects(userId);

        MatchResult match = matchEngine.generateMatch(
                new JobPosting(request.jobTitle(), request.company(), request.jobDescription()),
                new CandidateSnapshot(profile, projects));

        JobAnalysis analysis = JobAnalysis.builder()
                .userId(userId)
                .job(JobDetails.builder()
                        .title(request.jobTitle().trim())
                        .normalizedTitle(SkillNormalizer.normalizeJobTitle(request.jobTitle()))
                        .company(trimToNull(request.company()))
                        .location(trimToNull(request.location()))
                        .employmentType(request.employmentType())
                        // The raw posting is stored verbatim so the analysis can
                        // be audited, or re-scored later by a better engine.
                        .description(request.jobDescription())
                        .sourceUrl(trimToNull(request.sourceUrl()))
                        .source(request.sourceUrl() == null ? JobSource.PASTED : JobSource.URL)
                        .build())
                .analysis(toAnalysisResult(match))
                .recommendedProjects(toRecommendations(match))
                .tailoredSummary(match.tailoredSummary())
                .deleted(false)
                .createdAt(Instant.now())
                .build();

        JobAnalysis saved = jobAnalysisRepository.save(analysis);

        log.info("User {} analyzed '{}' -> match {}% (skills {} / projects {} / requirements {})",
                userId, request.jobTitle(), match.matchScore(),
                match.skillsMatch(), match.projectsMatch(), match.requirementsMatch());

        return OpportunityAnalysisResponse.from(saved);
    }

    public OpportunityAnalysisResponse get(String userId, String analysisId) {
        return OpportunityAnalysisResponse.from(requireOwned(userId, analysisId));
    }

    /** Dashboard "Recent Analyses". */
    public List<AnalysisSummaryResponse> recent(String userId) {
        return jobAnalysisRepository.findTop5ByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId).stream()
                .map(AnalysisSummaryResponse::from)
                .toList();
    }

    public PageResponse<AnalysisSummaryResponse> list(String userId, Pageable pageable) {
        return PageResponse.from(
                jobAnalysisRepository.findByUserIdAndDeletedFalse(userId, pageable),
                AnalysisSummaryResponse::from);
    }

    /**
     * Soft delete.
     *
     * <p>Any portfolio derived from this analysis is deliberately left published:
     * a live public URL must not disappear because the user tidied their history.
     */
    public void delete(String userId, String analysisId) {
        JobAnalysis analysis = requireOwned(userId, analysisId);
        analysis.setDeleted(true);
        analysis.setDeletedAt(Instant.now());
        jobAnalysisRepository.save(analysis);
        log.info("User {} soft-deleted analysis {}", userId, analysisId);
    }

    /** Ownership-checked accessor used by the resume and portfolio services. */
    public JobAnalysis requireOwned(String userId, String analysisId) {
        return jobAnalysisRepository.findByIdAndUserIdAndDeletedFalse(analysisId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Job analysis"));
    }

    /** Records the artifact produced from an analysis, so the UI can show "already generated". */
    public void linkResume(String analysisId, String resumeId) {
        jobAnalysisRepository.findById(analysisId).ifPresent(analysis -> {
            analysis.setResumeId(resumeId);
            jobAnalysisRepository.save(analysis);
        });
    }

    public void linkPortfolio(String analysisId, String portfolioId) {
        jobAnalysisRepository.findById(analysisId).ifPresent(analysis -> {
            analysis.setPortfolioId(portfolioId);
            jobAnalysisRepository.save(analysis);
        });
    }

    public long countForUser(String userId) {
        return jobAnalysisRepository.countByUserIdAndDeletedFalse(userId);
    }

    // ----------------------------------------------------------- mapping

    private AnalysisResult toAnalysisResult(MatchResult match) {
        return AnalysisResult.builder()
                .matchScore(match.matchScore())
                .skillsMatch(match.skillsMatch())
                .projectsMatch(match.projectsMatch())
                .requirementsMatch(match.requirementsMatch())
                .strongSkills(match.strongSkills())
                .strongSkillsNormalized(match.strongSkillsNormalized())
                .skillGaps(match.skillGaps())
                // The normalized arrays are what the admin analytics group on.
                // Persisting them here is the whole reason those charts are
                // accurate rather than splitting one skill across three bars.
                .skillGapsNormalized(match.skillGapsNormalized())
                .extractedRequirements(match.requirements().stream()
                        .map(r -> ExtractedRequirement.builder()
                                .text(r.text())
                                .met(r.met())
                                .weight(r.weight())
                                .build())
                        .toList())
                .engine(match.engine())
                .build();
    }

    private List<RecommendedProject> toRecommendations(MatchResult match) {
        return match.recommendedProjects().stream()
                .map(p -> RecommendedProject.builder()
                        .projectId(p.projectId())
                        // Only the title is snapshotted, so a later-deleted
                        // project still renders as readable history.
                        .titleSnapshot(p.title())
                        .relevanceScore(p.relevanceScore())
                        .reason(p.reason())
                        .matchedSkills(p.matchedSkills())
                        .build())
                .toList();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
