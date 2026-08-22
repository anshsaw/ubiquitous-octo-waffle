package com.portfoliopilot.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry in {@code jobAnalyses.recommendedProjects}.
 *
 * <p>A REFERENCE plus a score - never a copy of the project document. Copying
 * would duplicate large text and image data into every analysis and go stale
 * the moment the user edits the project. Only {@code titleSnapshot} is copied,
 * so a soft-deleted project still renders as readable history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedProject {

    @Field(targetType = FieldType.OBJECT_ID)
    private String projectId;

    private String titleSnapshot;

    /** 0-100. Stored as int32 to satisfy the validator. */
    private int relevanceScore;

    /** Human-readable justification shown on the Match Analysis chips. */
    private String reason;

    @Builder.Default
    private List<String> matchedSkills = new ArrayList<>();
}
