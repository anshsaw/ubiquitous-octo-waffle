package com.portfoliopilot.model;

import com.portfoliopilot.model.embedded.ThemeSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps the existing {@code portfolioTemplates} collection - the global,
 * admin-managed layout catalogue.
 *
 * <p>Shared by every user, so portfolios REFERENCE a template rather than
 * embedding it; otherwise a template tweak would require rewriting every
 * portfolio document.
 *
 * <p>Retiring a template sets {@code active = false}. It is never hard-deleted
 * while a portfolio still references it - existing portfolios must keep
 * rendering.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "portfolioTemplates")
public class PortfolioTemplate {

    @MongoId(targetType = FieldType.OBJECT_ID)
    private String id;

    private String name;

    private String description;

    /**
     * STABLE machine key mapping to a renderer, e.g. {@code MODERN_DEV}.
     * Unique and immutable once published - renaming it breaks every portfolio
     * that references it.
     */
    private String templateKey;

    private String thumbnailUrl;

    private String previewUrl;

    /** Sections this layout can render. The builder must disable absent toggles. */
    @Builder.Default
    private List<String> availableSections = new ArrayList<>();

    /** Pre-checked sections when the template is first selected. */
    @Builder.Default
    private List<String> defaultSections = new ArrayList<>();

    private ThemeSettings theme;

    private Integer sortOrder;

    private boolean active;

    private Instant createdAt;

    private Instant updatedAt;
}
