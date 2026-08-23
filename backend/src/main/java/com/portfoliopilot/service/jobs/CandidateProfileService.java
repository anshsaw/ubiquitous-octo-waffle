package com.portfoliopilot.service.jobs;

import com.portfoliopilot.model.Portfolio;
import com.portfoliopilot.model.Profile;
import com.portfoliopilot.model.Project;
import com.portfoliopilot.model.embedded.Experience;
import com.portfoliopilot.model.embedded.Skill;
import com.portfoliopilot.repository.PortfolioRepository;
import com.portfoliopilot.service.ProfileService;
import com.portfoliopilot.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the {@link CandidateProfile} from the user's own data.
 *
 * <p><b>The published portfolio is the source of truth.</b> When one exists,
 * only the content it actually exposes is used: its selected projects, its
 * ordered skills, its headline override. So republishing genuinely changes
 * future matches, and a project the user excluded from their portfolio does not
 * quietly influence their job results.
 *
 * <p>The template is deliberately ignored. Changing from Modern to Creative
 * changes pixels, not evidence, and must not move a match score.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateProfileService {

    private final PortfolioRepository portfolioRepository;
    private final ProfileService profileService;
    private final ProjectService projectService;

    public CandidateProfile build(String userId) {
        Profile profile = profileService.requireProfile(userId);
        Portfolio published = portfolioRepository
                .findByUserIdAndPublishedTrueAndDeletedFalse(userId)
                .orElse(null);

        boolean fromPortfolio = published != null;

        // Only projects the portfolio actually shows. Falling back to every
        // project when nothing is published keeps a new user useful.
        List<Project> projects = fromPortfolio
                ? projectService.resolveOrdered(userId, published.getOrderedProjects())
                : projectService.publishableProjects(userId);

        if (fromPortfolio && projects.isEmpty()) {
            // Portfolio published with no project ordering yet.
            projects = projectService.publishableProjects(userId);
        }

        List<Skill> skills = filterSkills(profile, published);

        String title = fromPortfolio && notBlank(published.getHeadlineOverride())
                ? stripHeadline(published.getHeadlineOverride())
                : profile.getProfessionalTitle();

        double years = totalYears(profile);

        return new CandidateProfile(
                fromPortfolio ? "PUBLISHED_PORTFOLIO" : "PROFILE",
                notBlank(title) ? title.trim() : inferTitle(skills),
                skills.stream().map(Skill::getNormalizedName).filter(this::notNull).distinct().toList(),
                skills.stream().map(Skill::getName).filter(this::notNull).distinct().toList(),
                inferExperienceLevel(years, profile),
                years,
                profile.getEducation() == null ? List.of()
                        : profile.getEducation().stream().map(e -> e.getDegree()).filter(this::notNull).toList(),
                projects.stream().map(this::toSummary).toList(),
                demonstratedEvidence(projects, profile),
                profile.getLocation());
    }

    /** Skills the published portfolio exposes, in its order; otherwise all of them. */
    private List<Skill> filterSkills(Profile profile, Portfolio published) {
        List<Skill> all = profile.getSkills() == null ? List.of() : profile.getSkills();
        if (published == null || published.getOrderedSkills() == null || published.getOrderedSkills().isEmpty()) {
            return all;
        }
        List<String> order = published.getOrderedSkills();
        return all.stream()
                .filter(s -> order.contains(s.getNormalizedName()))
                .sorted((a, b) -> Integer.compare(
                        order.indexOf(a.getNormalizedName()), order.indexOf(b.getNormalizedName())))
                .toList();
    }

    /**
     * Skills the portfolio DEMONSTRATES rather than merely lists.
     *
     * <p>This distinction drives the whole "not demonstrated in your portfolio"
     * wording. A skill counts as evidenced when it appears in a shipped
     * project's stack or in real work experience — somewhere a reader could
     * verify it — not because it sits in a skills list.
     */
    private Set<String> demonstratedEvidence(List<Project> projects, Profile profile) {
        Set<String> evidence = new LinkedHashSet<>();
        for (Project project : projects) {
            if (project.getTechStackNormalized() != null) {
                evidence.addAll(project.getTechStackNormalized());
            }
        }
        if (profile.getExperience() != null) {
            profile.getExperience().stream()
                    .filter(x -> x.getTechnologiesNormalized() != null)
                    .forEach(x -> evidence.addAll(x.getTechnologiesNormalized()));
        }
        return evidence;
    }

    /** "Java Backend Developer | Spring Boot + MongoDB" -> "Java Backend Developer". */
    private String stripHeadline(String headline) {
        int pipe = headline.indexOf('|');
        return pipe > 0 ? headline.substring(0, pipe).trim() : headline.trim();
    }

    /** Last resort when no title is set anywhere: name the strongest skills. */
    private String inferTitle(List<Skill> skills) {
        if (skills.isEmpty()) {
            return "Software Developer";
        }
        String top = skills.get(0).getName();
        return top + " Developer";
    }

    private double totalYears(Profile profile) {
        if (profile.getExperience() == null || profile.getExperience().isEmpty()) {
            return 0;
        }
        double total = 0;
        for (Experience x : profile.getExperience()) {
            if (x.getStartDate() == null) {
                continue;
            }
            Instant end = x.getEndDate() == null ? Instant.now() : x.getEndDate();
            total += Duration.between(x.getStartDate(), end).toDays() / 365.0;
        }
        return Math.round(total * 10) / 10.0;
    }

    /**
     * Bands the candidate. Internships alone do not make someone "junior", so an
     * internship-only history stays at ENTRY.
     */
    private String inferExperienceLevel(double years, Profile profile) {
        boolean onlyInternships = profile.getExperience() != null
                && !profile.getExperience().isEmpty()
                && profile.getExperience().stream().allMatch(
                        x -> x.getEmploymentType() == com.portfoliopilot.model.enums.EmploymentType.INTERNSHIP);

        if (years <= 0) return "ENTRY";
        if (onlyInternships || years < 1) return "ENTRY";
        if (years < 2.5) return "JUNIOR";
        if (years < 6) return "MID";
        return "SENIOR";
    }

    private CandidateProfile.ProjectSummary toSummary(Project project) {
        return new CandidateProfile.ProjectSummary(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                project.getTechStackNormalized() == null ? List.of() : project.getTechStackNormalized(),
                project.getTechStack() == null ? List.of() : project.getTechStack());
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private boolean notNull(String value) {
        return value != null && !value.isEmpty();
    }
}
