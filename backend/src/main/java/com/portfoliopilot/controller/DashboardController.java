package com.portfoliopilot.controller;

import com.portfoliopilot.dto.common.ApiResponse;
import com.portfoliopilot.dto.dashboard.DashboardResponse;
import com.portfoliopilot.security.SecurityUtils;
import com.portfoliopilot.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/dashboard} - stat cards plus recent analyses in one call. */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard", description = "The authenticated user's home screen")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Dashboard summary for the authenticated user")
    public ResponseEntity<ApiResponse<DashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.forUser(SecurityUtils.currentUserId())));
    }
}
