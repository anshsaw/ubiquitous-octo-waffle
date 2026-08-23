package com.portfoliopilot.model;

import com.portfoliopilot.model.embedded.AnalysisResult;
import com.portfoliopilot.model.embedded.JobDetails;
import com.portfoliopilot.model.embedded.RecommendedProject;
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
 * Maps the existing {@code jobAnalyses} collection - the heart of the product.
 *
 * <p>One record per "Analyze Opportunity" action. It is an EVENT SNAPSHOT: once
 * written it is never edited, so a user can revisit a months-old analysis and
 * see exactly what the engine concluded then, even though their profile has
 * changed since. That is why there is intentionally no {@code updatedAt}.
 *
 * <p>{@code resumeId} and {@code portfolioId} are the only mutable fields, and
 * only as back-pointers to artifacts produced FROM this analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "jobAnalyses")
public class JobAnalysis {

    @MongoId(targetType = FieldType.OBJECT_ID)
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String userId;

    private JobDetails job;

    private AnalysisResult analysis;

    @Builder.Default
    private List<RecommendedProject> recommendedProjects = new ArrayList<>();

    /** Auto-written summary aimed at this specific role. Copied into the resume. */
    private String tailoredSummary;

    /**
     * Denormalised back-pointer so /match-analysis can show "resume already
     * generated" without a second query. {@code resumes.jobAnalysisId} remains
     * the authoritative link.
     */
    @Field(targetType = FieldType.OBJECT_ID)
    private String resumeId;

    /** Back-pointer to the job-specific adapted portfolio, if one was generated. */
    @Field(targetType = FieldType.OBJECT_ID)
    private String portfolioId;

    private boolean deleted;

    private Instant deletedAt;

    private Instant createdAt;
}
