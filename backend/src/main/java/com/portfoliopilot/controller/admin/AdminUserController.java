package com.portfoliopilot.controller.admin;

import com.portfoliopilot.dto.admin.AdminUserDetailResponse;
import com.portfoliopilot.dto.admin.AdminUserResponse;
import com.portfoliopilot.dto.common.ApiResponse;
import com.portfoliopilot.dto.common.PageResponse;
import com.portfoliopilot.model.enums.Role;
import com.portfoliopilot.model.enums.UserStatus;
import com.portfoliopilot.service.admin.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/** {@code /api/admin/users} - moderation and inspection. */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Users", description = "Search, inspect, suspend and delete accounts (ADMIN only)")
public class AdminUserController {

    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Sorting is restricted to an allow-list.
     *
     * <p>Passing a user-supplied string straight into {@code Sort.by} lets a
     * caller sort by {@code passwordHash}, which combined with pagination is a
     * slow but real way to extract hash prefixes.
     */
    private static final Set<String> SORTABLE =
            Set.of("createdAt", "name", "email", "username", "status", "lastLoginAt");

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(
            summary = "Search and filter users",
            description = "Search matches name, email or username. Sorting is limited to an allow-list of fields.")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> list(
            @Parameter(description = "Free-text search") @RequestParam(required = false) String search,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "field,direction e.g. createdAt,desc") @RequestParam(defaultValue = "createdAt,desc") String sort) {

        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                parseSort(sort));

        return ResponseEntity.ok(ApiResponse.ok(
                adminUserService.search(search, status, role, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Full user detail",
            description = "Profile, projects, recent analyses, published portfolio link and audit trail. The view itself is audited.")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> detail(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.detail(id)));
    }

    @PatchMapping("/{id}/suspend")
    @Operation(
            summary = "Suspend an account",
            description = "Blocks sign-in, revokes every active session immediately, and unpublishes their portfolio.")
    public ResponseEntity<ApiResponse<AdminUserResponse>> suspend(
            @PathVariable String id,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.ok("User suspended", adminUserService.suspend(id, reason)));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Reactivate an account", description = "Also clears any brute-force lockout.")
    public ResponseEntity<ApiResponse<AdminUserResponse>> activate(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok("User activated", adminUserService.activate(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete an account",
            description = "Soft delete, cascading to the user's projects, analyses, resumes and portfolios. Reversible.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id,
                                                    @RequestParam(required = false) String reason) {
        adminUserService.softDelete(id, reason);
        return ResponseEntity.ok(ApiResponse.message("User deleted"));
    }

    /** Parses {@code field,direction}, rejecting any field outside the allow-list. */
    private Sort parseSort(String sort) {
        List<String> parts = List.of(sort.split(","));
        String field = parts.get(0).trim();
        if (!SORTABLE.contains(field)) {
            field = "createdAt";
        }
        Sort.Direction direction = parts.size() > 1 && parts.get(1).trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
