package com.portfoliopilot.model.enums;

import java.util.List;
import java.util.Locale;

/**
 * Toggleable portfolio sections. Mirrors the fixed key set of
 * {@code portfolios.sections} and {@code portfolioTemplates.availableSections}.
 *
 * <p>The database stores these as lowercase strings ({@code "about"}), so the
 * JSON/BSON value is produced by {@link #key()} rather than {@link #name()}.
 */
public enum PortfolioSection {
    ABOUT,
    SKILLS,
    PROJECTS,
    EDUCATION,
    EXPERIENCE,
    CERTIFICATES,
    CONTACT;

    /** Lowercase wire/BSON form, e.g. {@code "about"}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Default render order when a portfolio defines no custom {@code sectionOrder}. */
    public static List<PortfolioSection> defaultOrder() {
        return List.of(ABOUT, SKILLS, PROJECTS, EDUCATION, EXPERIENCE, CERTIFICATES, CONTACT);
    }

    public static PortfolioSection fromKey(String key) {
        return valueOf(key.toUpperCase(Locale.ROOT));
    }
}
