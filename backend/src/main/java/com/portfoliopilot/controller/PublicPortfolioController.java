package com.portfoliopilot.controller;

import com.portfoliopilot.dto.common.ApiResponse;
import com.portfoliopilot.dto.portfolio.PublicPortfolioResponse;
import com.portfoliopilot.service.PublicPortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * {@code /api/public/**} - the only unauthenticated route in the API.
 *
 * <p>Kept in its own controller under its own path prefix so the security rule
 * that opens it up is a single, obvious line in {@code SecurityConfig}, rather
 * than an exception buried among authenticated endpoints.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Public Portfolio", description = "Published portfolios - no authentication required")
public class PublicPortfolioController {

    private final PublicPortfolioService publicPortfolioService;

    @GetMapping("/portfolio/{username}")
    @Operation(
            summary = "Get a published portfolio by username",
            description = """
                    Public. Returns only a portfolio that is currently published.

                    The response is an explicit allow-list: account email, phone number, user id,
                    draft portfolios, job analyses and resumes are never included, and projects the
                    owner excluded from their portfolio are omitted entirely — not merely hidden.

                    Returns 404 for an unknown username, an unpublished portfolio, or a suspended
                    account, so the three cases are indistinguishable to a probe.
                    """)
    public ResponseEntity<ApiResponse<PublicPortfolioResponse>> getPublicPortfolio(@PathVariable String username) {
        PublicPortfolioResponse portfolio = publicPortfolioService.getByUsername(username);

        // Analytics only, never awaited - it must not delay the page.
        publicPortfolioService.recordView(portfolio.username());

        return ResponseEntity.ok()
                // A published portfolio changes rarely and is the highest-volume
                // read in the system, so it is worth caching at the edge.
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(ApiResponse.ok(portfolio));
    }
}
