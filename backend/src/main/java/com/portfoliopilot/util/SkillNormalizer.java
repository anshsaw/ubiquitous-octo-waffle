package com.portfoliopilot.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Text normalisation, ported VERBATIM from {@code mongodb/lib/normalize.js}.
 *
 * <p><strong>These two implementations must stay byte-for-byte equivalent.</strong>
 * The seed data, the aggregation pipelines and this backend all compare skills
 * on {@code normalizedName}. If the Java and JavaScript algorithms diverge by
 * even one character, seeded rows and runtime rows stop joining and every skill
 * gap chart silently becomes wrong.
 *
 * <p>Worked examples:
 * <pre>
 *   "React.js"     -> "react js"      -> dictionary alias -> "react"
 *   "Spring-Boot"  -> "spring boot"   -> direct hit       -> "spring boot"
 *   "Mongo DB"     -> "mongo db"      -> alias            -> "mongodb"
 *   "C++"          -> "c++"           (the '+' is preserved on purpose)
 *   "CI/CD"        -> "ci cd"
 * </pre>
 */
public final class SkillNormalizer {

    private SkillNormalizer() {
    }

    /**
     * Lowercase, fold accents, convert separator punctuation to spaces, drop
     * remaining punctuation, collapse whitespace.
     *
     * <p>{@code +} and {@code #} survive so "c++" and "c#" remain distinguishable
     * from "c".
     */
    public static String normalizeSkill(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFKD)
                .replaceAll("[\\u0300-\\u036f]", "")   // strip combining accents
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[._/\\\\|]+", " ")        // "react.js" / "ci/cd" -> spaced tokens
                .replaceAll("[^a-z0-9+#\\- ]+", "")    // keep c++, c#, objective-c
                .replaceAll("\\s*-\\s*", " ")          // "spring-boot" -> "spring boot"
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Normalises a public handle to satisfy the
     * {@code ^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$} pattern enforced by the users
     * and portfolios collection validators.
     */
    public static String normalizeUsername(String raw) {
        if (raw == null) {
            return "";
        }
        String slug = Normalizer.normalize(raw, Normalizer.Form.NFKD)
                .replaceAll("[\\u0300-\\u036f]", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");

        return slug.length() > 30 ? slug.substring(0, 30).replaceAll("-+$", "") : slug;
    }

    /**
     * Normalises a job title for the "most analysed roles" aggregation.
     * <pre>"Senior Java Developer (Remote)" -> "senior java developer"</pre>
     */
    public static String normalizeJobTitle(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("\\(.*?\\)", " ")
                .replaceAll("[^a-z0-9+#\\- ]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** True when the handle satisfies the collection validator's pattern. */
    public static boolean isValidUsername(String username) {
        return username != null && username.matches("^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$");
    }
}
