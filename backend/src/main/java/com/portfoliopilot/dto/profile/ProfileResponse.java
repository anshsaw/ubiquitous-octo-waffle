package com.portfoliopilot.dto.profile;

import com.portfoliopilot.model.Profile;
import com.portfoliopilot.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/profile}.
 *
 * <p>Joins the {@link User} identity fields the UI needs (name, email, username)
 * onto the {@link Profile} document, so the client makes one call rather than
 * two. {@code passwordHash} and every auth field are structurally absent.
 */
public record ProfileResponse(
        String id,
        String userId,
        String username,
        @Schema(description = "Account email. Not published unless copied into contact.publicEmail.")
        String email,
        String name,
        String avatarUrl,
        @Schema(example = "Full-Stack Developer (Java + React)") String professionalTitle,
        @Schema(description = "Legacy alias of professionalTitle") String title,
        String bio,
        String location,
        ContactDto contact,
        List<SkillDto> skills,
        List<EducationDto> education,
        List<ExperienceDto> experience,
        List<CertificateDto> certificates,
        @Schema(description = "Cached 0-100 completeness score shown on the dashboard") Integer profileHealth,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProfileResponse from(Profile profile, User user) {
        return new ProfileResponse(
                profile.getId(),
                profile.getUserId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getEmail(),
                profile.getFullName() != null ? profile.getFullName() : (user == null ? null : user.getName()),
                profile.getAvatarUrl(),
                profile.getProfessionalTitle(),
                profile.getProfessionalTitle(),
                profile.getBio(),
                profile.getLocation(),
                ContactDto.from(profile.getContact()),
                nullSafe(profile.getSkills()).stream().map(SkillDto::from).toList(),
                nullSafe(profile.getEducation()).stream().map(EducationDto::from).toList(),
                nullSafe(profile.getExperience()).stream().map(ExperienceDto::from).toList(),
                nullSafe(profile.getCertificates()).stream().map(CertificateDto::from).toList(),
                profile.getProfileHealth(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
