package com.portfoliopilot.model;

import com.portfoliopilot.model.embedded.ResumeCertificate;
import com.portfoliopilot.model.embedded.ResumeEducation;
import com.portfoliopilot.model.embedded.ResumeExperience;
import com.portfoliopilot.model.embedded.ResumeProject;
import com.portfoliopilot.model.embedded.ResumeSkill;
import com.portfoliopilot.model.enums.ResumeTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps the existing {@code resumes} collection - a generated, job-targeted CV.
 *
 * <p>{@code jobAnalysisId} is mandatory. It is the link that makes a resume
 * "tailored" rather than generic, and it answers the product's key question:
 * <em>which job was this resume created for?</em>
 *
 * <p>Two different persistence strategies coexist here on purpose:
 * <ul>
 *   <li>skills / education / experience / certificates are SNAPSHOTS, so a
 *       delivered PDF never changes under the candidate's feet;</li>
 *   <li>projects are REFERENCES, because their bodies are large and are re-read
 *       at render time.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "resumes")
public class Resume {

    @MongoId(targetType = FieldType.OBJECT_ID)
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String userId;

    /** Reference to {@link JobAnalysis}. MANDATORY. */
    @Field(targetType = FieldType.OBJECT_ID)
    private String jobAnalysisId;

    private String targetRole;

    private String targetCompany;

    /** The auto-written summary line, reflecting THIS role. */
    private String summary;

    @Builder.Default
    private List<ResumeSkill> skills = new ArrayList<>();

    @Builder.Default
    private List<ResumeProject> projects = new ArrayList<>();

    @Builder.Default
    private List<ResumeEducation> education = new ArrayList<>();

    @Builder.Default
    private List<ResumeExperience> experience = new ArrayList<>();

    @Builder.Default
    private List<ResumeCertificate> certificates = new ArrayList<>();

    @Builder.Default
    private ResumeTemplate template = ResumeTemplate.ATS_CLASSIC;

    /** Object-storage URL of the last rendered PDF. The binary is never stored in MongoDB. */
    private String pdfUrl;

    @Builder.Default
    private Integer downloadCount = 0;

    private boolean deleted;

    private Instant deletedAt;

    private Instant createdAt;

    private Instant updatedAt;
}
