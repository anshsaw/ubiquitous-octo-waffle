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
