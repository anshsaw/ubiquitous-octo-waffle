package com.portfoliopilot.dto.profile;

import com.portfoliopilot.model.embedded.Certificate;

import java.time.Instant;

/** A certificate on the wire. */
public record CertificateDto(
        String id,
        String name,
        String issuingOrganization,
        Instant issueDate,
        Instant expiryDate,
        String credentialId,
        String credentialUrl
) {

    public static CertificateDto from(Certificate certificate) {
        return new CertificateDto(
                certificate.getId(),
                certificate.getName(),
                certificate.getIssuingOrganization(),
                certificate.getIssueDate(),
                certificate.getExpiryDate(),
                certificate.getCredentialId(),
                certificate.getCredentialUrl());
    }
}
