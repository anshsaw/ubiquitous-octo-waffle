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
}
