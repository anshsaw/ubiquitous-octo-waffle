package com.portfoliopilot.model;

import com.portfoliopilot.model.embedded.Certificate;
import com.portfoliopilot.model.embedded.ContactInfo;
import com.portfoliopilot.model.embedded.Education;
import com.portfoliopilot.model.embedded.Experience;
import com.portfoliopilot.model.embedded.Skill;
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
 * Maps the existing {@code profiles} collection - the user's ONE canonical CV
 * dataset, and the single source of truth that job-specific adaptation must
 * never mutate.
 *
 * <p>Skills, education, experience and certificates are embedded because they
 * are small, bounded, owned by exactly one profile, and always read together.
 * One document read serves the whole /profile, /analyzer and /builder page.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "profiles")
public class Profile {

    @MongoId(targetType = FieldType.OBJECT_ID)
    private String id;

    /** Reference to {@link User#getId()}. Unique - exactly one profile per user. */
    @Field(targetType = FieldType.OBJECT_ID)
    private String userId;

    /** Object-storage URL. Binary is never stored in MongoDB. */
    private String avatarUrl;

    private String fullName;

    private String professionalTitle;

    private String bio;

    private String location;

    @Builder.Default
    private ContactInfo contact = new ContactInfo();

    @Builder.Default
    private List<Skill> skills = new ArrayList<>();

    /**
     * DERIVED from {@code skills[].normalizedName}. Two jobs:
     * <ol>
     *   <li>the collection validator declares {@code uniqueItems: true} on this
     *       array, which is how "no duplicate skill per profile" is enforced AT
     *       THE DATABASE LEVEL - JSON Schema cannot express uniqueness on one
     *       field inside an array of objects;</li>
     *   <li>a single multikey index answers "which users have skill X".</li>
     * </ol>
     * MUST be rebuilt on every skills mutation. {@code ProfileService} owns that.
     */
    @Builder.Default
    private List<String> skillIndex = new ArrayList<>();

    @Builder.Default
    private List<Education> education = new ArrayList<>();

    @Builder.Default
    private List<Experience> experience = new ArrayList<>();

    @Builder.Default
    private List<Certificate> certificates = new ArrayList<>();

    /**
     * Cached completeness score (0-100) shown on the dashboard. Recomputed on
     * every profile write so the dashboard needs no aggregation.
     */
    private Integer profileHealth;

    private Instant createdAt;

    private Instant updatedAt;
}
