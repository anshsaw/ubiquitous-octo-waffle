package com.portfoliopilot.controller.admin;

import com.portfoliopilot.dto.admin.AdminJobAnalysisResponse;
import com.portfoliopilot.dto.common.ApiResponse;
import com.portfoliopilot.dto.common.PageResponse;
import com.portfoliopilot.service.admin.AdminJobAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** {@code /api/admin/job-analyses} - the platform-wide analysis log. */
@RestController
@RequestMapping("/api/admin/job-analyses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Job Analyses", description = "Every analysis run on the platform (ADMIN only)")
public class AdminJobAnalysisController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminJobAnalysisService adminJobAnalysisService;

    @GetMapping
    @Operation(
            summary = "Search the job analysis log",
            description = """
                    Filter by job title/company, match-score range and date range.

                    The raw job description is not returned in list rows — each can be 30 000
                    characters. Fetch a single analysis to see it.
                    """)
    public ResponseEntity<ApiResponse<PageResponse<AdminJobAnalysisResponse>>> list(
            @Parameter(description = "Matches job title or company") @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) Integer maxScore,
            @Parameter(description = "ISO-8601, inclusive") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "ISO-8601, exclusive") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return ResponseEntity.ok(ApiResponse.ok(
                adminJobAnalysisService.search(search, minScore, maxScore, from, to, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one analysis, including its owner")
    public ResponseEntity<ApiResponse<AdminJobAnalysisResponse>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(adminJobAnalysisService.get(id)));
    }
}
