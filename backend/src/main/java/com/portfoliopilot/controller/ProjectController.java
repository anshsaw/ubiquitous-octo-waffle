package com.portfoliopilot.controller;

import com.portfoliopilot.dto.common.ApiResponse;
import com.portfoliopilot.dto.common.PageResponse;
import com.portfoliopilot.dto.project.ProjectRequest;
import com.portfoliopilot.dto.project.ProjectResponse;
import com.portfoliopilot.security.SecurityUtils;
import com.portfoliopilot.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /api/projects} - the authenticated user's projects.
 *
 * <p>Supports both a paginated form (for large collections) and an unpaginated
 * one, because the existing /projects grid renders everything at once while the
 * builder and analyzer only need a page.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Projects", description = "Portfolio projects owned by the authenticated user")
public class ProjectController {

    /** Guard against a client asking for an unbounded page. */
    private static final int MAX_PAGE_SIZE = 100;

    private final ProjectService projectService;

    @GetMapping
    @Operation(
            summary = "List the user's projects",
            description = "Paginated by default. Pass `paged=false` to receive the full list for a grid view.")
    public ResponseEntity<ApiResponse<?>> list(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Return every project instead of a page") @RequestParam(defaultValue = "true") boolean paged) {

        String userId = SecurityUtils.currentUserId();

        if (!paged) {
            List<ProjectResponse> all = projectService.listAll(userId);
            return ResponseEntity.ok(ApiResponse.ok(all));
        }

        PageResponse<ProjectResponse> result = projectService.list(
                userId,
                PageRequest.of(Math.max(0, page),
                        Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get one project",
            description = "Returns 404 - not 403 - for a project owned by someone else, so ids cannot be probed.")
    public ResponseEntity<ApiResponse<ProjectResponse>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.get(SecurityUtils.currentUserId(), id)));
    }

    @PostMapping
    @Operation(
            summary = "Create a project",
            description = "Ownership is taken from the access token. A `userId` in the body is ignored.")
    public ResponseEntity<ApiResponse<ProjectResponse>> create(@Valid @RequestBody ProjectRequest request) {
        ProjectResponse created = projectService.create(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Project created", created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a project")
    public ResponseEntity<ApiResponse<ProjectResponse>> update(@PathVariable String id,
                                                               @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Project updated",
                projectService.update(SecurityUtils.currentUserId(), id, request)));
    }

    @PatchMapping("/{id}/portfolio")
    @Operation(
            summary = "Toggle portfolio inclusion",
            description = "Excluded projects never appear in a generated portfolio or on the public page.")
    public ResponseEntity<ApiResponse<ProjectResponse>> togglePortfolio(
            @PathVariable String id,
            @RequestParam boolean include) {
        return ResponseEntity.ok(ApiResponse.ok(
                include ? "Project included in portfolio" : "Project excluded from portfolio",
                projectService.setPortfolioInclusion(SecurityUtils.currentUserId(), id, include)));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a project",
            description = "Soft delete. Existing resumes and analyses keep a readable reference to it.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        projectService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.message("Project deleted"));
    }
}
