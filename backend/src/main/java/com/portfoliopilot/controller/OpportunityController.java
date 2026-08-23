package com.portfoliopilot.controller;

import com.portfoliopilot.dto.common.ApiResponse;
import com.portfoliopilot.dto.common.PageResponse;
import com.portfoliopilot.dto.opportunity.AnalysisSummaryResponse;
import com.portfoliopilot.dto.opportunity.OpportunityAnalysisRequest;
import com.portfoliopilot.dto.opportunity.OpportunityAnalysisResponse;
import com.portfoliopilot.security.SecurityUtils;
import com.portfoliopilot.service.OpportunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /api/opportunities} - the Opportunity Analyzer and Match Analysis.
 *
 * <p>{@code /analyze} is an RPC-shaped endpoint on purpose: it is a genuine
 * business operation that creates a new resource, not a CRUD write.
 */
@RestController
@RequestMapping("/api/opportunities")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Opportunities", description = "Analyze a job description against the user's profile")
public class OpportunityController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OpportunityService opportunityService;
    private final com.portfoliopilot.service.OpportunitySearchService opportunitySearchService;

    @PostMapping("/search")
    @Operation(
            summary = "Find real opportunities matching the published portfolio",
            description = """
                    Builds a candidate profile from the user's PUBLISHED portfolio (falling back to
                    their profile if nothing is published), derives 5-8 role queries from it, queries
                    every configured job provider, and ranks the results.

                    **Portfolio Match Score** — `skills 40% + role 20% + experience 15% +
                    projects 10% + education 5% + location 10%`. A skill evidenced by a project or a
                    role scores full credit; one merely listed scores half. The per-component
                    breakdown is returned so the score can be audited.

                    Listings are always real and always link to the provider's own URL. When a
                    provider is unavailable it is reported in `providers` with a reason — no
                    placeholder or invented listings are ever returned.
                    """)
    public ResponseEntity<ApiResponse<com.portfoliopilot.dto.opportunity.OpportunitySearchResponse>> searchOpportunities(
            @Valid @RequestBody(required = false) com.portfoliopilot.dto.opportunity.OpportunitySearchRequest request) {

        var body = request == null
                ? new com.portfoliopilot.dto.opportunity.OpportunitySearchRequest(null, null, null, null, null, null)
                : request;

        return ResponseEntity.ok(ApiResponse.ok(
                opportunitySearchService.search(SecurityUtils.currentUserId(), body)));
    }

    @GetMapping("/candidate")
    @Operation(
            summary = "The candidate profile the analyzer will use",
            description = "Shows whether it came from the published portfolio or the raw profile, "
                    + "and which skills are evidenced rather than merely listed.")
    public ResponseEntity<ApiResponse<com.portfoliopilot.dto.opportunity.OpportunitySearchResponse.CandidateSummary>> candidate() {
        return ResponseEntity.ok(ApiResponse.ok(
                opportunitySearchService.candidateSummary(SecurityUtils.currentUserId())));
    }

    @GetMapping("/providers")
    @Operation(
            summary = "Job provider availability",
            description = "Which sources are configured, and why any are not. LinkedIn job retrieval "
                    + "requires a Talent Solutions partnership and is normally unavailable.")
    public ResponseEntity<ApiResponse<List<com.portfoliopilot.dto.opportunity.OpportunitySearchResponse.ProviderStatusDto>>> providers() {
        return ResponseEntity.ok(ApiResponse.ok(opportunitySearchService.providerStatuses()));
    }

    @GetMapping("/queries")
    @Operation(summary = "Preview the role queries derived from the portfolio")
    public ResponseEntity<ApiResponse<List<String>>> previewQueries(
            @RequestParam(required = false) String targetRole,
            @RequestParam(required = false) String experienceLevel) {
        return ResponseEntity.ok(ApiResponse.ok(
                opportunitySearchService.previewQueries(
                        SecurityUtils.currentUserId(), targetRole, experienceLevel)));
    }

    @PostMapping("/analyze")
    @Operation(
            summary = "Analyze a job opportunity",
            description = """
                    Loads the caller's profile and projects, scores them against the pasted
                    job description, and stores an immutable analysis.

                    **Scoring** — `skills 45% + projects 30% + requirements 25%` (configurable).
                    Deterministic: the same input always produces the same score.

                    Returns the match breakdown, strong skills, skill gaps, ranked project
                    recommendations and a role-specific summary.
                    """)
    public ResponseEntity<ApiResponse<OpportunityAnalysisResponse>> analyze(
            @Valid @RequestBody OpportunityAnalysisRequest request) {

        OpportunityAnalysisResponse response =
                opportunityService.analyze(SecurityUtils.currentUserId(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Analysis complete", response));
    }

    @GetMapping("/recent")
    @Operation(summary = "The five most recent analyses", description = "Backs the dashboard's Recent Analyses list.")
    public ResponseEntity<ApiResponse<List<AnalysisSummaryResponse>>> recent() {
        return ResponseEntity.ok(ApiResponse.ok(opportunityService.recent(SecurityUtils.currentUserId())));
    }

    @GetMapping
    @Operation(summary = "Paginated analysis history")
    public ResponseEntity<ApiResponse<PageResponse<AnalysisSummaryResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<AnalysisSummaryResponse> result = opportunityService.list(
                SecurityUtils.currentUserId(),
                PageRequest.of(Math.max(0, page),
                        Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a stored analysis",
            description = "Backs the /match-analysis screen, including revisiting an older analysis.")
    public ResponseEntity<ApiResponse<OpportunityAnalysisResponse>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(opportunityService.get(SecurityUtils.currentUserId(), id)));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete an analysis",
            description = "Soft delete. A portfolio already published from this analysis stays live.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        opportunityService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.message("Analysis deleted"));
    }
}
