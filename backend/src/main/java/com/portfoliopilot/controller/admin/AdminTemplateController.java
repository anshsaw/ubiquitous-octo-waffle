package com.portfoliopilot.controller.admin;

import com.portfoliopilot.dto.admin.TemplateRequest;
import com.portfoliopilot.dto.common.ApiResponse;
import com.portfoliopilot.dto.portfolio.TemplateResponse;
import com.portfoliopilot.service.admin.AdminTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** {@code /api/admin/templates} - template catalogue management. */
@RestController
@RequestMapping("/api/admin/templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Templates", description = "Manage portfolio templates (ADMIN only)")
public class AdminTemplateController {

    private final AdminTemplateService adminTemplateService;

    @GetMapping
    @Operation(summary = "List all templates", description = "Includes inactive ones, unlike the public endpoint.")
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(adminTemplateService.listAll()));
    }

    @PostMapping
    @Operation(summary = "Create a template")
    public ResponseEntity<ApiResponse<TemplateResponse>> create(@Valid @RequestBody TemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Template created", adminTemplateService.create(request)));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a template",
            description = "templateKey is immutable while any portfolio still renders with it.")
    public ResponseEntity<ApiResponse<TemplateResponse>> update(@PathVariable String id,
                                                                @Valid @RequestBody TemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Template updated", adminTemplateService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Activate or deactivate a template",
            description = "Deactivating removes it from the builder picker; existing portfolios keep rendering.")
    public ResponseEntity<ApiResponse<TemplateResponse>> setStatus(@PathVariable String id,
                                                                   @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.ok(
                active ? "Template activated" : "Template deactivated",
                adminTemplateService.setActive(id, active)));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a template",
            description = "Hard delete, refused while any portfolio references it. Deactivate instead in that case.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        adminTemplateService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Template deleted"));
    }
}
