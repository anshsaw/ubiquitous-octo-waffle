package com.portfoliopilot.controller;

import com.portfoliopilot.dto.common.ApiResponse;
import com.portfoliopilot.dto.portfolio.TemplateResponse;
import com.portfoliopilot.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * {@code GET /api/templates} - the builder's template picker.
 *
 * <p>Public: a template catalogue contains no user data, and leaving it open
 * lets the landing page preview available layouts before sign-up.
 */
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Templates", description = "Portfolio template catalogue")
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping
    @Operation(
            summary = "List active portfolio templates",
            description = "Retired templates are excluded here but keep rendering for portfolios already using them.")
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> list() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(ApiResponse.ok(templateService.listActive()));
    }
}
