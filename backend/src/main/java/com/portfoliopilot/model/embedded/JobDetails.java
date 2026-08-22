package com.portfoliopilot.model.embedded;

import com.portfoliopilot.model.enums.EmploymentType;
import com.portfoliopilot.model.enums.JobSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code jobAnalyses.job} - an embedded SNAPSHOT of the pasted posting.
 *
 * <p>Deliberately duplicated rather than referenced: a job posting has no
 * independent lifecycle in this product, and the raw text must stay frozen so
 * an analysis can be audited or re-scored later with a better engine.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDetails {

    private String title;

    /**
     * DERIVED lowercase title. Powers the "most analysed roles" chart without
     * running {@code $toLower} over the whole collection at query time.
     */
    private String normalizedTitle;

    private String company;

    private String location;

    private EmploymentType employmentType;

    /** Raw pasted job description. Capped at 30 000 chars by the validator. */
    private String description;

    private String sourceUrl;

    private JobSource source;
}
