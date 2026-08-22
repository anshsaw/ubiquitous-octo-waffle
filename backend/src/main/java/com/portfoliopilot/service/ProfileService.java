package com.portfoliopilot.service;

import com.portfoliopilot.dto.profile.CertificateRequest;
import com.portfoliopilot.dto.profile.EducationRequest;
import com.portfoliopilot.dto.profile.ExperienceRequest;
import com.portfoliopilot.dto.profile.ProfileResponse;
import com.portfoliopilot.dto.profile.SkillRequest;
import com.portfoliopilot.dto.profile.UpdateProfileRequest;
import com.portfoliopilot.exception.BusinessValidationException;
import com.portfoliopilot.exception.ResourceNotFoundException;
import com.portfoliopilot.model.Profile;
import com.portfoliopilot.model.User;
import com.portfoliopilot.model.embedded.Certificate;
import com.portfoliopilot.model.embedded.ContactInfo;
import com.portfoliopilot.model.embedded.Education;
import com.portfoliopilot.model.embedded.Experience;
import com.portfoliopilot.model.embedded.Skill;
import com.portfoliopilot.repository.ProfileRepository;
import com.portfoliopilot.repository.ProjectRepository;
import com.portfoliopilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns everything under {@code /api/profile}.
 *
 * <p>Two invariants are maintained here and nowhere else:
 * <ol>
 *   <li><b>{@code skillIndex} is always in sync</b> with
 *       {@code skills[].normalizedName}. The collection validator declares
 *       {@code uniqueItems} on it, so a duplicate skill is rejected by MongoDB
 *       itself - but only if this service rebuilds the array on every mutation.</li>
 *   <li><b>{@code profileHealth} is recomputed on every write</b>, so the
 *       dashboard reads a number instead of running an aggregation.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final SkillDictionaryService skillDictionary;

    // ------------------------------------------------------------------ read

    public ProfileResponse getProfile(String userId) {
        Profile profile = requireProfile(userId);
        User user = userRepository.findByIdAndDeletedFalse(userId).orElse(null);
        return ProfileResponse.from(profile, user);
    }

    /** Internal accessor for the analyzer, resume and portfolio services. */
    public Profile requireProfile(String userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Profile"));
    }

    // --------------------------------------------------------------- personal

    /**
     * @param partial {@code true} for PATCH (nulls ignored), {@code false} for
     *                PUT (nulls clear the field)
     */
    public ProfileResponse updatePersonal(String userId, UpdateProfileRequest request, boolean partial) {
        Profile profile = requireProfile(userId);
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User"));

        if (!partial || request.name() != null) {
            String name = trimToNull(request.name());
            if (name != null) {
                profile.setFullName(name);
                // Keep the account display name in step, otherwise the greeting
                // and the portfolio would disagree about the user's name.
                user.setName(name);
                user.setUpdatedAt(Instant.now());
                userRepository.save(user);
            } else if (!partial) {
                throw new BusinessValidationException("Name cannot be cleared",
                        Map.of("name", "Name is required"));
            }
        }

        String resolvedTitle = request.resolveTitle();
        if (!partial || resolvedTitle != null) {
            profile.setProfessionalTitle(trimToNull(resolvedTitle));
        }
        if (!partial || request.bio() != null) {
            profile.setBio(trimToNull(request.bio()));
        }
        if (!partial || request.location() != null) {
            profile.setLocation(trimToNull(request.location()));
        }
        if (!partial || request.avatarUrl() != null) {
            profile.setAvatarUrl(trimToNull(request.avatarUrl()));
        }
        if (request.contact() != null) {
            profile.setContact(request.contact().toEntity());
        } else if (!partial) {
            profile.setContact(new ContactInfo());
        }

        return persist(profile, user);
    }

    // ----------------------------------------------------------------- skills

    /**
     * Replaces the entire skill list.
     *
     * <p>De-duplicates by canonical key, keeping the HIGHEST proficiency: a user
     * who types both "React" (Advanced) and "ReactJS" (Beginner) means one skill,
     * and silently downgrading them would be wrong.
     */
    public ProfileResponse replaceSkills(String userId, List<SkillRequest> requests) {
        Profile profile = requireProfile(userId);
        List<Skill> resolved = new ArrayList<>();

        for (SkillRequest request : requests) {
            Skill skill = toSkill(request);
            if (skill.getNormalizedName().isEmpty()) {
                continue;
            }
            Skill existing = findSkill(resolved, skill.getNormalizedName());
            if (existing == null) {
                resolved.add(skill);
            } else if (skill.getProficiency().rank() > existing.getProficiency().rank()) {
                resolved.remove(existing);
                resolved.add(skill);
            }
        }

        profile.setSkills(resolved);
        return persist(profile, null);
    }

    /** Adds one skill, or upgrades it if the canonical key already exists. */
    public ProfileResponse addSkill(String userId, SkillRequest request) {
        Profile profile = requireProfile(userId);
        Skill skill = toSkill(request);

        if (skill.getNormalizedName().isEmpty()) {
            throw new BusinessValidationException("Skill name could not be interpreted",
                    Map.of("name", "Provide a recognisable skill name"));
        }

        Skill existing = findSkill(profile.getSkills(), skill.getNormalizedName());
        if (existing != null) {
            existing.setName(skill.getName());
            existing.setProficiency(skill.getProficiency());
            existing.setYearsOfExperience(skill.getYearsOfExperience());
            existing.setCategory(skill.getCategory());
            log.debug("Skill '{}' already present for user {} - updated in place",
                    skill.getNormalizedName(), userId);
        } else {
            profile.getSkills().add(skill);
        }

        return persist(profile, null);
    }

    /** Removes a skill by canonical key. Accepts any spelling - it is normalised first. */
    public ProfileResponse removeSkill(String userId, String skillName) {
        Profile profile = requireProfile(userId);
        String normalized = skillDictionary.resolve(skillName).normalizedName();

        boolean removed = profile.getSkills()
                .removeIf(s -> Objects.equals(s.getNormalizedName(), normalized));

        if (!removed) {
            throw ResourceNotFoundException.of("Skill '" + skillName + "'");
        }
        return persist(profile, null);
    }

    private Skill toSkill(SkillRequest request) {
        SkillDictionaryService.ResolvedSkill resolved = skillDictionary.resolve(request.name());
        return Skill.builder()
                // Canonical display name when the dictionary knows it, so
                // "reactjs" is stored and rendered as "React".
                .name(resolved.known() ? resolved.displayName() : request.name().trim())
                .normalizedName(resolved.normalizedName())
                .proficiency(request.resolveProficiency())
                .yearsOfExperience(request.yearsOfExperience())
                .category(resolved.category())
                .build();
    }

    private Skill findSkill(List<Skill> skills, String normalizedName) {
        return skills.stream()
                .filter(s -> Objects.equals(s.getNormalizedName(), normalizedName))
                .findFirst()
                .orElse(null);
    }

    // -------------------------------------------------------------- education

    public ProfileResponse addEducation(String userId, EducationRequest request) {
        validateYears(request);
        Profile profile = requireProfile(userId);

        profile.getEducation().add(Education.builder()
                .id(new ObjectId().toHexString())
                .degree(request.degree().trim())
                .institution(request.institution().trim())
                .fieldOfStudy(trimToNull(request.fieldOfStudy()))
                .startYear(request.startYear())
                .endYear(request.endYear())
                .grade(trimToNull(request.grade()))
                .description(trimToNull(request.description()))
                .build());

        return persist(profile, null);
    }

    public ProfileResponse updateEducation(String userId, String educationId, EducationRequest request) {
        validateYears(request);
        Profile profile = requireProfile(userId);

        Education entry = profile.getEducation().stream()
                .filter(e -> Objects.equals(e.getId(), educationId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Education entry"));

        entry.setDegree(request.degree().trim());
        entry.setInstitution(request.institution().trim());
        entry.setFieldOfStudy(trimToNull(request.fieldOfStudy()));
        entry.setStartYear(request.startYear());
        entry.setEndYear(request.endYear());
        entry.setGrade(trimToNull(request.grade()));
        entry.setDescription(trimToNull(request.description()));

        return persist(profile, null);
    }

    public ProfileResponse deleteEducation(String userId, String educationId) {
        Profile profile = requireProfile(userId);
        if (!profile.getEducation().removeIf(e -> Objects.equals(e.getId(), educationId))) {
            throw ResourceNotFoundException.of("Education entry");
        }
        return persist(profile, null);
    }

    /** Bean Validation cannot compare two sibling fields, so the range check lives here. */
    private void validateYears(EducationRequest request) {
        if (request.startYear() != null && request.endYear() != null
                && request.endYear() < request.startYear()) {
            throw new BusinessValidationException("End year cannot be before start year",
                    Map.of("endYear", "Must be greater than or equal to startYear"));
        }
    }

    // ------------------------------------------------------------- experience

    public ProfileResponse addExperience(String userId, ExperienceRequest request) {
        validateDates(request);
        Profile profile = requireProfile(userId);

        profile.getExperience().add(Experience.builder()
                .id(new ObjectId().toHexString())
                .company(request.company().trim())
                .role(request.role().trim())
                .location(trimToNull(request.location()))
                .employmentType(request.employmentType())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .description(trimToNull(request.description()))
                .responsibilities(nullSafe(request.responsibilities()))
                .technologies(nullSafe(request.technologies()))
                .technologiesNormalized(normalizeAll(request.technologies()))
                .build());

        return persist(profile, null);
    }

    public ProfileResponse updateExperience(String userId, String experienceId, ExperienceRequest request) {
        validateDates(request);
        Profile profile = requireProfile(userId);

        Experience entry = profile.getExperience().stream()
                .filter(e -> Objects.equals(e.getId(), experienceId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Experience entry"));

        entry.setCompany(request.company().trim());
        entry.setRole(request.role().trim());
        entry.setLocation(trimToNull(request.location()));
        entry.setEmploymentType(request.employmentType());
        entry.setStartDate(request.startDate());
        entry.setEndDate(request.endDate());
        entry.setDescription(trimToNull(request.description()));
        entry.setResponsibilities(nullSafe(request.responsibilities()));
        entry.setTechnologies(nullSafe(request.technologies()));
        entry.setTechnologiesNormalized(normalizeAll(request.technologies()));

        return persist(profile, null);
    }

    public ProfileResponse deleteExperience(String userId, String experienceId) {
        Profile profile = requireProfile(userId);
        if (!profile.getExperience().removeIf(e -> Objects.equals(e.getId(), experienceId))) {
            throw ResourceNotFoundException.of("Experience entry");
        }
        return persist(profile, null);
    }

    private void validateDates(ExperienceRequest request) {
        if (request.startDate() != null && request.endDate() != null
                && request.endDate().isBefore(request.startDate())) {
            throw new BusinessValidationException("End date cannot be before start date",
                    Map.of("endDate", "Must be on or after startDate"));
        }
    }

    // ----------------------------------------------------------- certificates

    public ProfileResponse addCertificate(String userId, CertificateRequest request) {
        Profile profile = requireProfile(userId);

        profile.getCertificates().add(Certificate.builder()
                .id(new ObjectId().toHexString())
                .name(request.name().trim())
                .issuingOrganization(request.issuingOrganization().trim())
                .issueDate(request.issueDate())
                .expiryDate(request.expiryDate())
                .credentialId(trimToNull(request.credentialId()))
                .credentialUrl(trimToNull(request.credentialUrl()))
                .build());

        return persist(profile, null);
    }

    public ProfileResponse deleteCertificate(String userId, String certificateId) {
        Profile profile = requireProfile(userId);
        if (!profile.getCertificates().removeIf(c -> Objects.equals(c.getId(), certificateId))) {
            throw ResourceNotFoundException.of("Certificate");
        }
        return persist(profile, null);
    }

    // -------------------------------------------------------------- internals

    /**
     * The single write path. Rebuilding {@code skillIndex} and
     * {@code profileHealth} here - rather than at each call site - is what
     * guarantees they can never drift out of sync.
     */
    private ProfileResponse persist(Profile profile, User preloadedUser) {
        profile.setSkillIndex(buildSkillIndex(profile.getSkills()));

        long projectCount = projectRepository.countByUserIdAndDeletedFalse(profile.getUserId());
        profile.setProfileHealth(computeProfileHealth(profile, (int) projectCount));
        profile.setUpdatedAt(Instant.now());

        Profile saved = profileRepository.save(profile);

        User user = preloadedUser != null
                ? preloadedUser
                : userRepository.findByIdAndDeletedFalse(profile.getUserId()).orElse(null);

        return ProfileResponse.from(saved, user);
    }

    /** Derived array backing the validator's duplicate-skill rule and the multikey index. */
    private List<String> buildSkillIndex(List<Skill> skills) {
        return new ArrayList<>(new LinkedHashSet<>(
                nullSafe(skills).stream()
                        .map(Skill::getNormalizedName)
                        .filter(n -> n != null && !n.isEmpty())
                        .toList()));
    }

    private List<String> normalizeAll(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(
                nullSafe(values).stream()
                        .map(v -> skillDictionary.resolve(v).normalizedName())
                        .filter(v -> !v.isEmpty())
                        .toList()));
    }

    /**
     * 0-100 completeness score. Weights mirror what the UI nudges users to
     * complete, and match {@code mongodb/seed/seed.js} so seeded and runtime
     * profiles are scored identically.
     *
     * <p>Static so {@code AuthService} can seed a value at registration without
     * a circular dependency.
     */
    public static int computeProfileHealth(Profile profile, int projectCount) {
        if (profile == null) {
            return 10; // a brand-new account has only its name
        }
        int score = 0;
        if (isPresent(profile.getFullName())) score += 10;
        if (isPresent(profile.getProfessionalTitle())) score += 10;
        if (profile.getBio() != null && profile.getBio().length() > 60) score += 15;
        if (isPresent(profile.getLocation())) score += 5;
        if (isPresent(profile.getAvatarUrl())) score += 5;
        if (nullSafe(profile.getSkills()).size() >= 5) score += 20;
        if (!nullSafe(profile.getEducation()).isEmpty()) score += 15;
        if (projectCount >= 2) score += 15;

        ContactInfo contact = profile.getContact();
        if (contact != null && (isPresent(contact.getGithub()) || isPresent(contact.getLinkedin()))) {
            score += 5;
        }
        return Math.min(100, score);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
