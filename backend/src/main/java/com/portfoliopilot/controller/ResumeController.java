package com.portfoliopilot.controller;

import com.portfoliopilot.dto.common.ApiResponse;
import com.portfoliopilot.dto.resume.ResumeGenerationRequest;
import com.portfoliopilot.dto.resume.ResumeResponse;
import com.portfoliopilot.exception.ResourceNotFoundException;
import com.portfoliopilot.security.SecurityUtils;
import com.portfoliopilot.service.ResumeService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /api/resumes} - tailored resume generation.
 *
 * <p>PDF rendering is intentionally left to the client (the existing frontend
 * prints the resume view). The API returns structured, ordered content; turning
 * that into a document is a presentation concern, and keeping it out of the
 * backend avoids shipping a headless browser.
 */
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Resumes", description = "Generate and manage job-tailored resumes")
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping("/generate")
    @Operation(
            summary = "Generate a tailored resume from an analysis",
            description = """
                    Builds a resume for the job behind `jobAnalysisId`.

                    **Nothing is invented.** No company, degree, project, skill or achievement is
                    fabricated. The generator only reorders the user's real data (matched skills
                    and relevant projects first), selects which existing bullets to highlight, and
                    reuses the summary already produced by the analysis.
                    """)
    public ResponseEntity<ApiResponse<ResumeResponse>> generate(
            @Valid @RequestBody ResumeGenerationRequest request) {

        ResumeResponse response = resumeService.generate(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Resume generated", response));
    }

    @GetMapping
    @Operation(summary = "List the user's resumes, newest first")
    public ResponseEntity<ApiResponse<List<ResumeResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(resumeService.listAll(SecurityUtils.currentUserId())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one resume")
    public ResponseEntity<ApiResponse<ResumeResponse>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(resumeService.get(SecurityUtils.currentUserId(), id)));
    }

    @GetMapping("/by-analysis/{analysisId}")
    @Operation(
            summary = "Get the latest resume generated for an analysis",
            description = "Lets /match-analysis show an existing resume instead of regenerating one.")
    public ResponseEntity<ApiResponse<ResumeResponse>> getForAnalysis(@PathVariable String analysisId) {
        ResumeResponse response = resumeService
                .findForAnalysis(SecurityUtils.currentUserId(), analysisId)
                .orElseThrow(() -> ResourceNotFoundException.of("Resume for this analysis"));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/download")
    @Operation(summary = "Record a download", description = "Analytics counter. Returns the resume content.")
    public ResponseEntity<ApiResponse<ResumeResponse>> download(@PathVariable String id) {
        String userId = SecurityUtils.currentUserId();
        resumeService.recordDownload(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(resumeService.get(userId, id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a resume", description = "Soft delete.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        resumeService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.ok(ApiResponse.message("Resume deleted"));
    }
}
