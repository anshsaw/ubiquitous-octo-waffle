package com.portfoliopilot.model.embedded;

import com.portfoliopilot.model.enums.Proficiency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A skill as frozen into a generated resume.
 *
 * <p>Array ORDER is the render order - matched skills first. Do not sort at
 * read time; the ordering is part of what makes the resume "tailored".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeSkill {

    private String name;

    private String normalizedName;

    private Proficiency proficiency;

    /** True when this skill was explicitly demanded by the job description. */
    private boolean matched;
}
