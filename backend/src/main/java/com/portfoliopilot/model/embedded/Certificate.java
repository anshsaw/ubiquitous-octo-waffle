package com.portfoliopilot.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

/** One entry inside {@code profiles.certificates}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Certificate {

    @Field(value = "_id", targetType = FieldType.OBJECT_ID)
    private String id;

    private String name;

    private String issuingOrganization;

    private Instant issueDate;

    private Instant expiryDate;

    private String credentialId;

    private String credentialUrl;
}
