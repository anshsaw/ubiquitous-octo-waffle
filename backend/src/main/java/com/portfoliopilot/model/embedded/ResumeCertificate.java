package com.portfoliopilot.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Certificate frozen into a resume. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeCertificate {

    private String name;

    private String issuingOrganization;

    private Instant issueDate;

    private String credentialUrl;
}
