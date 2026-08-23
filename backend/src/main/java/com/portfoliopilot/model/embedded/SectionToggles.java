package com.portfoliopilot.model.embedded;

import com.portfoliopilot.model.enums.PortfolioSection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code portfolios.sections} - a fixed-key toggle map matching the /builder
 * checkboxes exactly.
 *
 * <p>A concrete class rather than a {@code Map<String, Boolean>} on purpose: the
 * collection validator declares {@code additionalProperties: false} on this
 * object, so an unknown key would fail the write. Typed fields make that
 * impossible to get wrong.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionToggles {

    @Builder.Default
    private boolean about = true;

    @Builder.Default
    private boolean skills = true;

    @Builder.Default
    private boolean projects = true;

    @Builder.Default
    private boolean education = true;

    @Builder.Default
    private boolean experience = true;

    @Builder.Default
    private boolean certificates = true;

    @Builder.Default
    private boolean contact = true;

    public boolean isEnabled(PortfolioSection section) {
        return switch (section) {
            case ABOUT -> about;
            case SKILLS -> skills;
            case PROJECTS -> projects;
            case EDUCATION -> education;
            case EXPERIENCE -> experience;
            case CERTIFICATES -> certificates;
            case CONTACT -> contact;
        };
    }

    public void set(PortfolioSection section, boolean value) {
        switch (section) {
            case ABOUT -> about = value;
            case SKILLS -> skills = value;
            case PROJECTS -> projects = value;
            case EDUCATION -> education = value;
            case EXPERIENCE -> experience = value;
            case CERTIFICATES -> certificates = value;
            case CONTACT -> contact = value;
        }
    }

    /** Wire form for the API: {@code {"about": true, "skills": true, ...}}. */
    public Map<String, Boolean> asMap() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (PortfolioSection section : PortfolioSection.defaultOrder()) {
            map.put(section.key(), isEnabled(section));
        }
        return map;
    }

    /** Builds from the wire form, ignoring unknown keys rather than failing. */
    public static SectionToggles fromMap(Map<String, Boolean> map) {
        SectionToggles toggles = SectionToggles.builder().build();
        if (map == null) {
            return toggles;
        }
        for (PortfolioSection section : PortfolioSection.values()) {
            Boolean value = map.get(section.key());
            if (value != null) {
                toggles.set(section, value);
            }
        }
        return toggles;
    }
}
