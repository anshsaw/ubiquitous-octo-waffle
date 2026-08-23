package com.portfoliopilot.model.embedded;

import com.portfoliopilot.model.enums.Proficiency;
import com.portfoliopilot.model.enums.SkillCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One skill inside {@code profiles.skills}.
 *
 * <p>Every skill is stored twice on purpose:
 * <ul>
 *   <li>{@code name} - exactly what the user typed. Display value.</li>
 *   <li>{@code normalizedName} - the canonical key. ALL matching, gap analysis
 *       and analytics compare on this field.</li>
 * </ul>
 * Without the second field, "React", "React.js" and "ReactJS" are three
 * different skills and the match engine silently under-reports.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill {

    private String name;

    /** Produced by SkillNormalizer + the skillDictionary alias table. */
    private String normalizedName;

    private Proficiency proficiency;

    private Double yearsOfExperience;

    /** Denormalised from {@code skillDictionary.category} so the UI can group without a second query. */
    private SkillCategory category;
}
