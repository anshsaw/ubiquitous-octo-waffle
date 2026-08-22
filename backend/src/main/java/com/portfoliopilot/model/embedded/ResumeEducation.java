package com.portfoliopilot.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Education frozen into a resume.
 *
 * <p>A SNAPSHOT, not a reference: a resume is a delivered artifact, and a PDF
 * already sent to an employer must remain reproducible byte-for-byte even after
 * the profile changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeEducation {

    private String degree;

    private String institution;

    private String fieldOfStudy;

    private Integer startYear;

    private Integer endYear;

    private String grade;
}
