package com.portfoliopilot.service.jobs;

import com.portfoliopilot.service.SkillDictionaryService;
import com.portfoliopilot.service.match.JobSkillExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Scores and ranks listings against the candidate's published portfolio.
 *
 * <pre>
 *   Portfolio Match =
 *       skills      40%    demanded skills the portfolio evidences
 *     + role        20%    title overlap with the candidate's role family
 *     + experience  15%    seniority band agreement
 *     + projects    10%    strongest project's technology overlap
 *     + education    5%    degree present when the posting asks for one
 *     + location    10%    work-type / location preference agreement
 * </pre>
 *
 * <p>Every component is derived from something a reader could check, and the
 * per-component breakdown is returned so the UI can show its working. A listed
 * skill scores less than a demonstrated one — that gap is the entire point of
 * the product.
 *
 * <p>Reuses {@link JobSkillExtractor}, the same component that powers the paste
 * a job description flow, so a listing found through search and the same
 * posting pasted by hand extract identically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpportunityRankingService {

    private static final double W_SKILLS = 0.40;
    private static final double W_ROLE = 0.20;
    private static final double W_EXPERIENCE = 0.15;
    private static final double W_PROJECTS = 0.10;
    private static final double W_EDUCATION = 0.05;
    private static final double W_LOCATION = 0.10;

    /** A listed-but-unevidenced skill earns this fraction of a demonstrated one. */
    private static final double PARTIAL_CREDIT = 0.5;

    private static final List<String> LEVELS =
            List.of("INTERNSHIP", "ENTRY", "JUNIOR", "MID", "SENIOR");

    private final JobSkillExtractor extractor;
    private final SkillDictionaryService skillDictionary;

    public List<ScoredOpportunity> rank(List<JobListing> listings,
                                        CandidateProfile candidate,
                                        JobQuery query,
                                        int minimumMatch) {

        List<ScoredOpportunity> scored = new ArrayList<>();
        int irrelevant = 0;

        for (JobListing listing : listings) {
            ScoredOpportunity opportunity = score(listing, candidate, query);

            if (!isRelevant(opportunity)) {
                irrelevant++;
                continue;
            }
            if (opportunity.matchScore() >= minimumMatch) {
                scored.add(opportunity);
            }
        }

        if (irrelevant > 0) {
            log.debug("Dropped {} listing(s) with no technical overlap", irrelevant);
        }

        scored.sort(Comparator.comparingInt(ScoredOpportunity::matchScore).reversed());
        return scored.stream().limit(query.limit()).toList();
    }

    /**
     * Hard relevance gate, applied BEFORE the score is considered.
     *
     * <p>Found by testing against live Remotive data: its {@code search}
     * parameter is loose, so a query for "Java Backend Developer" returned
     * "Freelance Writer" and "Remote Office Assistant". Those scored in the
     * fifties purely on the always-on components — education and location
     * contribute 15 points to literally any posting — which made the whole
     * number meaningless.
     *
     * <p>A posting is only an opportunity if the portfolio shares at least one
     * real technical requirement with it. No overlap means it is not a weak
     * match, it is a different job, and it is dropped rather than ranked low.
     */
    private boolean isRelevant(ScoredOpportunity opportunity) {
        int shared = opportunity.matchedSkills().size() + opportunity.partialSkills().size();

        // TWO shared technologies, not one. A single hit is usually incidental:
        // job descriptions mention "Git" or "communication" in boilerplate, and
        // one such coincidence was enough to rank an office-assistant role
        // above real engineering posts.
        if (shared >= 2) {
            return true;
        }

        // One hit still counts when the title itself is clearly the same family,
        // which protects a genuine posting whose stack the dictionary does not
        // know yet.
        return shared == 1 && opportunity.breakdown().role() >= 50;
    }

    public ScoredOpportunity score(JobListing listing, CandidateProfile candidate, JobQuery query) {
        JobSkillExtractor.ExtractedJob job = extractor.extract(listing.title(), listing.description());

        Set<String> demanded = new LinkedHashSet<>(job.allDemanded());
        // Provider tags are a useful extra signal, but only when the dictionary
        // recognises them - raw tags are noisy free text.
        for (String tag : listing.tags() == null ? List.<String>of() : listing.tags()) {
            var resolved = skillDictionary.resolve(tag);
            if (resolved.known()) {
                demanded.add(resolved.normalizedName());
            }
        }

        Set<String> listed = new LinkedHashSet<>(candidate.normalizedSkills());
        Set<String> evidenced = candidate.evidence();

        List<String> matched = new ArrayList<>();
        List<String> partial = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        double earned = 0;
        for (String skill : demanded) {
            if (evidenced.contains(skill)) {
                matched.add(skill);
                earned += 1.0;
            } else if (listed.contains(skill)) {
                partial.add(skill);
                earned += PARTIAL_CREDIT;
            } else {
                missing.add(skill);
            }
        }

        /*
         * No recognisable technical requirement means this is not a technical
         * role, so it scores zero rather than a neutral 50.
         *
         * The neutral value was the second half of the "Freelance Writer at
         * 57%" bug: a non-technical posting demanded nothing, collected 50 free
         * points on the largest weight, and floated to the top of the list.
         */
        int skillsScore = demanded.isEmpty() ? 0 : clamp(earned / demanded.size() * 100);
        int roleScore = roleScore(listing.title(), candidate, query);
        int experienceScore = experienceScore(listing, candidate, query);
        int projectScore = projectScore(demanded, candidate);
        int educationScore = candidate.education().isEmpty() ? 40 : 100;
        int locationScore = locationScore(listing, query);

        int total = clamp(
                skillsScore * W_SKILLS
                        + roleScore * W_ROLE
                        + experienceScore * W_EXPERIENCE
                        + projectScore * W_PROJECTS
                        + educationScore * W_EDUCATION
                        + locationScore * W_LOCATION);

        return new ScoredOpportunity(
                listing,
                total,
                display(matched),
                display(partial),
                display(missing),
                new ScoredOpportunity.Breakdown(
                        skillsScore, roleScore, experienceScore, projectScore, educationScore, locationScore),
                explain(matched, partial, missing, candidate));
    }

    /** Word overlap between the posting title and the candidate's role phrases. */
    private int roleScore(String title, CandidateProfile candidate, JobQuery query) {
        if (title == null || title.isBlank()) {
            return 40;
        }
        Set<String> titleWords = words(title);

        // Stop words would otherwise make every title look like a partial match.
        titleWords.removeAll(Set.of("developer", "engineer", "senior", "junior", "lead", "staff",
                "remote", "the", "and", "for", "with", "of", "a", "an", "i", "ii", "iii"));

        int best = 0;
        List<String> candidates = new ArrayList<>(query.roleQueries());
        if (candidate.title() != null) {
            candidates.add(candidate.title());
        }

        for (String phrase : candidates) {
            Set<String> phraseWords = words(phrase);
            phraseWords.removeAll(Set.of("developer", "engineer", "senior", "junior", "full", "stack"));
            if (phraseWords.isEmpty()) {
                continue;
            }
            long hits = phraseWords.stream().filter(titleWords::contains).count();
            best = Math.max(best, clamp((double) hits / phraseWords.size() * 100));
        }

        // A generic title ("Software Engineer") is a weak but genuine match.
        return Math.max(best, 30);
    }

    /**
     * Seniority agreement. Being one band below the posting is common and only
     * mildly penalised; being far above or below is not a good use of anyone's
     * time.
     */
    private int experienceScore(JobListing listing, CandidateProfile candidate, JobQuery query) {
        String wanted = query.experienceLevel() != null && !query.experienceLevel().isBlank()
                ? query.experienceLevel()
                : inferLevelFromTitle(listing.title());

        if (wanted == null) {
            return 70;
        }
        int wantedIndex = LEVELS.indexOf(wanted);
        int haveIndex = LEVELS.indexOf(candidate.experienceLevel());
        if (wantedIndex < 0 || haveIndex < 0) {
            return 70;
        }
        int distance = Math.abs(wantedIndex - haveIndex);
        return switch (distance) {
            case 0 -> 100;
            case 1 -> 75;
            case 2 -> 45;
            default -> 20;
        };
    }

    private String inferLevelFromTitle(String title) {
        if (title == null) return null;
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("intern")) return "INTERNSHIP";
        if (lower.contains("junior") || lower.contains("entry") || lower.contains("graduate")) return "JUNIOR";
        if (lower.contains("senior") || lower.contains("sr.") || lower.contains("lead")
                || lower.contains("principal") || lower.contains("staff")) return "SENIOR";
        return null;
    }

    /** The single most relevant project's coverage of what the posting demands. */
    private int projectScore(Set<String> demanded, CandidateProfile candidate) {
        if (demanded.isEmpty() || candidate.projects().isEmpty()) {
            return 0;
        }
        int best = 0;
        for (CandidateProfile.ProjectSummary project : candidate.projects()) {
            long overlap = project.technologies().stream().filter(demanded::contains).count();
            best = Math.max(best, clamp((double) overlap / demanded.size() * 100));
        }
        return best;
    }

    private int locationScore(JobListing listing, JobQuery query) {
        String wanted = query.workType();
        if (wanted == null || wanted.isBlank() || "ANY".equalsIgnoreCase(wanted)) {
            return 100;
        }
        if (listing.workType() == null) {
            return 60; // unknown, not wrong
        }
        return wanted.equalsIgnoreCase(listing.workType()) ? 100 : 30;
    }

    /**
     * One factual sentence. It describes what the portfolio shows and what it
     * does not — it never claims the user lacks a skill, only that the portfolio
     * does not demonstrate it.
     */
    private String explain(List<String> matched, List<String> partial, List<String> missing,
                           CandidateProfile candidate) {
        StringBuilder text = new StringBuilder();

        if (matched.isEmpty()) {
            text.append("Your portfolio does not yet demonstrate the core technologies in this posting.");
        } else {
            List<String> top = display(matched).stream().limit(4).toList();
            text.append("Your portfolio demonstrates ").append(String.join(", ", top));
            text.append(matched.size() > 4 ? " and " + (matched.size() - 4) + " more requirement(s)." : ".");
        }

        if (!partial.isEmpty()) {
            text.append(" ").append(String.join(", ", display(partial).stream().limit(3).toList()))
                .append(partial.size() == 1 ? " is listed" : " are listed")
                .append(" in your skills but not shown in a project or role.");
        }
        if (!missing.isEmpty()) {
            text.append(" Not demonstrated in your current portfolio: ")
                .append(String.join(", ", display(missing).stream().limit(4).toList())).append(".");
        }
        return text.toString();
    }

    private List<String> display(List<String> normalized) {
        return normalized.stream().map(skillDictionary::displayNameFor).distinct().toList();
    }

    private Set<String> words(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9+#]+")) {
            if (word.length() > 1) {
                out.add(word);
            }
        }
        return out;
    }

    private int clamp(double value) {
        return (int) Math.max(0, Math.min(100, Math.round(value)));
    }
}
