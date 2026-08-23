package com.portfoliopilot.model.embedded;

import com.portfoliopilot.model.enums.EmploymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Experience frozen into a resume, re-ordered for relevance to the target role. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeExperience {

    private String company;

    private String role;

    private EmploymentType employmentType;

    private Instant startDate;

    private Instant endDate;

    /** Copied from {@code Experience.responsibilities}. Never invented. */
    @Builder.Default
    private List<String> bullets = new ArrayList<>();
}
