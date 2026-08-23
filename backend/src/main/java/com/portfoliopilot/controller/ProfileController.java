package com.portfoliopilot.controller;

import com.portfoliopilot.dto.common.ApiResponse;
import com.portfoliopilot.dto.profile.CertificateRequest;
import com.portfoliopilot.dto.profile.EducationRequest;
import com.portfoliopilot.dto.profile.ExperienceRequest;
import com.portfoliopilot.dto.profile.ProfileResponse;
import com.portfoliopilot.dto.profile.SkillRequest;
import com.portfoliopilot.dto.profile.UpdateProfileRequest;
import com.portfoliopilot.security.SecurityUtils;
import com.portfoliopilot.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /api/profile} - the user's single canonical CV dataset.
 *
 * <p>Sub-resources (skills, education, experience, certificates) have their own
 * endpoints rather than being folded into one giant PUT. That way, adding a
 * skill cannot accidentally clear an education list because the client omitted
 * a field.
 *
 * <p>There is no {@code {userId}} path variable anywhere here. The profile is
 * always the caller's, resolved from the token.
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Profile", description = "Personal details, skills, education, experience and certificates")
public class ProfileController {

    private final ProfileService profileService;

    // ------------------------------------------------------------------ read

    @GetMapping
    @Operation(summary = "Get the authenticated user's profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile() {
        return ResponseEntity.ok(ApiResponse.ok(profileService.getProfile(SecurityUtils.currentUserId())));
    }

    // -------------------------------------------------------------- personal

    @PutMapping
    @Operation(
            summary = "Replace the personal block",
            description = "Omitted fields are CLEARED. Use PATCH to update only what you send.")
    public ResponseEntity<ApiResponse<ProfileResponse>> replaceProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Profile updated",
                profileService.updatePersonal(SecurityUtils.currentUserId(), request, false)));
    }

    @PatchMapping
    @Operation(
            summary = "Partially update the personal block",
            description = "Only non-null fields are applied. Collections are untouched.")
    public ResponseEntity<ApiResponse<ProfileResponse>> patchProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Profile updated",
                profileService.updatePersonal(SecurityUtils.currentUserId(), request, true)));
    }

    // ---------------------------------------------------------------- skills

    @PutMapping("/skills")
    @Operation(
            summary = "Replace the whole skill list",
            description = """
                    Skills are de-duplicated by canonical name, keeping the highest proficiency.
                    "React", "React.js" and "ReactJS" collapse into a single skill.
                    """)
    public ResponseEntity<ApiResponse<ProfileResponse>> replaceSkills(
            @Valid @RequestBody List<SkillRequest> requests) {
        return ResponseEntity.ok(ApiResponse.ok("Skills updated",
                profileService.replaceSkills(SecurityUtils.currentUserId(), requests)));
    }

    @PostMapping("/skills")
    @Operation(summary = "Add a skill", description = "Updates in place when the canonical skill already exists.")
    public ResponseEntity<ApiResponse<ProfileResponse>> addSkill(@Valid @RequestBody SkillRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Skill added",
                profileService.addSkill(SecurityUtils.currentUserId(), request)));
    }

    @DeleteMapping("/skills/{skillName}")
    @Operation(summary = "Remove a skill", description = "Any spelling is accepted; it is normalised before matching.")
    public ResponseEntity<ApiResponse<ProfileResponse>> removeSkill(@PathVariable String skillName) {
        return ResponseEntity.ok(ApiResponse.ok("Skill removed",
                profileService.removeSkill(SecurityUtils.currentUserId(), skillName)));
    }

    // ------------------------------------------------------------- education

    @PostMapping("/education")
    @Operation(summary = "Add an education entry")
    public ResponseEntity<ApiResponse<ProfileResponse>> addEducation(@Valid @RequestBody EducationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Education added",
                profileService.addEducation(SecurityUtils.currentUserId(), request)));
    }

    @PutMapping("/education/{educationId}")
    @Operation(summary = "Update an education entry")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateEducation(
            @PathVariable String educationId,
            @Valid @RequestBody EducationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Education updated",
                profileService.updateEducation(SecurityUtils.currentUserId(), educationId, request)));
    }

    @DeleteMapping("/education/{educationId}")
    @Operation(summary = "Delete an education entry")
    public ResponseEntity<ApiResponse<ProfileResponse>> deleteEducation(@PathVariable String educationId) {
        return ResponseEntity.ok(ApiResponse.ok("Education removed",
                profileService.deleteEducation(SecurityUtils.currentUserId(), educationId)));
    }

    // ------------------------------------------------------------ experience

    @PostMapping("/experience")
    @Operation(summary = "Add an experience entry")
    public ResponseEntity<ApiResponse<ProfileResponse>> addExperience(@Valid @RequestBody ExperienceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Experience added",
                profileService.addExperience(SecurityUtils.currentUserId(), request)));
    }

    @PutMapping("/experience/{experienceId}")
    @Operation(summary = "Update an experience entry")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateExperience(
            @PathVariable String experienceId,
            @Valid @RequestBody ExperienceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Experience updated",
                profileService.updateExperience(SecurityUtils.currentUserId(), experienceId, request)));
    }

    @DeleteMapping("/experience/{experienceId}")
    @Operation(summary = "Delete an experience entry")
    public ResponseEntity<ApiResponse<ProfileResponse>> deleteExperience(@PathVariable String experienceId) {
        return ResponseEntity.ok(ApiResponse.ok("Experience removed",
                profileService.deleteExperience(SecurityUtils.currentUserId(), experienceId)));
    }

    // ---------------------------------------------------------- certificates

    @PostMapping("/certificates")
    @Operation(summary = "Add a certificate")
    public ResponseEntity<ApiResponse<ProfileResponse>> addCertificate(
            @Valid @RequestBody CertificateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Certificate added",
                profileService.addCertificate(SecurityUtils.currentUserId(), request)));
    }

    @DeleteMapping("/certificates/{certificateId}")
    @Operation(summary = "Delete a certificate")
    public ResponseEntity<ApiResponse<ProfileResponse>> deleteCertificate(@PathVariable String certificateId) {
        return ResponseEntity.ok(ApiResponse.ok("Certificate removed",
                profileService.deleteCertificate(SecurityUtils.currentUserId(), certificateId)));
    }
}
