package com.portfoliopilot.dto.project;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Create or update a project.
 *
 * <p>Note what is ABSENT: {@code userId}. Ownership is taken from the
 * authenticated principal, never from the body. Accepting it here would be the
 * classic mass-assignment hole that lets a caller write into another account.
 *
 * <p>{@code tech} is a legacy alias of {@code techStack} for the existing
 * frontend; {@code repo}/{@code live} likewise alias the URL fields.
 */
public record ProjectRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 160, message = "Title must be 160 characters or fewer")
        @Schema(example = "Campus Placement Portal")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 5000, message = "Description must be 5000 characters or fewer")
        String description,

        @Size(max = 40, message = "At most 40 technologies")
        @Schema(example = "[\"Java\", \"Spring Boot\", \"MySQL\"]")
        List<@NotBlank @Size(max = 60) String> techStack,

        @Size(max = 40)
        @Schema(description = "Legacy alias of techStack")
        List<@Size(max = 60) String> tech,

        @Size(max = 2048)
        String repositoryUrl,

        @Size(max = 2048)
        @Schema(description = "Legacy alias of repositoryUrl")
        String repo,

        @Size(max = 2048)
        String liveDemoUrl,

        @Size(max = 2048)
        @Schema(description = "Legacy alias of liveDemoUrl")
        String live,

        @Size(max = 2048)
        String imageUrl,

        @Size(max = 10, message = "At most 10 gallery images")
        List<@Size(max = 2048) String> images,

        @Size(max = 120)
        @Schema(example = "Backend developer")
        String role,

        @Size(max = 20, message = "At most 20 features")
        List<@Size(max = 400) String> features,

        @Size(max = 20, message = "At most 20 achievements")
        @Schema(description = "Quantified outcomes. These become the resume bullet points.")
        List<@Size(max = 400) String> achievements,

        Instant startDate,

        Instant endDate,

        Boolean featured,

        @Schema(description = "Include this project in generated portfolios and the public page")
        Boolean includeInPortfolio
) {

    /** The database requires at least one technology, so one of the two aliases must be present. */
    @NotEmpty(message = "At least one technology is required")
    public List<String> resolveTechStack() {
        if (techStack != null && !techStack.isEmpty()) {
            return techStack;
        }
        return tech == null ? List.of() : tech;
    }

    public String resolveRepositoryUrl() {
        return repositoryUrl != null ? repositoryUrl : repo;
    }

    public String resolveLiveDemoUrl() {
        return liveDemoUrl != null ? liveDemoUrl : live;
    }
}
