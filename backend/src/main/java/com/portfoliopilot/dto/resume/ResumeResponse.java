package com.portfoliopilot.dto.resume;

import com.portfoliopilot.model.Resume;
import com.portfoliopilot.model.enums.EmploymentType;
import com.portfoliopilot.model.enums.Proficiency;
import com.portfoliopilot.model.enums.ResumeTemplate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * A generated resume, ready to render.
 *
 * <p>Array order IS render order throughout - matched skills first, most
 * relevant project first. The client must not re-sort; the ordering is the
 * tailoring.
 */
public record ResumeResponse(
        String id,
        String jobAnalysisId,
        String targetRole,
        String targetCompany,
        @Schema(description = "Auto-written summary reflecting this specific role") String summary,
        List<Skill> skills,
        List<ProjectEntry> projects,
        List<EducationEntry> education,
        List<ExperienceEntry> experience,
        List<CertificateEntry> certificates,
        ResumeTemplate template,
        String pdfUrl,
        Instant createdAt,
        Instant updatedAt
) {

    /** @param matched true when the job description explicitly asked for this skill */
    public record Skill(String name, Proficiency proficiency, boolean matched) {
    }

    public record ProjectEntry(String projectId, int priority, String title, List<String> highlightedBullets) {
    }

    public record EducationEntry(String degree, String institution, String fieldOfStudy,
                                 Integer startYear, Integer endYear, String grade) {
    }

    public record ExperienceEntry(String company, String role, EmploymentType employmentType,
                                  Instant startDate, Instant endDate, List<String> bullets) {
    }

    public record CertificateEntry(String name, String issuingOrganization, Instant issueDate, String credentialUrl) {
    }

    public static ResumeResponse from(Resume resume) {
        return new ResumeResponse(
                resume.getId(),
                resume.getJobAnalysisId(),
                resume.getTargetRole(),
                resume.getTargetCompany(),
                resume.getSummary(),
                nullSafe(resume.getSkills()).stream()
                        .map(s -> new Skill(s.getName(), s.getProficiency(), s.isMatched()))
                        .toList(),
                nullSafe(resume.getProjects()).stream()
                        .map(p -> new ProjectEntry(p.getProjectId(), p.getPriority(), p.getTitleSnapshot(),
                                p.getHighlightedBullets() == null ? List.of() : p.getHighlightedBullets()))
                        .toList(),
                nullSafe(resume.getEducation()).stream()
                        .map(e -> new EducationEntry(e.getDegree(), e.getInstitution(), e.getFieldOfStudy(),
                                e.getStartYear(), e.getEndYear(), e.getGrade()))
                        .toList(),
                nullSafe(resume.getExperience()).stream()
                        .map(x -> new ExperienceEntry(x.getCompany(), x.getRole(), x.getEmploymentType(),
                                x.getStartDate(), x.getEndDate(),
                                x.getBullets() == null ? List.of() : x.getBullets()))
                        .toList(),
                nullSafe(resume.getCertificates()).stream()
                        .map(c -> new CertificateEntry(c.getName(), c.getIssuingOrganization(),
                                c.getIssueDate(), c.getCredentialUrl()))
                        .toList(),
                resume.getTemplate(),
                resume.getPdfUrl(),
                resume.getCreatedAt(),
                resume.getUpdatedAt());
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
