package com.portfoliopilot.model.enums;

/**
 * Resume layouts. Mirrors {@code resumes.template}.
 *
 * <p>All three are single-column and parser-safe: no tables, no multi-column
 * text flow, no images. That is what "ATS-friendly" actually means.
 *
 * <p>Deliberately an enum and NOT a reference to {@code portfolioTemplates} -
 * the resume and portfolio render pipelines are unrelated.
 */
public enum ResumeTemplate {
    ATS_CLASSIC,
    ATS_COMPACT,
    ATS_MODERN
}
