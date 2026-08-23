package com.portfoliopilot.service.match;

import com.portfoliopilot.model.Profile;
import com.portfoliopilot.model.Project;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The candidate side of a match: their profile plus the projects that may be
 * cited as evidence.
 *
 * <p>Exposes {@link #allNormalizedSkills()} which unions declared skills with
 * technologies used in experience and projects. That matters: a candidate who
 * shipped a Docker-based project but never added "Docker" to their skill list
 * genuinely does have the skill, and reporting it as a gap would be wrong.
 */
public record CandidateSnapshot(
        Profile profile,
        List<Project> projects
) {

    /** Skills the user explicitly declared. */
    public Set<String> declaredSkills() {
        if (profile == null || profile.getSkillIndex() == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(profile.getSkillIndex());
    }

    /**
     * Everything the candidate can credibly claim: declared skills plus
     * technologies evidenced by their experience and projects.
     */
    public Set<String> allNormalizedSkills() {
        Set<String> all = new LinkedHashSet<>(declaredSkills());

        if (profile != null && profile.getExperience() != null) {
            profile.getExperience().stream()
                    .filter(e -> e.getTechnologiesNormalized() != null)
                    .forEach(e -> all.addAll(e.getTechnologiesNormalized()));
        }
        for (Project project : projects == null ? List.<Project>of() : projects) {
            if (project.getTechStackNormalized() != null) {
                all.addAll(project.getTechStackNormalized());
            }
        }
        return all;
    }

    public String professionalTitle() {
        if (profile == null || profile.getProfessionalTitle() == null || profile.getProfessionalTitle().isBlank()) {
            return "developer";
        }
        return profile.getProfessionalTitle();
    }

    public boolean hasEducation() {
        return profile != null && profile.getEducation() != null && !profile.getEducation().isEmpty();
    }

    public int projectCount() {
        return projects == null ? 0 : projects.size();
    }
}
