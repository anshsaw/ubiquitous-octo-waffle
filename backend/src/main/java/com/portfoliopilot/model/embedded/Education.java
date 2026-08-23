package com.portfoliopilot.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * One entry inside {@code profiles.education}.
 *
 * <p>Carries its own {@code _id} so the API can PATCH or DELETE a single entry
 * by id instead of rewriting the whole array - rewriting loses concurrent edits.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Education {

    @Field(value = "_id", targetType = FieldType.OBJECT_ID)
    private String id;

    private String degree;

    private String institution;

    private String fieldOfStudy;

    /** The database stores real integers; the legacy frontend used a free-text string. */
    private Integer startYear;

    /** {@code null} means ongoing. */
    private Integer endYear;

    private String grade;

    private String description;
}
