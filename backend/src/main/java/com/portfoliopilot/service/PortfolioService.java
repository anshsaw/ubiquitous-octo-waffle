package com.portfoliopilot.service;

import com.portfoliopilot.dto.portfolio.PortfolioGenerateRequest;
import com.portfoliopilot.dto.portfolio.PortfolioRequest;
import com.portfoliopilot.dto.portfolio.PortfolioResponse;
import com.portfoliopilot.exception.BusinessValidationException;
import com.portfoliopilot.exception.ResourceNotFoundException;
import com.portfoliopilot.model.JobAnalysis;
import com.portfoliopilot.model.Portfolio;
import com.portfoliopilot.model.PortfolioTemplate;
import com.portfoliopilot.model.Profile;
import com.portfoliopilot.model.Project;
import com.portfoliopilot.model.User;
import com.portfoliopilot.model.embedded.RecommendedProject;
import com.portfoliopilot.model.embedded.SectionToggles;
import com.portfoliopilot.model.embedded.ThemeSettings;
import com.portfoliopilot.model.enums.PortfolioSection;
import com.portfoliopilot.repository.PortfolioRepository;
import com.portfoliopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Portfolio building, job adaptation and publishing.
 *
 * <p><strong>The non-destructive rule.</strong> Adapting a portfolio for a job
 * never writes to {@code profiles} or {@code projects}. Every method here
 * produces or edits a CONFIGURATION - a template choice, section toggles, and
 * two ordering arrays. The user's canonical data is read, never modified.
 *
 * <pre>
 *   Original Profile  ->  Job Analysis  ->  Tailored Portfolio Configuration
 *        (untouched)                              (new document)
 * </pre>
 *
 * <p>Publishing relies on the partial unique index
 * {@code uniq_published_username}: many drafts may exist, but at most one may be
 * live per username. The pre-check below is only for a friendly message - the
 * index is what actually wins a concurrent double-publish race.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final UserRepository userRepository;
    private final ProfileService profileService;
    private final ProjectService projectService;
    private final OpportunityService opportunityService;
    private final TemplateService templateService;

    // -------------------------------------------------------------- reading

    public List<PortfolioResponse> list(String userId) {
        return portfolioRepository.findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(userId).stream()
                .map(PortfolioResponse::from)
                .toList();
    }

    public PortfolioResponse get(String userId, String portfolioId) {
        return PortfolioResponse.from(requireOwned(userId, portfolioId));
    }

    /** The user's live portfolio, if any. */
    public Optional<PortfolioResponse> published(String userId) {
        return portfolioRepository.findByUserIdAndPublishedTrueAndDeletedFalse(userId)
                .map(PortfolioResponse::from);
    }

    // ------------------------------------------------------------ generation

    /**
     * "Adapt Portfolio for this Job", and also the generic "Generate Portfolio".
     *
     * <p>When {@code jobAnalysisId} is present, the recommended projects lead the
     * ordering and the matched skills lead the skill list. Everything else the
     * user owns still follows - a tailored portfolio narrows emphasis, it does
     * not hide the person.
     */
    public PortfolioResponse generate(String userId, PortfolioGenerateRequest request) {
        User user = requireUser(userId);
        Profile profile = profileService.requireProfile(userId);
        PortfolioTemplate template = templateService.resolve(request.templateId(), request.templateKey());

        JobAnalysis analysis = request.jobAnalysisId() == null || request.jobAnalysisId().isBlank()
                ? null
                : opportunityService.requireOwned(userId, request.jobAnalysisId());

        List<Project> publishable = projectService.publishableProjects(userId);

        Instant now = Instant.now();
        Portfolio portfolio = Portfolio.builder()
                .userId(userId)
                .username(user.getUsername())
                .name(resolveName(request.name(), analysis))
                .templateId(template.getId())
                .templateKey(template.getTemplateKey())
                .sourceJobAnalysisId(analysis == null ? null : analysis.getId())
                .sections(defaultSectionsFor(template, profile, publishable))
                .sectionOrder(new ArrayList<>())
                .orderedSkills(orderSkills(profile, analysis))
                .orderedProjects(orderProjects(publishable, analysis))
                // Job-specific headline lives on the portfolio, NOT on the
                // profile - that is the non-destructive rule in one field.
                .headlineOverride(analysis == null ? null : headlineFor(analysis))
                .summaryOverride(analysis == null ? null : analysis.getTailoredSummary())
                .theme(themeFrom(template))
                .resumeId(analysis == null ? null : analysis.getResumeId())
                .published(false)
                .viewCount(0)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Portfolio saved = portfolioRepository.save(portfolio);

        if (analysis != null) {
            opportunityService.linkPortfolio(analysis.getId(), saved.getId());
            log.info("User {} adapted portfolio {} for analysis {}", userId, saved.getId(), analysis.getId());
        } else {
            log.info("User {} generated generic portfolio {}", userId, saved.getId());
        }

        return PortfolioResponse.from(saved);
    }

    /** Manual creation from the builder, with no source analysis. */
    public PortfolioResponse create(String userId, PortfolioRequest request) {
        User user = requireUser(userId);
        Profile profile = profileService.requireProfile(userId);
        PortfolioTemplate template = templateService.resolve(request.templateId(), request.templateKey());
        List<Project> publishable = projectService.publishableProjects(userId);

        Instant now = Instant.now();
        Portfolio portfolio = Portfolio.builder()
                .userId(userId)
                .username(user.getUsername())
                .name(request.name() == null || request.name().isBlank() ? "My Portfolio" : request.name().trim())
                .templateId(template.getId())
                .templateKey(template.getTemplateKey())
                .sections(request.sections() == null
                        ? defaultSectionsFor(template, profile, publishable)
                        : SectionToggles.fromMap(request.sections()))
                .sectionOrder(sanitiseSectionOrder(request.sectionOrder()))
                .orderedSkills(request.orderedSkills() == null
                        ? new ArrayList<>(profile.getSkillIndex())
                        : sanitiseSkills(request.orderedSkills(), profile))
                .orderedProjects(request.orderedProjects() == null
                        ? publishable.stream().map(Project::getId).toList()
                        : sanitiseProjects(userId, request.orderedProjects()))
                .headlineOverride(request.headlineOverride())
                .summaryOverride(request.summaryOverride())
                .theme(themeFrom(request, template))
                .published(false)
                .viewCount(0)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        validateSections(portfolio.getSections(), template);
        return PortfolioResponse.from(portfolioRepository.save(portfolio));
    }

    public PortfolioResponse update(String userId, String portfolioId, PortfolioRequest request) {
        Portfolio portfolio = requireOwned(userId, portfolioId);
        Profile profile = profileService.requireProfile(userId);

        if (request.templateId() != null || request.templateKey() != null) {
            PortfolioTemplate template = templateService.resolve(request.templateId(), request.templateKey());
            boolean templateChanged = !template.getId().equals(portfolio.getTemplateId());

            portfolio.setTemplateId(template.getId());
            portfolio.setTemplateKey(template.getTemplateKey());

            // Switching template must also adopt that template's colours,
            // unless the caller is explicitly setting their own in this same
            // request. Without this the key changed but the theme stayed on the
            // PREVIOUS template, so the public page rendered the new layout in
            // the old palette - the template appeared not to apply.
            if (templateChanged && !request.hasExplicitTheme()) {
                portfolio.setTheme(themeFrom(template));
            }
        }
        if (request.name() != null && !request.name().isBlank()) {
            portfolio.setName(request.name().trim());
        }
        if (request.sections() != null) {
            portfolio.setSections(SectionToggles.fromMap(request.sections()));
        }
        if (request.sectionOrder() != null) {
            portfolio.setSectionOrder(sanitiseSectionOrder(request.sectionOrder()));
        }
        if (request.orderedSkills() != null) {
            portfolio.setOrderedSkills(sanitiseSkills(request.orderedSkills(), profile));
        }
        if (request.orderedProjects() != null) {
            portfolio.setOrderedProjects(sanitiseProjects(userId, request.orderedProjects()));
        }
        if (request.headlineOverride() != null) {
            portfolio.setHeadlineOverride(blankToNull(request.headlineOverride()));
        }
        if (request.summaryOverride() != null) {
            portfolio.setSummaryOverride(blankToNull(request.summaryOverride()));
        }
        if (request.hasExplicitTheme()) {
            portfolio.setTheme(mergeTheme(portfolio.getTheme(), request));
        }

        portfolio.setUpdatedAt(Instant.now());

        validateSections(portfolio.getSections(), templateService.requireById(portfolio.getTemplateId()));
        return PortfolioResponse.from(portfolioRepository.save(portfolio));
    }

    // ------------------------------------------------------------ publishing

    /**
     * Makes a portfolio live at {@code /portfolio/{username}}.
     *
     * <p>Any previously published portfolio is unpublished first. Doing it in
     * this order matters: the partial unique index would otherwise reject the
     * write, and the user would see a confusing conflict instead of their new
     * portfolio going live.
     */
    public PortfolioResponse publish(String userId, String portfolioId) {
        Portfolio portfolio = requireOwned(userId, portfolioId);
        User user = requireUser(userId);

        portfolioRepository.findByUserIdAndPublishedTrueAndDeletedFalse(userId)
                .filter(current -> !current.getId().equals(portfolioId))
                .ifPresent(current -> {
                    current.setPublished(false);
                    current.setUpdatedAt(Instant.now());
                    portfolioRepository.save(current);
                    log.info("Unpublished portfolio {} to make room for {}", current.getId(), portfolioId);
                });

        // Re-sync the denormalised handle in case the username changed since
        // this portfolio was created.
        portfolio.setUsername(user.getUsername());
        portfolio.setPublished(true);
        portfolio.setPublishedAt(Instant.now());
        portfolio.setUpdatedAt(Instant.now());

        Portfolio saved = portfolioRepository.save(portfolio);
        log.info("User {} published portfolio {} at /portfolio/{}", userId, portfolioId, user.getUsername());
        return PortfolioResponse.from(saved);
    }

    public PortfolioResponse unpublish(String userId, String portfolioId) {
        Portfolio portfolio = requireOwned(userId, portfolioId);
        portfolio.setPublished(false);
        portfolio.setUpdatedAt(Instant.now());
        log.info("User {} unpublished portfolio {}", userId, portfolioId);
        return PortfolioResponse.from(portfolioRepository.save(portfolio));
    }

    public void delete(String userId, String portfolioId) {
        Portfolio portfolio = requireOwned(userId, portfolioId);
        portfolio.setDeleted(true);
        portfolio.setDeletedAt(Instant.now());
        // Unpublish on delete, otherwise the partial unique index would keep
        // treating this row as the live one and block the next publish.
        portfolio.setPublished(false);
        portfolio.setUpdatedAt(Instant.now());
        portfolioRepository.save(portfolio);
    }

    /** Attaches a downloadable CV to the public page. */
    public PortfolioResponse attachResume(String userId, String portfolioId, String resumeId) {
        Portfolio portfolio = requireOwned(userId, portfolioId);
        portfolio.setResumeId(resumeId);
        portfolio.setUpdatedAt(Instant.now());
        return PortfolioResponse.from(portfolioRepository.save(portfolio));
    }

    // --------------------------------------------------------------- helpers

    private Portfolio requireOwned(String userId, String portfolioId) {
        return portfolioRepository.findByIdAndUserIdAndDeletedFalse(portfolioId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Portfolio"));
    }

    private User requireUser(String userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User"));
    }

    private String resolveName(String requested, JobAnalysis analysis) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        if (analysis != null && analysis.getJob() != null && analysis.getJob().getTitle() != null) {
            return analysis.getJob().getTitle() + " Portfolio";
        }
        return "My Portfolio";
    }

    private String headlineFor(JobAnalysis analysis) {
        if (analysis.getJob() == null || analysis.getJob().getTitle() == null) {
            return null;
        }
        List<String> strong = analysis.getAnalysis() == null ? List.of() : analysis.getAnalysis().getStrongSkills();
        if (strong == null || strong.isEmpty()) {
            return analysis.getJob().getTitle();
        }
        return analysis.getJob().getTitle() + " | " + String.join(" + ", strong.subList(0, Math.min(3, strong.size())));
    }

    /**
     * Matched skills first, then everything else the user has.
     * Only ordering changes; no skill is deleted from the profile.
     */
    private List<String> orderSkills(Profile profile, JobAnalysis analysis) {
        List<String> all = profile.getSkillIndex() == null ? List.of() : profile.getSkillIndex();
        if (analysis == null || analysis.getAnalysis() == null) {
            return new ArrayList<>(all);
        }
        List<String> strong = analysis.getAnalysis().getStrongSkillsNormalized();
        if (strong == null || strong.isEmpty()) {
            return new ArrayList<>(all);
        }

        Set<String> ordered = new LinkedHashSet<>();
        strong.stream().filter(all::contains).forEach(ordered::add);
        ordered.addAll(all);
        return new ArrayList<>(ordered);
    }

    /** Recommended projects lead, in relevance order; the rest follow, featured first. */
    private List<String> orderProjects(List<Project> publishable, JobAnalysis analysis) {
        if (analysis == null || analysis.getRecommendedProjects() == null) {
            return publishable.stream()
                    .sorted(Comparator.comparing(Project::isFeatured).reversed())
                    .map(Project::getId)
                    .toList();
        }

        Set<String> publishableIds = new LinkedHashSet<>(publishable.stream().map(Project::getId).toList());
        Set<String> ordered = new LinkedHashSet<>();

        analysis.getRecommendedProjects().stream()
                .map(RecommendedProject::getProjectId)
                .filter(publishableIds::contains)
                .forEach(ordered::add);

        publishable.stream()
                .sorted(Comparator.comparing(Project::isFeatured).reversed())
                .map(Project::getId)
                .forEach(ordered::add);

        return new ArrayList<>(ordered);
    }

    /** Enables only sections the template supports AND the profile can actually fill. */
    private SectionToggles defaultSectionsFor(PortfolioTemplate template, Profile profile, List<Project> projects) {
        List<String> available = template.getAvailableSections() == null
                ? PortfolioSection.defaultOrder().stream().map(PortfolioSection::key).toList()
                : template.getAvailableSections();

        SectionToggles toggles = SectionToggles.builder().build();
        for (PortfolioSection section : PortfolioSection.values()) {
            boolean supported = available.contains(section.key());
            boolean hasContent = switch (section) {
                case ABOUT, CONTACT, SKILLS -> true;
                case PROJECTS -> !projects.isEmpty();
                case EDUCATION -> profile.getEducation() != null && !profile.getEducation().isEmpty();
                case EXPERIENCE -> profile.getExperience() != null && !profile.getExperience().isEmpty();
                case CERTIFICATES -> profile.getCertificates() != null && !profile.getCertificates().isEmpty();
            };
            toggles.set(section, supported && hasContent);
        }
        return toggles;
    }

    /** A template cannot be asked to render a section it has no layout for. */
    private void validateSections(SectionToggles sections, PortfolioTemplate template) {
        if (template.getAvailableSections() == null || template.getAvailableSections().isEmpty()) {
            return;
        }
        for (PortfolioSection section : PortfolioSection.values()) {
            if (sections.isEnabled(section) && !template.getAvailableSections().contains(section.key())) {
                throw new BusinessValidationException(
                        "Template '" + template.getName() + "' does not support the '" + section.key() + "' section",
                        Map.of("sections", section.key() + " is not available for this template"));
            }
        }
    }

    private List<String> sanitiseSectionOrder(List<String> requested) {
        if (requested == null) {
            return new ArrayList<>();
        }
        Set<String> valid = new LinkedHashSet<>();
        for (String key : requested) {
            try {
                valid.add(PortfolioSection.fromKey(key).key());
            } catch (IllegalArgumentException ignored) {
                // Silently drop unknown keys rather than failing the whole save.
            }
        }
        return new ArrayList<>(valid);
    }

    /** Keeps only skills the user actually has - a portfolio cannot advertise a skill off-profile. */
    private List<String> sanitiseSkills(List<String> requested, Profile profile) {
        Set<String> owned = new LinkedHashSet<>(
                profile.getSkillIndex() == null ? List.of() : profile.getSkillIndex());
        return requested.stream().filter(owned::contains).distinct().toList();
    }

    /** Keeps only projects the caller owns - ordering can never smuggle in a foreign project. */
    private List<String> sanitiseProjects(String userId, List<String> requested) {
        return projectService.resolveOrdered(userId, requested).stream()
                .map(Project::getId)
                .toList();
    }

    private ThemeSettings themeFrom(PortfolioTemplate template) {
        ThemeSettings source = template.getTheme();
        if (source == null) {
            return null;
        }
        return ThemeSettings.builder()
                .primaryColor(source.getPrimaryColor())
                .accentColor(source.getAccentColor())
                .darkMode(source.getDarkMode())
                .build();
    }

    private ThemeSettings themeFrom(PortfolioRequest request, PortfolioTemplate template) {
        ThemeSettings base = themeFrom(template);
        return ThemeSettings.builder()
                .primaryColor(request.primaryColor() != null ? request.primaryColor()
                        : base == null ? null : base.getPrimaryColor())
                .accentColor(request.accentColor() != null ? request.accentColor()
                        : base == null ? null : base.getAccentColor())
                .darkMode(request.darkMode() != null ? request.darkMode()
                        : base == null ? null : base.getDarkMode())
                .build();
    }

    /** Applies only the colours the request actually supplied; the rest are kept. */
    private ThemeSettings mergeTheme(ThemeSettings current, PortfolioRequest request) {
        return ThemeSettings.builder()
                .primaryColor(request.primaryColor() != null ? request.primaryColor()
                        : current == null ? null : current.getPrimaryColor())
                .accentColor(request.accentColor() != null ? request.accentColor()
                        : current == null ? null : current.getAccentColor())
                .darkMode(request.darkMode() != null ? request.darkMode()
                        : current == null ? null : current.getDarkMode())
                .backgroundColor(request.backgroundColor() != null ? request.backgroundColor()
                        : current == null ? null : current.getBackgroundColor())
                .surfaceColor(request.surfaceColor() != null ? request.surfaceColor()
                        : current == null ? null : current.getSurfaceColor())
                .inkColor(request.inkColor() != null ? request.inkColor()
                        : current == null ? null : current.getInkColor())
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
