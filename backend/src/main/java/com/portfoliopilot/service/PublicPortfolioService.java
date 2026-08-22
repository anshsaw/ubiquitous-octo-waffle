package com.portfoliopilot.service;

import com.portfoliopilot.dto.portfolio.PublicPortfolioResponse;
import com.portfoliopilot.exception.ResourceNotFoundException;
import com.portfoliopilot.model.Portfolio;
import com.portfoliopilot.model.Profile;
import com.portfoliopilot.model.Project;
import com.portfoliopilot.model.embedded.ContactInfo;
import com.portfoliopilot.model.embedded.SectionToggles;
import com.portfoliopilot.model.embedded.ThemeSettings;
import com.portfoliopilot.model.enums.PortfolioSection;
import com.portfoliopilot.repository.PortfolioRepository;
import com.portfoliopilot.repository.ProfileRepository;
import com.portfoliopilot.repository.ProjectRepository;
import com.portfoliopilot.util.SkillNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Resolves {@code /portfolio/{username}} for anonymous visitors.
 *
 * <p>Security posture for this class:
 * <ul>
 *   <li>Only a portfolio that is {@code isPublished = true} and not deleted is
 *       ever returned. Drafts are invisible.</li>
 *   <li>The response is built field by field into an explicit allow-list record.
 *       No document is serialised directly, so a field added to {@code Profile}
 *       later cannot leak by default.</li>
 *   <li>Account email, phone, {@code userId} and every auth field are omitted.
 *       Only {@code contact.publicEmail} - which the user explicitly filled in -
 *       is exposed.</li>
 *   <li>Sections the owner switched off are returned EMPTY, not merely hidden by
 *       the client. Hiding in CSS is not privacy.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicPortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final ProfileRepository profileRepository;
    private final ProjectRepository projectRepository;

    public PublicPortfolioResponse getByUsername(String rawUsername) {
        String username = SkillNormalizer.normalizeUsername(rawUsername);

        // Single hit on the partial unique index uniq_published_username.
        Portfolio portfolio = portfolioRepository
                .findByUsernameAndPublishedTrueAndDeletedFalse(username)
                .orElseThrow(() -> ResourceNotFoundException.of("Published portfolio"));

        Profile profile = profileRepository.findByUserId(portfolio.getUserId())
                .orElseThrow(() -> ResourceNotFoundException.of("Published portfolio"));

        SectionToggles sections = portfolio.getSections() == null
                ? SectionToggles.builder().build()
                : portfolio.getSections();

        return new PublicPortfolioResponse(
                portfolio.getUsername(),
                portfolio.getTemplateKey(),
                sections.asMap(),
                resolveSectionOrder(portfolio),
                theme(portfolio.getTheme()),
                owner(profile, portfolio, sections),
                sections.isEnabled(PortfolioSection.SKILLS) ? skills(profile, portfolio) : List.of(),
                sections.isEnabled(PortfolioSection.PROJECTS) ? projects(portfolio) : List.of(),
                sections.isEnabled(PortfolioSection.EDUCATION) ? education(profile) : List.of(),
                sections.isEnabled(PortfolioSection.EXPERIENCE) ? experience(profile) : List.of(),
                sections.isEnabled(PortfolioSection.CERTIFICATES) ? certificates(profile) : List.of(),
                portfolio.getResumeId(),
                portfolio.getPublishedAt());
    }

    /**
     * Fire-and-forget view counter.
     *
     * <p>Async and exception-swallowing on purpose: analytics must never slow
     * down or break the rendering of a public page.
     */
    @Async
    public void recordView(String username) {
        try {
            portfolioRepository.findByUsernameAndPublishedTrueAndDeletedFalse(username)
                    .ifPresent(portfolio -> {
                        portfolio.setViewCount((portfolio.getViewCount() == null ? 0 : portfolio.getViewCount()) + 1);
                        portfolioRepository.save(portfolio);
                    });
        } catch (RuntimeException ex) {
            log.debug("View count update failed for {}: {}", username, ex.getMessage());
        }
    }

    // ------------------------------------------------------- projections

    private List<String> resolveSectionOrder(Portfolio portfolio) {
        if (portfolio.getSectionOrder() != null && !portfolio.getSectionOrder().isEmpty()) {
            return portfolio.getSectionOrder();
        }
        return PortfolioSection.defaultOrder().stream().map(PortfolioSection::key).toList();
    }

    private PublicPortfolioResponse.Theme theme(ThemeSettings theme) {
        if (theme == null) {
            return new PublicPortfolioResponse.Theme(null, null, null);
        }
        return new PublicPortfolioResponse.Theme(
                theme.getPrimaryColor(), theme.getAccentColor(), theme.getDarkMode());
    }

    /**
     * The public identity.
     *
     * <p>Portfolio-level overrides win over profile values, which is how a
     * job-adapted portfolio shows a targeted headline without ever altering the
     * profile itself.
     */
    private PublicPortfolioResponse.Owner owner(Profile profile, Portfolio portfolio, SectionToggles sections) {
        ContactInfo contact = profile.getContact() == null ? new ContactInfo() : profile.getContact();
        boolean contactVisible = sections.isEnabled(PortfolioSection.CONTACT);

        return new PublicPortfolioResponse.Owner(
                profile.getFullName(),
                portfolio.getHeadlineOverride() != null
                        ? portfolio.getHeadlineOverride()
                        : profile.getProfessionalTitle(),
                sections.isEnabled(PortfolioSection.ABOUT)
                        ? (portfolio.getSummaryOverride() != null
                        ? portfolio.getSummaryOverride()
                        : profile.getBio())
                        : null,
                profile.getLocation(),
                profile.getAvatarUrl(),
                // phone is NEVER exposed, and the rest only when the owner
                // enabled the contact section.
                contactVisible ? contact.getPublicEmail() : null,
                contactVisible ? contact.getWebsite() : null,
                contactVisible ? contact.getGithub() : null,
                contactVisible ? contact.getLinkedin() : null,
                contactVisible ? contact.getTwitter() : null);
    }

    /** Skills in the portfolio's chosen order; unlisted skills are omitted entirely. */
    private List<PublicPortfolioResponse.Skill> skills(Profile profile, Portfolio portfolio) {
        if (profile.getSkills() == null) {
            return List.of();
        }
        List<String> order = portfolio.getOrderedSkills() == null ? List.of() : portfolio.getOrderedSkills();

        if (order.isEmpty()) {
            return profile.getSkills().stream().map(this::toPublicSkill).toList();
        }

        Map<String, com.portfoliopilot.model.embedded.Skill> byKey = new java.util.HashMap<>();
        profile.getSkills().forEach(s -> byKey.put(s.getNormalizedName(), s));

        List<PublicPortfolioResponse.Skill> ordered = new ArrayList<>();
        for (String key : order) {
            var skill = byKey.get(key);
            if (skill != null) {
                ordered.add(toPublicSkill(skill));
            }
        }
        return ordered;
    }

    private PublicPortfolioResponse.Skill toPublicSkill(com.portfoliopilot.model.embedded.Skill skill) {
        return new PublicPortfolioResponse.Skill(
                skill.getName(),
                skill.getProficiency() == null ? null : skill.getProficiency().name(),
                skill.getCategory() == null ? null : skill.getCategory().name());
    }

    /**
     * Projects in the portfolio's order.
     *
     * <p>Filtered twice by design: the query only returns opted-in, non-deleted
     * projects, and ids in {@code orderedProjects} that no longer resolve are
     * dropped. A stale ordering array can therefore never resurrect a project
     * the user removed from their portfolio.
     */
    private List<PublicPortfolioResponse.Project> projects(Portfolio portfolio) {
        List<Project> publishable = projectRepository
                .findByUserIdAndIncludeInPortfolioTrueAndDeletedFalse(portfolio.getUserId());

        List<String> order = portfolio.getOrderedProjects() == null ? List.of() : portfolio.getOrderedProjects();

        List<Project> ordered = order.isEmpty()
                ? publishable.stream().sorted(Comparator.comparing(Project::isFeatured).reversed()).toList()
                : publishable.stream()
                .filter(p -> order.contains(p.getId()))
                .sorted(Comparator.comparingInt(p -> order.indexOf(p.getId())))
                .toList();

        return ordered.stream()
                .map(p -> new PublicPortfolioResponse.Project(
                        p.getId(),
                        p.getTitle(),
                        p.getDescription(),
                        p.getTechStack() == null ? List.of() : p.getTechStack(),
                        p.getRepositoryUrl(),
                        p.getLiveDemoUrl(),
                        p.getImageUrl(),
                        p.getImages() == null ? List.of() : p.getImages(),
                        p.getRole(),
                        p.getFeatures() == null ? List.of() : p.getFeatures(),
                        p.getAchievements() == null ? List.of() : p.getAchievements(),
                        p.isFeatured()))
                .toList();
    }

    private List<PublicPortfolioResponse.Education> education(Profile profile) {
        if (profile.getEducation() == null) {
            return List.of();
        }
        return profile.getEducation().stream()
                .sorted(Comparator.comparing(
                        com.portfoliopilot.model.embedded.Education::getStartYear,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(e -> new PublicPortfolioResponse.Education(
                        e.getDegree(), e.getInstitution(), e.getFieldOfStudy(),
                        e.getStartYear(), e.getEndYear(), e.getGrade()))
                .toList();
    }

    private List<PublicPortfolioResponse.Experience> experience(Profile profile) {
        if (profile.getExperience() == null) {
            return List.of();
        }
        return profile.getExperience().stream()
                .sorted(Comparator.comparing(
                        com.portfoliopilot.model.embedded.Experience::getStartDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(x -> new PublicPortfolioResponse.Experience(
                        x.getCompany(), x.getRole(),
                        x.getEmploymentType() == null ? null : x.getEmploymentType().name(),
                        x.getLocation(), x.getStartDate(), x.getEndDate(), x.getDescription(),
                        x.getTechnologies() == null ? List.of() : x.getTechnologies()))
                .toList();
    }

    private List<PublicPortfolioResponse.Certificate> certificates(Profile profile) {
        if (profile.getCertificates() == null) {
            return List.of();
        }
        return profile.getCertificates().stream()
                .map(c -> new PublicPortfolioResponse.Certificate(
                        c.getName(), c.getIssuingOrganization(), c.getIssueDate(), c.getCredentialUrl()))
                .toList();
    }
}
