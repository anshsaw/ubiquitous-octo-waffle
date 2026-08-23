package com.portfoliopilot.service.jobs;

import java.util.regex.Pattern;

/**
 * Converts provider HTML into plain text at the ingestion boundary.
 *
 * <p>Remotive (and most job APIs) return the description as HTML. That HTML is
 * third-party content and must never reach a browser, so it is flattened here —
 * once, on the way in — rather than being sanitised at each of the several
 * places it is later rendered.
 *
 * <p>Flattening also improves matching: the skill extractor counts word
 * n-grams, and leaving markup in would let {@code <strong>} tags and CSS class
 * names pollute the token stream.
 */
public final class HtmlText {

    private static final Pattern SCRIPT_OR_STYLE =
            Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern BLOCK_END =
            Pattern.compile("(?i)</(p|div|li|ul|ol|h[1-6]|tr|br)\\s*>|<br\\s*/?>");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private HtmlText() {
    }

    /**
     * @param html    raw provider content, may be null
     * @param maxChars hard cap; the jobAnalyses validator limits descriptions to
     *                 30 000 characters, and nothing useful lives beyond that
     */
    public static String toPlainText(String html, int maxChars) {
        if (html == null || html.isBlank()) {
            return "";
        }

        String text = SCRIPT_OR_STYLE.matcher(html).replaceAll(" ");
        // Preserve document structure as newlines so bullet lists survive; the
        // skill extractor keys "Requirements" vs "Nice to have" off line breaks.
        text = BLOCK_END.matcher(text).replaceAll("\n");
        text = TAG.matcher(text).replaceAll("");
        text = decodeEntities(text);
        text = WHITESPACE.matcher(text).replaceAll(" ");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n");
        text = text.lines().map(String::strip).reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
        text = text.strip();

        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }

    /** The handful of entities that actually appear in job descriptions. */
    private static String decodeEntities(String text) {
        return text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&hellip;", "...")
                .replace("&ndash;", "-")
                .replace("&mdash;", "-")
                .replace("&rsquo;", "'")
                .replace("&lsquo;", "'");
    }
}
