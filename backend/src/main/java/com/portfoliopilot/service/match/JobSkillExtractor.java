package com.portfoliopilot.service.match;

import com.portfoliopilot.model.SkillDictionaryEntry;
import com.portfoliopilot.service.SkillDictionaryService;
import com.portfoliopilot.util.SkillNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns raw job-description text into structured requirements.
 *
 * <p>Strategy: <b>dictionary-driven n-gram matching</b>, not regex-per-skill and
 * not an LLM.
 *
 * <ol>
 *   <li>Normalise the whole JD with the SAME function used for stored skills, so
 *       both sides of the comparison speak one vocabulary.</li>
 *   <li>Build the set of 1..4-word n-grams present in the text.</li>
 *   <li>Test every dictionary key (canonical names AND aliases) for membership.
 *       That is a hash lookup per key rather than a scan per key.</li>
 * </ol>
 *
 * <p>Matching on n-grams rather than {@code text.contains(skill)} avoids the
 * classic false positives: {@code contains("r")} matching every word, or
 * {@code contains("go")} firing on "going", "algorithm" and "Google".
 *
 * <p>Required vs nice-to-have is decided by section headings, because that is
 * how job posts are actually written. A skill under "Nice to have" scores at a
 * fraction of a hard requirement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobSkillExtractor {

    /** Longest multi-word skill we index, e.g. "amazon web services". */
    private static final int MAX_NGRAM = 4;

    private static final Pattern BULLET = Pattern.compile("^\\s*[-*\u2022\u25cf\u25aa\\d.)\\]]+\\s*");

    private static final List<String> NICE_TO_HAVE_HEADINGS = List.of(
            "nice to have", "nice-to-have", "bonus", "plus", "preferred", "desirable",
            "good to have", "would be a plus", "advantageous", "optional");

    private static final List<String> REQUIRED_HEADINGS = List.of(
            "requirement", "required", "must have", "must-have", "qualification",
            "what you", "you will need", "we expect", "essential", "skills needed",
            "who you are", "about you");

    /** Headings after which lines describe the company, not the candidate. */
    private static final List<String> IGNORE_HEADINGS = List.of(
            "what we offer", "benefits", "perks", "about us", "about the company",
            "our team", "compensation", "why join", "equal opportunity");

    private final SkillDictionaryService skillDictionary;

    /**
     * @param required     canonical keys the job treats as mandatory
     * @param niceToHave   canonical keys the job treats as a bonus
     * @param requirements human-readable requirement lines, in document order
     */
    public record ExtractedJob(
            Set<String> required,
            Set<String> niceToHave,
            List<String> requirements
    ) {
        public Set<String> allDemanded() {
            Set<String> all = new LinkedHashSet<>(required);
            all.addAll(niceToHave);
            return all;
        }
    }

    public ExtractedJob extract(String jobTitle, String jobDescription) {
        String text = (jobTitle == null ? "" : jobTitle + "\n") + (jobDescription == null ? "" : jobDescription);

        Sections sections = splitSections(text);

        Set<String> required = matchSkills(sections.requiredText());
        Set<String> niceToHave = matchSkills(sections.niceToHaveText());

        // A skill named in both places is a hard requirement - the stronger
        // signal wins, otherwise a passing mention under "bonus" would
        // downgrade something the role genuinely depends on.
        niceToHave.removeAll(required);

        // Nothing was classified (a JD with no headings at all): treat every
        // skill found anywhere as required rather than returning nothing.
        if (required.isEmpty() && niceToHave.isEmpty()) {
            required = matchSkills(text);
        }

        List<String> requirementLines = extractRequirementLines(text);

        log.debug("Extracted {} required and {} nice-to-have skills from job '{}'",
                required.size(), niceToHave.size(), jobTitle);

        return new ExtractedJob(required, niceToHave, requirementLines);
    }

    // ------------------------------------------------------- skill matching

    /** Dictionary keys present in the given text, as canonical normalized names. */
    private Set<String> matchSkills(String text) {
        if (text == null || text.isBlank()) {
            return new LinkedHashSet<>();
        }

        Set<String> ngrams = buildNgrams(SkillNormalizer.normalizeSkill(text));
        Set<String> found = new LinkedHashSet<>();

        for (SkillDictionaryEntry entry : skillDictionary.allActiveEntries()) {
            if (matches(entry, ngrams)) {
                found.add(entry.getNormalizedName());
            }
        }
        return dropSubsumedSkills(found);
    }

    /**
     * Removes a matched skill when it is a strict sub-phrase of another matched
     * skill.
     *
     * <p>Found by testing: a posting asking only for "Spring Boot" also matched
     * the separate dictionary entry "Spring" (normalized {@code "spring"}),
     * because {@code spring} is a legitimate 1-gram of {@code spring boot}. The
     * candidate was then told they had a gap for a framework the job never
     * mentioned independently, which is both wrong and demoralising.
     *
     * <p>Comparison is on TOKEN sequences, not substrings. That distinction
     * matters: {@code "java"} must not be treated as part of
     * {@code "javascript"}, and a naive {@code String.contains} would do exactly
     * that.
     */
    private Set<String> dropSubsumedSkills(Set<String> matched) {
        if (matched.size() < 2) {
            return matched;
        }

        Set<String> result = new LinkedHashSet<>();
        for (String candidate : matched) {
            List<String> candidateTokens = List.of(candidate.split(" "));

            boolean subsumed = matched.stream()
                    .filter(other -> !other.equals(candidate))
                    .anyMatch(other -> containsSequence(List.of(other.split(" ")), candidateTokens));

            if (!subsumed) {
                result.add(candidate);
            }
        }
        return result;
    }

    /** True when {@code needle} appears as a contiguous run of tokens inside {@code haystack}. */
    private boolean containsSequence(List<String> haystack, List<String> needle) {
        if (needle.isEmpty() || needle.size() >= haystack.size()) {
            return false;
        }
        outer:
        for (int i = 0; i <= haystack.size() - needle.size(); i++) {
            for (int j = 0; j < needle.size(); j++) {
                if (!haystack.get(i + j).equals(needle.get(j))) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private boolean matches(SkillDictionaryEntry entry, Set<String> ngrams) {
        if (entry.getNormalizedName() != null && ngrams.contains(entry.getNormalizedName())) {
            return true;
        }
        if (entry.getAliases() == null) {
            return false;
        }
        for (String alias : entry.getAliases()) {
            if (ngrams.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    /** Every contiguous 1..{@value #MAX_NGRAM}-word sequence in the normalised text. */
    private Set<String> buildNgrams(String normalizedText) {
        String[] tokens = normalizedText.split("\\s+");
        Set<String> ngrams = new HashSet<>();

        for (int i = 0; i < tokens.length; i++) {
            StringBuilder gram = new StringBuilder();
            for (int n = 0; n < MAX_NGRAM && i + n < tokens.length; n++) {
                if (n > 0) {
                    gram.append(' ');
                }
                gram.append(tokens[i + n]);
                ngrams.add(gram.toString());
            }
        }
        return ngrams;
    }

    // ---------------------------------------------------- section splitting

    private record Sections(String requiredText, String niceToHaveText) {
    }

    /**
     * Walks the JD line by line, tracking which heading is currently in effect.
     * Lines under "What we offer" are dropped entirely - a benefits list is full
     * of nouns that would otherwise be mistaken for requirements.
     */
    private Sections splitSections(String text) {
        StringBuilder required = new StringBuilder();
        StringBuilder nice = new StringBuilder();

        Mode mode = Mode.REQUIRED;

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);

            Mode heading = detectHeading(lower);
            if (heading != null) {
                mode = heading;
                continue;
            }

            switch (mode) {
                case REQUIRED -> required.append(line).append('\n');
                case NICE_TO_HAVE -> nice.append(line).append('\n');
                case IGNORE -> { /* company-facing copy, deliberately skipped */ }
            }
        }
        return new Sections(required.toString(), nice.toString());
    }

    private enum Mode {REQUIRED, NICE_TO_HAVE, IGNORE}

    /** A heading is a short line that names a known section. */
    private Mode detectHeading(String lowerLine) {
        if (lowerLine.length() > 60) {
            return null;
        }
        if (containsAny(lowerLine, IGNORE_HEADINGS)) {
            return Mode.IGNORE;
        }
        if (containsAny(lowerLine, NICE_TO_HAVE_HEADINGS)) {
            return Mode.NICE_TO_HAVE;
        }
        if (containsAny(lowerLine, REQUIRED_HEADINGS)) {
            return Mode.REQUIRED;
        }
        return null;
    }

    private boolean containsAny(String haystack, List<String> needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------- requirement lines

    /**
     * Pulls out the bullet lines a human would recognise as requirements, so the
     * Requirements sub-score can be explained rather than merely asserted.
     */
    private List<String> extractRequirementLines(String text) {
        List<String> lines = new ArrayList<>();
        boolean inRequirements = false;

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);

            if (line.length() <= 60) {
                if (containsAny(lower, IGNORE_HEADINGS)) {
                    inRequirements = false;
                    continue;
                }
                if (containsAny(lower, REQUIRED_HEADINGS) || containsAny(lower, NICE_TO_HAVE_HEADINGS)) {
                    inRequirements = true;
                    continue;
                }
            }

            boolean isBullet = BULLET.matcher(line).find();
            if (inRequirements && isBullet) {
                String cleaned = BULLET.matcher(line).replaceFirst("").trim();
                if (cleaned.length() >= 8 && cleaned.length() <= 500) {
                    lines.add(cleaned);
                }
            }
            if (lines.size() >= 40) {
                break;
            }
        }
        return lines;
    }
}
