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
 * A project slot in a generated resume.
 *
 * <p>Reference + explicit priority. The project BODY (description, achievements)
 * is read live from {@code projects} at render time so later edits propagate;
 * only the ordering and a title fallback are frozen here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeProject {

    @Field(targetType = FieldType.OBJECT_ID)
    private String projectId;

    /** 1 = most relevant, printed first. */
    private int priority;

    /** Kept so the resume stays readable even if the project is later deleted. */
    private String titleSnapshot;

    /** Bullets selected for THIS job. Empty means render the project defaults. */
    @Builder.Default
    private List<String> highlightedBullets = new ArrayList<>();
}
