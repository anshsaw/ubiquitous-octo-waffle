package com.portfoliopilot.model.embedded;

import com.portfoliopilot.model.enums.EmploymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.List;

/** One entry inside {@code profiles.experience}. A student profile may legitimately have none. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Experience {

    @Field(value = "_id", targetType = FieldType.OBJECT_ID)
    private String id;

    private String company;

    private String role;

    private String location;

    private EmploymentType employmentType;

    private Instant startDate;

    /** {@code null} means currently employed here. */
    private Instant endDate;

    private String description;

    private List<String> responsibilities;

    private List<String> technologies;

    /** Derived. Lets experience technologies feed the same scoring as profile skills. */
    private List<String> technologiesNormalized;
}
