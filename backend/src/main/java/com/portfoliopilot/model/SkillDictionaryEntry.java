package com.portfoliopilot.model;

import com.portfoliopilot.model.enums.SkillCategory;
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
 * Maps the existing {@code skillDictionary} collection - the global alias table
 * that collapses "React", "React.js" and "ReactJS" into one canonical skill.
 *
 * <p>Without it the product is quietly broken: a user with "ReactJS" would show
 * a skill GAP for a job asking "React.js", and the admin gap chart would split
 * one real skill across three bars.
 *
 * <p>Tiny and read-heavy - cached in memory by {@code SkillDictionaryService}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "skillDictionary")
public class SkillDictionaryEntry {

    @MongoId(targetType = FieldType.OBJECT_ID)
    private String id;

    /** Correctly-cased display form, e.g. "Spring Boot". */
    private String canonicalName;

    /** Unique canonical key, e.g. "spring boot". Written into profiles and projects. */
    private String normalizedName;

    /** Already-normalised alternative spellings, e.g. {@code ["springboot"]}. */
    @Builder.Default
    private List<String> aliases = new ArrayList<>();

    private SkillCategory category;

    /**
     * normalizedName values of adjacent skills. Enables partial-credit scoring
     * ("Spring" is evidence for "Spring Boot") and "learn this next" hints.
     */
    @Builder.Default
    private List<String> relatedSkills = new ArrayList<>();

    /**
     * 0..1 importance multiplier used by the scorer, so a niche framework
     * outranks a generic term like "programming".
     */
    private Double weight;

    private boolean active;

    private Instant createdAt;

    private Instant updatedAt;
}
