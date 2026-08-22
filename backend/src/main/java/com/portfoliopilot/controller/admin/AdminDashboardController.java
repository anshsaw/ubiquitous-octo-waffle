package com.portfoliopilot.controller.admin;

import com.portfoliopilot.dto.admin.AdminDashboardResponse;
import com.portfoliopilot.dto.admin.ChartPoint;
import com.portfoliopilot.dto.common.ApiResponse;
import com.portfoliopilot.service.admin.AdminAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * {@code /api/admin/dashboard/**} - stat cards and charts.
 *
 * <p>{@code @PreAuthorize} here is intentionally redundant with the
 * {@code /api/admin/**} rule in {@code SecurityConfig}: defence in depth means a
 * future refactor of the URL rules cannot silently expose these endpoints.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Dashboard", description = "Platform statistics and charts (ADMIN only)")
public class AdminDashboardController {

    private final AdminAnalyticsService analyticsService;

    @GetMapping("/stats")
    @Operation(
            summary = "Platform stat cards",
            description = "Total users, published portfolios, jobs analyzed today, average match score.")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.stats()));
    }

    @GetMapping("/signup-trends")
    @Operation(
            summary = "Daily signups, analyses and publishes",
            description = "Gap-filled series, so days with no activity still return a zero point.")
    public ResponseEntity<ApiResponse<Map<String, List<ChartPoint.DailyPoint>>>> signupTrends(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.signupTrends(days)));
    }

    @GetMapping("/skill-gaps")
    @Operation(
            summary = "Top skill gaps, most requested skills and most analysed roles",
            description = """
                    Gaps are grouped on the canonical skill key, so "React", "React.js" and
                    "ReactJS" count as one skill rather than three bars.

                    Ranked by DISTINCT USERS affected, not raw occurrences — one heavy user cannot
                    dominate the chart.
                    """)
    public ResponseEntity<ApiResponse<Map<String, Object>>> skillGaps(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.skillInsights(limit)));
    }
}
