package com.portfoliopilot.service.jobs;

import com.portfoliopilot.service.SkillDictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns a {@link CandidateProfile} into 5–10 role phrases to search for.
 *
 * <p>Searching the user's exact job title alone is the obvious approach and a
 * poor one: "Full-Stack Developer" misses "Java Backend Developer" and
 * "Spring Boot Engineer", which is precisely where a Java/Spring portfolio
 * should be looking. So queries are generated from the candidate's strongest
 * TECHNOLOGIES combined with the role families those technologies imply, then
 * banded by experience level.
 *
 * <p>Kept to ~8 queries. Each one costs a network round trip, and beyond that
 * the variations stop being meaningfully different.
 */
@Component
@RequiredArgsConstructor
public class JobQueryGenerator {

    private static final int MAX_QUERIES = 8;

    /**
     * Which role family a technology implies. Only strong, unambiguous signals
     * are listed — a weak association produces noisy searches.
     */
    private static final Map<String, String> ROLE_BY_SKILL = Map.ofEntries(
            Map.entry("java", "Java Developer"),
            Map.entry("spring boot", "Spring Boot Developer"),
            Map.entry("spring", "Java Backend Developer"),
            Map.entry("hibernate", "Java Backend Developer"),
            Map.entry("react", "React Developer"),
            Map.entry("nextjs", "React Developer"),
            Map.entry("typescript", "Frontend Developer"),
            Map.entry("javascript", "Frontend Developer"),
            Map.entry("nodejs", "Node.js Developer"),
            Map.entry("expressjs", "Backend Developer"),
            Map.entry("python", "Python Developer"),
            Map.entry("mongodb", "Backend Developer"),
            Map.entry("postgresql", "Backend Developer"),
            Map.entry("mysql", "Backend Developer"),
            Map.entry("docker", "DevOps Engineer"),
            Map.entry("kubernetes", "Platform Engineer"),
            Map.entry("aws", "Cloud Engineer"),
            Map.entry("ci cd", "DevOps Engineer")
    );

    /** Prefix applied to broaden or narrow by seniority. */
    private static final Map<String, String> LEVEL_PREFIX = Map.of(
            "INTERNSHIP", "Intern",
            "ENTRY", "Junior",
            "JUNIOR", "Junior",
            "MID", "",
            "SENIOR", "Senior"
    );

    private final SkillDictionaryService skillDictionary;

    /**
     * @param candidate     built from the published portfolio
     * @param overrideRole  user-typed role that replaces auto-detection, or null
     */
    public List<String> generate(CandidateProfile candidate, String overrideRole, String experienceLevel) {
        // An explicit role is an instruction, not a hint: honour it first and
        // only pad with derived variations.
        Set<String> queries = new LinkedHashSet<>();

        if (overrideRole != null && !overrideRole.isBlank()) {
            queries.add(overrideRole.trim());
        }

        String level = experienceLevel == null || experienceLevel.isBlank()
                ? candidate.experienceLevel()
                : experienceLevel;
        String prefix = LEVEL_PREFIX.getOrDefault(level, "");

        if (candidate.title() != null && !candidate.title().isBlank()) {
            queries.add(candidate.title().trim());
        }

        // Role families implied by the top skills, most confident first.
        List<String> topSkills = candidate.normalizedSkills().stream().limit(8).toList();
        for (String skill : topSkills) {
            String role = ROLE_BY_SKILL.get(skill);
            if (role != null) {
                queries.add(role);
            }
        }

        // Pair the two strongest technologies into a combined role, which is how
        // these jobs are usually titled ("Java Full Stack Developer").
        if (topSkills.size() >= 2) {
            String a = skillDictionary.displayNameFor(topSkills.get(0));
            queries.add(a + " Developer");
            if (ROLE_BY_SKILL.containsKey(topSkills.get(0)) && hasFrontendAndBackend(topSkills)) {
                queries.add(a + " Full Stack Developer");
            }
        }

        // Seniority-banded variant of the single best query, so entry-level
        // candidates are not only shown senior postings.
        if (!prefix.isBlank() && !queries.isEmpty()) {
            String base = queries.iterator().next();
            queries.add(prefix + " " + base);
        }

        queries.add("Software Engineer");

        return queries.stream()
                .map(this::tidy)
                .filter(q -> q.length() > 2)
                .distinct()
                .limit(MAX_QUERIES)
                .toList();
    }

    /** True when the stack spans both sides, which justifies a "Full Stack" query. */
    private boolean hasFrontendAndBackend(List<String> skills) {
        Set<String> frontend = Set.of("react", "nextjs", "javascript", "typescript", "css", "html");
        Set<String> backend = Set.of("java", "spring boot", "nodejs", "python", "expressjs", "rest api");
        boolean f = skills.stream().anyMatch(frontend::contains);
        boolean b = skills.stream().anyMatch(backend::contains);
        return f && b;
    }

    private String tidy(String query) {
        return query.replaceAll("\\s+", " ").trim();
    }

    /** Title-cases a canonical key for display, e.g. {@code spring boot -> Spring Boot}. */
    public static String titleCase(String value) {
        String[] parts = value.split(" ");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            out.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
        }
        return String.join(" ", out);
    }
}
