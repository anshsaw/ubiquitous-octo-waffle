package com.portfoliopilot.dto.portfolio;

import com.portfoliopilot.model.PortfolioTemplate;
import com.portfoliopilot.model.embedded.ThemeSettings;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** A portfolio template for the /builder picker and the admin templates screen. */
public record TemplateResponse(
        String id,
        String name,
        String description,
        @Schema(description = "Stable key the renderer maps to a layout", example = "MODERN_DEV") String templateKey,
        String thumbnailUrl,
        String previewUrl,
        @Schema(description = "Sections this layout can render; the builder disables the rest")
        List<String> availableSections,
        List<String> defaultSections,
        String primaryColor,
        String accentColor,
        Boolean darkMode,
        Integer sortOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static TemplateResponse from(PortfolioTemplate template) {
        ThemeSettings theme = template.getTheme();
        return new TemplateResponse(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getTemplateKey(),
                template.getThumbnailUrl(),
                template.getPreviewUrl(),
                template.getAvailableSections() == null ? List.of() : template.getAvailableSections(),
                template.getDefaultSections() == null ? List.of() : template.getDefaultSections(),
                theme == null ? null : theme.getPrimaryColor(),
                theme == null ? null : theme.getAccentColor(),
                theme == null ? null : theme.getDarkMode(),
                template.getSortOrder(),
                template.isActive(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }
}
