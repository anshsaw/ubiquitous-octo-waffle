package com.portfoliopilot.model;

import com.portfoliopilot.model.embedded.SectionToggles;
import com.portfoliopilot.model.embedded.ThemeSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps the existing {@code portfolios} collection.
 *
 * <p><strong>This is a CONFIGURATION, not a copy of the profile.</strong> That
 * is the central rule of the product: adapting a portfolio for a job must never
 * mutate {@link Profile} or {@link Project}. So this document stores only a
 * template choice, section toggles and ORDERING. Content is resolved from the
 * profile and projects at render time, which means editing a project instantly
 * updates every portfolio that shows it.
 *
 * <p>A user may hold many portfolios (drafts, published, one per job), but only
 * one may be published under a given username at a time. That rule is enforced
 * by a partial unique index in {@code mongodb/indexes/indexes.js}, not by
 * application code - an application-level check would lose the concurrent
 * double-publish race.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "portfolios")
public class Portfolio {

    @MongoId(targetType = FieldType.OBJECT_ID)
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String userId;

    /**
     * Denormalised from {@code users.username}. Deliberate duplication: the
     * public route must resolve in ONE indexed read with no join. Must be
     * rewritten on every username change.
     */
    private String username;

    /** Internal label, e.g. "Java Developer Portfolio". Not shown publicly. */
    private String name;

    @Field(targetType = FieldType.OBJECT_ID)
    private String templateId;

    /** Denormalised template key so the renderer needs no second query. */
    private String templateKey;

    /** {@code null} = generic portfolio; non-null = adapted for that opportunity. */
    @Field(targetType = FieldType.OBJECT_ID)
    private String sourceJobAnalysisId;

    @Builder.Default
    private SectionToggles sections = SectionToggles.builder().build();

    /** Empty means use {@code PortfolioSection.defaultOrder()}. */
    @Builder.Default
    private List<String> sectionOrder = new ArrayList<>();

    /** ORDERING ONLY - normalizedName values pointing into {@code profiles.skills}. */
    @Builder.Default
    private List<String> orderedSkills = new ArrayList<>();

    /** ORDERING ONLY - references to {@code projects._id}, recommended first. */
    @Field(targetType = FieldType.OBJECT_ID)
    @Builder.Default
    private List<String> orderedProjects = new ArrayList<>();

    /** Job-specific headline. Overrides the profile title for THIS portfolio only. */
    private String headlineOverride;

    /** Job-specific About text. Same non-destructive principle. */
    private String summaryOverride;

    private ThemeSettings theme;

    /** Backs the public "Download CV" button. */
    @Field(targetType = FieldType.OBJECT_ID)
    private String resumeId;

    @Field("isPublished")
    private boolean published;

    private Instant publishedAt;

    @Builder.Default
    private Integer viewCount = 0;

    private boolean deleted;

    private Instant deletedAt;

    private Instant createdAt;

    private Instant updatedAt;
}
