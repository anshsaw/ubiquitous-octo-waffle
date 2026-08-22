package com.portfoliopilot.service;

import com.portfoliopilot.dto.common.PageResponse;
import com.portfoliopilot.dto.project.ProjectRequest;
import com.portfoliopilot.dto.project.ProjectResponse;
import com.portfoliopilot.exception.BusinessValidationException;
import com.portfoliopilot.exception.ResourceNotFoundException;
import com.portfoliopilot.model.Project;
import com.portfoliopilot.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Project CRUD.
 *
 * <p>Every method takes {@code userId} as its first parameter and every query
 * includes it. There is no code path that loads a project by id alone.
 *
 * <p>Deletion is SOFT. Hard-deleting would silently corrupt every historical
 * resume and job analysis that references the project; the soft flag keeps that
 * history readable while removing the project from all live surfaces.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final SkillDictionaryService skillDictionary;

    public PageResponse<ProjectResponse> list(String userId, Pageable pageable) {
        return PageResponse.from(
                projectRepository.findByUserIdAndDeletedFalse(userId, pageable),
                ProjectResponse::from);
    }

    public List<ProjectResponse> listAll(String userId) {
        return projectRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    public ProjectResponse get(String userId, String projectId) {
        return ProjectResponse.from(requireOwned(userId, projectId));
    }

    public ProjectResponse create(String userId, ProjectRequest request) {
        List<String> techStack = cleanTechStack(request.resolveTechStack());
        validateDates(request);

        Instant now = Instant.now();
        Project project = Project.builder()
                // Ownership comes from the token. The request body has no say.
                .userId(userId)
                .title(request.title().trim())
                .description(request.description().trim())
                .techStack(techStack)
                .techStackNormalized(normalize(techStack))
                .repositoryUrl(trimToNull(request.resolveRepositoryUrl()))
                .liveDemoUrl(trimToNull(request.resolveLiveDemoUrl()))
                .imageUrl(trimToNull(request.imageUrl()))
                .images(nullSafe(request.images()))
                .role(trimToNull(request.role()))
                .features(nullSafe(request.features()))
                .achievements(nullSafe(request.achievements()))
                .startDate(request.startDate())
                .endDate(request.endDate())
                .featured(Boolean.TRUE.equals(request.featured()))
                // Default ON: a user who bothered to add a project almost always
                // wants it shown. They can opt out per project.
                .includeInPortfolio(request.includeInPortfolio() == null || request.includeInPortfolio())
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Project saved = projectRepository.save(project);
        log.info("User {} created project {}", userId, saved.getId());
        return ProjectResponse.from(saved);
    }

    public ProjectResponse update(String userId, String projectId, ProjectRequest request) {
        Project project = requireOwned(userId, projectId);
        List<String> techStack = cleanTechStack(request.resolveTechStack());
        validateDates(request);

        project.setTitle(request.title().trim());
        project.setDescription(request.description().trim());
        project.setTechStack(techStack);
        project.setTechStackNormalized(normalize(techStack));
        project.setRepositoryUrl(trimToNull(request.resolveRepositoryUrl()));
        project.setLiveDemoUrl(trimToNull(request.resolveLiveDemoUrl()));
        project.setImageUrl(trimToNull(request.imageUrl()));
        project.setImages(nullSafe(request.images()));
        project.setRole(trimToNull(request.role()));
        project.setFeatures(nullSafe(request.features()));
        project.setAchievements(nullSafe(request.achievements()));
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        if (request.featured() != null) {
            project.setFeatured(request.featured());
        }
        if (request.includeInPortfolio() != null) {
            project.setIncludeInPortfolio(request.includeInPortfolio());
        }
        project.setUpdatedAt(Instant.now());

        return ProjectResponse.from(projectRepository.save(project));
    }

    /** The /projects checkbox. */
    public ProjectResponse setPortfolioInclusion(String userId, String projectId, boolean include) {
        Project project = requireOwned(userId, projectId);
        project.setIncludeInPortfolio(include);
        project.setUpdatedAt(Instant.now());
        return ProjectResponse.from(projectRepository.save(project));
    }

    /**
     * Soft delete. Existing resumes and analyses keep their reference; renderers
     * skip the project and fall back to the stored {@code titleSnapshot}.
     */
    public void delete(String userId, String projectId) {
        Project project = requireOwned(userId, projectId);
        project.setDeleted(true);
        project.setDeletedAt(Instant.now());
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        log.info("User {} soft-deleted project {}", userId, projectId);
    }

    // ------------------------------------------------- internal accessors

    /** Non-deleted projects owned by the user. Used by the analyzer and portfolio services. */
    public List<Project> ownedProjects(String userId) {
        return projectRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId);
    }

    /** Projects the user opted into publishing. */
    public List<Project> publishableProjects(String userId) {
        return projectRepository.findByUserIdAndIncludeInPortfolioTrueAndDeletedFalse(userId);
    }

    /**
     * Resolves an explicit id list into projects, preserving the requested order
     * and silently dropping ids that are deleted or not owned - which is exactly
     * what a stale {@code orderedProjects} array needs.
     */
    public List<Project> resolveOrdered(String userId, Collection<String> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return List.of();
        }
        List<Project> found = projectRepository.findByUserIdAndIdInAndDeletedFalse(userId, orderedIds);
        List<String> order = new ArrayList<>(orderedIds);
        return found.stream()
                .sorted(Comparator.comparingInt(p -> order.indexOf(p.getId())))
                .toList();
    }

    public long countOwned(String userId) {
        return projectRepository.countByUserIdAndDeletedFalse(userId);
    }

    /**
     * The ownership gate.
     *
     * <p>Returns 404 rather than 403 for a project owned by someone else: a 403
     * would confirm the id exists, letting an attacker enumerate valid ids.
     */
    private Project requireOwned(String userId, String projectId) {
        return projectRepository.findByIdAndUserIdAndDeletedFalse(projectId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project"));
    }

    // ------------------------------------------------------------ helpers

    private void validateDates(ProjectRequest request) {
        if (request.startDate() != null && request.endDate() != null
                && request.endDate().isBefore(request.startDate())) {
            throw new BusinessValidationException("End date cannot be before start date",
                    Map.of("endDate", "Must be on or after startDate"));
        }
    }

    /** The collection validator requires a non-empty, duplicate-free tech stack. */
    private List<String> cleanTechStack(List<String> raw) {
        List<String> cleaned = new ArrayList<>(new LinkedHashSet<>(
                nullSafe(raw).stream()
                        .filter(t -> t != null && !t.isBlank())
                        .map(String::trim)
                        .toList()));

        if (cleaned.isEmpty()) {
            throw new BusinessValidationException("At least one technology is required",
                    Map.of("techStack", "Provide at least one technology"));
        }
        return cleaned;
    }

    /**
     * Derives the canonical keys the match engine compares against.
     * Two display spellings can collapse to one key, which is intended.
     */
    private List<String> normalize(List<String> techStack) {
        return new ArrayList<>(new LinkedHashSet<>(
                techStack.stream()
                        .map(t -> skillDictionary.resolve(t).normalizedName())
                        .filter(t -> !t.isEmpty())
                        .toList()));
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
