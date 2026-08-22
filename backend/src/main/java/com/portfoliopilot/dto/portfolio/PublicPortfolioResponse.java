package com.portfoliopilot.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/public/portfolio/{username}} - the ONLY unauthenticated
 * payload in the product.
 *
 * <p>This record is an explicit ALLOW-LIST. Every field here was chosen to be
 * public. Nothing is inherited from a document, so a field added to
 * {@code Profile} or {@code User} later cannot leak onto the open internet by
 * default.
 *
 * <p>Deliberately absent: account email, phone number, {@code userId},
 * {@code passwordHash}, job analyses, resumes, draft portfolios, and every
 * project with {@code includeInPortfolio = false}.
 */
public record PublicPortfolioResponse(
        String username,
        @Schema(example = "MODERN_DEV") String templateKey,
        Map<String, Boolean> sections,
        List<String> sectionOrder,
        Theme theme,
        Owner owner,
        List<Skill> skills,
        List<Project> projects,
        List<Education> education,
        List<Experience> experience,
        List<Certificate> certificates,
        @Schema(description = "Present when the owner attached a downloadable CV") String resumeId,
        Instant publishedAt
) {

    public record Theme(String primaryColor, String accentColor, Boolean darkMode) {
    }

    /**
     * The public identity.
     *
     * @param publicEmail the address the user explicitly chose to publish - NOT
     *                    the account login email
     */
    public record Owner(
            String fullName,
            String professionalTitle,
            String bio,
            String location,
            String avatarUrl,
            String publicEmail,
            String website,
            String github,
            String linkedin,
            String twitter
    ) {
    }

    public record Skill(String name, String proficiency, String category) {
    }

    public record Project(
            String id,
            String title,
            String description,
            List<String> techStack,
            String repositoryUrl,
            String liveDemoUrl,
            String imageUrl,
            List<String> images,
            String role,
            List<String> features,
            List<String> achievements,
            boolean featured
    ) {
    }

    public record Education(String degree, String institution, String fieldOfStudy,
                            Integer startYear, Integer endYear, String grade) {
    }

    public record Experience(String company, String role, String employmentType, String location,
                             Instant startDate, Instant endDate, String description, List<String> technologies) {
    }

    public record Certificate(String name, String issuingOrganization, Instant issueDate, String credentialUrl) {
    }
}
