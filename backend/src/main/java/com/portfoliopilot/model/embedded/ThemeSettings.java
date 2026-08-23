package com.portfoliopilot.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Visual overrides for a portfolio, layered on top of the template defaults.
 * Colours are validated as {@code ^#[0-9a-fA-F]{6}$} by the collection schema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeSettings {

    private String primaryColor;

    private String accentColor;

    private Boolean darkMode;

    /**
     * Surface colours behind the builder's "Custom" template.
     *
     * <p>These exist so a user can have their own palette without the platform
     * ever accepting uploaded CSS or HTML. A colour is data; a stylesheet is
     * code. The validator restricts each to {@code ^#[0-9a-fA-F]{6}$}, which is
     * the sanitisation boundary - without it these values reach a CSS custom
     * property and become an injection vector.
     */
    private String backgroundColor;

    private String surfaceColor;

    private String inkColor;
}
