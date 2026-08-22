package com.portfoliopilot.controller;

import com.portfoliopilot.dto.common.ApiResponse;
import com.portfoliopilot.dto.portfolio.PortfolioGenerateRequest;
import com.portfoliopilot.dto.portfolio.PortfolioRequest;
import com.portfoliopilot.dto.portfolio.PortfolioResponse;
import com.portfoliopilot.security.SecurityUtils;
import com.portfoliopilot.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /api/portfolio} - the builder, adaptation and publishing.
 *
 * <p>{@code /generate}, {@code /publish} and {@code /unpublish} are action-shaped
 * rather than CRUD because they are real business operations with side effects
 * beyond writing a field.
 */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Portfolio", description = "Build, adapt, preview and publish portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    @Operation(summary = "List the user's portfolios", description = "Drafts and published, most recently edited first.")
    public ResponseEntity<ApiResponse<List<PortfolioResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(portfolioService.list(SecurityUtils.currentUserId())));
    }

    @GetMapping("/published")
    @Operation(summary = "The user's currently live portfolio, if any")
    public ResponseEntity<ApiResponse<PortfolioResponse>> published() {
        return ResponseEntity.ok(ApiResponse.ok(
                portfolioService.published(SecurityUtils.currentUserId()).orElse(null)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one portfolio configuration", description = "Backs the /preview screen.")
    public ResponseEntity<ApiResponse<PortfolioResponse>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(portfolioService.get(SecurityUtils.currentUserId(), id)));
    }

    @PostMapping
    @Operation(summary = "Create a portfolio configuration manually")
    public ResponseEntity<ApiResponse<PortfolioResponse>> create(@Valid @RequestBody PortfolioRequest request) {
        PortfolioResponse created = portfolioService.create(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Portfolio created", created));
    }

    @PostMapping("/generate")
    @Operation(
            summary = "Generate a portfolio, optionally adapted for a job",
            description = """
                    With `jobAnalysisId`: the recommended projects and matched skills are ordered
                    first, and a job-specific headline and summary are attached.

                    Without it: a generic portfolio ordered by the user's own preferences.

                    **The profile and projects are never modified.** A portfolio stores a template
                    choice, section toggles and ordering only — content is resolved at render time.
                    """)
    public ResponseEntity<ApiResponse<PortfolioResponse>> generate(
            @Valid @RequestBody PortfolioGenerateRequest request) {

        PortfolioResponse created = portfolioService.generate(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Portfolio generated", created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a portfolio configuration", description = "Only supplied fields are changed.")
    public ResponseEntity<ApiResponse<PortfolioResponse>> update(@PathVariable String id,
                                                                 @Valid @RequestBody PortfolioRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Portfolio updated",
                portfolioService.update(SecurityUtils.currentUserId(), id, request)));
    }

    @PostMapping("/{id}/publish")
    @Operation(
            summary = "Publish a portfolio",
            description = """
                    Makes it live at `/portfolio/{username}`. Any previously published portfolio is
                    unpublished first — a partial unique index enforces at most one live portfolio
                    per username, so this is the database's rule, not just the application's.
                    """)
    public ResponseEntity<ApiResponse<PortfolioResponse>> publish(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok("Portfolio published",
                portfolioService.publish(SecurityUtils.currentUserId(), id)));
    }

    @PostMapping("/{id}/unpublish")
    @Operation(summary = "Take a portfolio offline", description = "The public URL starts returning 404.")
    public ResponseEntity<ApiResponse<PortfolioResponse>> unpublish(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok("Portfolio unpublished",
                portfolioService.unpublish(SecurityUtils.currentUserId(), id)));
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Attach a resume", description = "Backs the public \"Download CV\" button.")
    public ResponseEntity<ApiResponse<PortfolioResponse>> attachResume(@PathVariable String id,
                                                                       @RequestParam String resumeId) {
        return ResponseEntity.ok(ApiResponse.ok("Resume attached",
                portfolioService.attachResume(SecurityUtils.currentUserId(), id, resumeId)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a portfolio", description = "Soft delete; it is unpublished at the same time.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        portfolioService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.message("Portfolio deleted"));
    }
}
