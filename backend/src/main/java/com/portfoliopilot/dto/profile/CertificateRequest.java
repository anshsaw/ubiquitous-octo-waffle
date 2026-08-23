package com.portfoliopilot.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Creating or updating one certificate. */
public record CertificateRequest(

        @NotBlank(message = "Certificate name is required")
        @Size(max = 200)
        String name,

        @NotBlank(message = "Issuing organization is required")
        @Size(max = 160)
        String issuingOrganization,

        Instant issueDate,

        Instant expiryDate,

        @Size(max = 120)
        String credentialId,

        @Size(max = 2048)
        String credentialUrl
) {
}
