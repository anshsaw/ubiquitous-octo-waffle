package com.portfoliopilot.model;

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
 * Maps the existing {@code projects} collection.
 *
 * <p>A separate collection rather than an array on {@link Profile} because
 * projects grow without bound, are paginated and edited independently, and are
 * referenced by {@code jobAnalyses}, {@code resumes} and {@code portfolios} -
 * embedding would force N-way duplication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "projects")
public class Project {

    @MongoId(targetType = FieldType.OBJECT_ID)
    private String id;

    /**
     * Owner. MANDATORY on every query - this is the ownership boundary that
     * prevents one user reading or mutating another user's project (IDOR).
     * It is never accepted from the request body; it always comes from the
     * authenticated principal.
     */
    @Field(targetType = FieldType.OBJECT_ID)
    private String userId;

    private String title;

    private String description;

    /** Display values, e.g. {@code ["React", "TypeScript"]}. */
    @Builder.Default
    private List<String> techStack = new ArrayList<>();

    /**
     * DERIVED via SkillNormalizer. This is what the match engine intersects with
     * the skills extracted from a job description. Rebuild on every write.
     */
    @Builder.Default
    private List<String> techStackNormalized = new ArrayList<>();

    private String repositoryUrl;

    private String liveDemoUrl;

    private String imageUrl;

    @Builder.Default
    private List<String> images = new ArrayList<>();

    /** The user's role on the project, e.g. "Full-stack developer". */
    private String role;

    @Builder.Default
    private List<String> features = new ArrayList<>();

    /** Quantified outcomes. These become the resume bullet points. */
    @Builder.Default
    private List<String> achievements = new ArrayList<>();

    private Instant startDate;

    private Instant endDate;

    /** User-pinned. Sorts first when no job-specific ordering applies. */
    private boolean featured;

    /** The /projects checkbox. {@code false} excludes it from every portfolio and the public page. */
    private boolean includeInPortfolio;

    /** Soft delete - keeps historical resumes and analyses referentially meaningful. */
    private boolean deleted;

    private Instant deletedAt;

    private Instant createdAt;

    private Instant updatedAt;
}
