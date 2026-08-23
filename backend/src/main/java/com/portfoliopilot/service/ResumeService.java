package com.portfoliopilot.service;

import com.portfoliopilot.dto.common.PageResponse;
import com.portfoliopilot.dto.resume.ResumeGenerationRequest;
import com.portfoliopilot.dto.resume.ResumeResponse;
import com.portfoliopilot.exception.ResourceNotFoundException;
import com.portfoliopilot.model.JobAnalysis;
import com.portfoliopilot.model.Profile;
import com.portfoliopilot.model.Project;
import com.portfoliopilot.model.Resume;
import com.portfoliopilot.model.embedded.RecommendedProject;
import com.portfoliopilot.model.embedded.ResumeCertificate;
import com.portfoliopilot.model.embedded.ResumeEducation;
import com.portfoliopilot.model.embedded.ResumeExperience;
import com.portfoliopilot.model.embedded.ResumeProject;
import com.portfoliopilot.model.embedded.ResumeSkill;
import com.portfoliopilot.model.embedded.Skill;
import com.portfoliopilot.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generates job-tailored resumes.
 *
 * <p><strong>The rule that governs this whole class: nothing is ever invented.</strong>
 * No company, degree, project, skill, certificate, date or achievement is
 * fabricated or embellished. The generator may only:
 * <ul>
 *   <li>REORDER - matched skills and relevant projects move to the top;</li>
 *   <li>SELECT  - pick which of the user's real bullets to highlight;</li>
 *   <li>SUMMARISE - assemble a summary line from facts already on the profile.</li>
 * </ul>
 * Every field written below is copied from an existing document. There is no
 * code path that writes a value the user did not supply.
 *
 * <p>Storage strategy is deliberately mixed: skills, education, experience and
 * certificates are SNAPSHOT so an already-downloaded PDF stays reproducible,
 * while projects are REFERENCED so their (large) bodies stay current.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    /** Cap on projects printed. A resume is one or two pages, not a catalogue. */
    private static final int MAX_PROJECTS = 4;

    /** Cap on bullets per project, for the same reason. */
    private static final int MAX_BULLETS = 3;

    private final ResumeRepository resumeRepository;
    private final OpportunityService opportunityService;
    private final ProfileService profileService;
    private final ProjectService projectService;

    public ResumeResponse generate(String userId, ResumeGenerationRequest request) {
        JobAnalysis analysis = opportunityService.requireOwned(userId, request.jobAnalysisId());
        Profile profile = profileService.requireProfile(userId);

        Set<String> matchedSkills = new HashSet<>(
                analysis.getAnalysis() == null || analysis.getAnalysis().getStrongSkillsNormalized() == null
                        ? List.of()
                        : analysis.getAnalysis().getStrongSkillsNormalized());

        Instant now = Instant.now();

        Resume resume = Resume.builder()
                .userId(userId)
                .jobAnalysisId(analysis.getId())
                .targetRole(analysis.getJob() == null ? null : analysis.getJob().getTitle())
                .targetCompany(analysis.getJob() == null ? null : analysis.getJob().getCompany())
                // Reused verbatim from the analysis, so the resume and the match
                // screen tell the candidate exactly the same story.
                .summary(analysis.getTailoredSummary())
                .skills(prioritiseSkills(profile.getSkills(), matchedSkills))
                .projects(prioritiseProjects(userId, analysis))
                .education(snapshotEducation(profile))
                .experience(prioritiseExperience(profile, matchedSkills))
                .certificates(snapshotCertificates(profile))
                .template(request.resolveTemplate())
                .downloadCount(0)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Resume saved = resumeRepository.save(resume);

        // Back-pointer so /match-analysis can show "resume already generated".
        opportunityService.linkResume(analysis.getId(), saved.getId());

        log.info("User {} generated resume {} for analysis {} ('{}')",
                userId, saved.getId(), analysis.getId(), saved.getTargetRole());

        return ResumeResponse.from(saved);
    }

    public ResumeResponse get(String userId, String resumeId) {
        return ResumeResponse.from(requireOwned(userId, resumeId));
    }

    public List<ResumeResponse> listAll(String userId) {
        return resumeRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId).stream()
                .map(ResumeResponse::from)
                .toList();
    }

    public PageResponse<ResumeResponse> list(String userId, Pageable pageable) {
        return PageResponse.from(
                resumeRepository.findByUserIdAndDeletedFalse(userId, pageable),
                ResumeResponse::from);
    }

    /** The newest resume for an analysis, if one exists. */
    public Optional<ResumeResponse> findForAnalysis(String userId, String analysisId) {
        opportunityService.requireOwned(userId, analysisId);
        return resumeRepository.findFirstByJobAnalysisIdAndDeletedFalseOrderByCreatedAtDesc(analysisId)
                .map(ResumeResponse::from);
    }

    public void delete(String userId, String resumeId) {
        Resume resume = requireOwned(userId, resumeId);
        resume.setDeleted(true);
        resume.setDeletedAt(Instant.now());
        resume.setUpdatedAt(Instant.now());
        resumeRepository.save(resume);
    }

    /** Called when a PDF is downloaded. Analytics only - never on a read path. */
    public void recordDownload(String userId, String resumeId) {
        Resume resume = requireOwned(userId, resumeId);
        resume.setDownloadCount((resume.getDownloadCount() == null ? 0 : resume.getDownloadCount()) + 1);
        resumeRepository.save(resume);
    }

    private Resume requireOwned(String userId, String resumeId) {
        return resumeRepository.findByIdAndUserIdAndDeletedFalse(resumeId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Resume"));
    }

    // ------------------------------------------------------- prioritisation

    /**
     * Matched skills first, then the rest. Within each group the profile's own
     * order is preserved, and stronger proficiencies lead - a recruiter scanning
     * the first line should see the most relevant, strongest skills.
     *
     * <p>Unmatched skills are KEPT, not dropped: a resume that lists only what
     * the job asked for looks engineered, and hides genuine breadth.
     */
    private List<ResumeSkill> prioritiseSkills(List<Skill> skills, Set<String> matched) {
        if (skills == null) {
            return List.of();
        }
        return skills.stream()
                .sorted(Comparator
                        .comparing((Skill s) -> !matched.contains(s.getNormalizedName()))
                        .thenComparing(s -> -(s.getProficiency() == null ? 0 : s.getProficiency().rank())))
                .map(s -> ResumeSkill.builder()
                        .name(s.getName())
                        .normalizedName(s.getNormalizedName())
                        .proficiency(s.getProficiency())
                        .matched(matched.contains(s.getNormalizedName()))
                        .build())
                .toList();
    }

    /**
     * Recommended projects first, in the engine's relevance order, then any
     * remaining publishable projects as filler up to {@link #MAX_PROJECTS}.
     *
     * <p>Bullets are chosen from the project's real achievements (preferred,
     * because they are quantified) falling back to its features.
     */
    private List<ResumeProject> prioritiseProjects(String userId, JobAnalysis analysis) {
        List<RecommendedProject> recommended = analysis.getRecommendedProjects() == null
                ? List.of()
                : analysis.getRecommendedProjects();

        Map<String, Project> ownedById = projectService.ownedProjects(userId).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity(), (a, b) -> a));

        List<ResumeProject> entries = new ArrayList<>();
        Set<String> used = new HashSet<>();
        int priority = 1;

        for (RecommendedProject rec : recommended) {
            Project project = ownedById.get(rec.getProjectId());
            if (project == null) {
                continue; // deleted since the analysis ran
            }
            entries.add(buildResumeProject(project, priority++));
            used.add(project.getId());
            if (entries.size() >= MAX_PROJECTS) {
                return entries;
            }
        }

        // Fill remaining slots with the user's other publishable projects,
        // featured ones first.
        List<Project> filler = projectService.publishableProjects(userId).stream()
                .filter(p -> !used.contains(p.getId()))
                .sorted(Comparator.comparing(Project::isFeatured).reversed())
                .toList();

        for (Project project : filler) {
            if (entries.size() >= MAX_PROJECTS) {
                break;
            }
            entries.add(buildResumeProject(project, priority++));
        }
        return entries;
    }

    private ResumeProject buildResumeProject(Project project, int priority) {
        List<String> bullets = new ArrayList<>();
        if (project.getAchievements() != null) {
            bullets.addAll(project.getAchievements());
        }
        if (bullets.size() < MAX_BULLETS && project.getFeatures() != null) {
            project.getFeatures().stream()
                    .filter(f -> !bullets.contains(f))
                    .limit(MAX_BULLETS - bullets.size())
                    .forEach(bullets::add);
        }

        return ResumeProject.builder()
                .projectId(project.getId())
                .priority(priority)
                .titleSnapshot(project.getTitle())
                .highlightedBullets(bullets.stream().limit(MAX_BULLETS).toList())
                .build();
    }

    /** Newest first - standard reverse-chronological CV convention. */
    private List<ResumeExperience> prioritiseExperience(Profile profile, Set<String> matched) {
        if (profile.getExperience() == null) {
            return List.of();
        }
        return profile.getExperience().stream()
                .sorted(Comparator.comparing(
                        com.portfoliopilot.model.embedded.Experience::getStartDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(x -> ResumeExperience.builder()
                        .company(x.getCompany())
                        .role(x.getRole())
                        .employmentType(x.getEmploymentType())
                        .startDate(x.getStartDate())
                        .endDate(x.getEndDate())
                        // Copied verbatim. Never rewritten, never embellished.
                        .bullets(x.getResponsibilities() == null ? List.of() : x.getResponsibilities())
                        .build())
                .toList();
    }

    private List<ResumeEducation> snapshotEducation(Profile profile) {
        if (profile.getEducation() == null) {
            return List.of();
        }
        return profile.getEducation().stream()
                .sorted(Comparator.comparing(
                        com.portfoliopilot.model.embedded.Education::getStartYear,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(e -> ResumeEducation.builder()
                        .degree(e.getDegree())
                        .institution(e.getInstitution())
                        .fieldOfStudy(e.getFieldOfStudy())
                        .startYear(e.getStartYear())
                        .endYear(e.getEndYear())
                        .grade(e.getGrade())
                        .build())
                .toList();
    }

    private List<ResumeCertificate> snapshotCertificates(Profile profile) {
        if (profile.getCertificates() == null) {
            return List.of();
        }
        return profile.getCertificates().stream()
                .map(c -> ResumeCertificate.builder()
                        .name(c.getName())
                        .issuingOrganization(c.getIssuingOrganization())
                        .issueDate(c.getIssueDate())
                        .credentialUrl(c.getCredentialUrl())
                        .build())
                .toList();
    }
}
