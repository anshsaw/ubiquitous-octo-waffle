package com.portfoliopilot.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Admin create/update for a portfolio template. */
public record TemplateRequest(

        @NotBlank(message = "Template name is required")
        @Size(max = 120)
        @Schema(example = "Modern Developer")
        String name,

        @Size(max = 1000)
        String description,

        /*
         * Immutable once portfolios reference it - renaming the key would break
         * every portfolio using this layout, so the service rejects a change.
         */
        @NotBlank(message = "Template key is required")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,59}$",
                message = "Template key must be UPPER_SNAKE_CASE, e.g. MODERN_DEV")
        @Schema(example = "MODERN_DEV")
        String templateKey,

        @Size(max = 2048)
        String thumbnailUrl,

        @Size(max = 2048)
        String previewUrl,

        @NotEmpty(message = "At least one available section is required")
        @Schema(example = "[\"about\", \"skills\", \"projects\", \"contact\"]")
        List<String> availableSections,

        List<String> defaultSections,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Must be a 6-digit hex colour")
        String primaryColor,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Must be a 6-digit hex colour")
        String accentColor,

        Boolean darkMode,

        Integer sortOrder,

        Boolean active
) {
}
