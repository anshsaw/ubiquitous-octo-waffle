package com.portfoliopilot.dto.opportunity;

import com.portfoliopilot.service.jobs.CandidateProfile;
import com.portfoliopilot.service.jobs.JobSearchService;
import com.portfoliopilot.service.jobs.ScoredOpportunity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Search results plus enough context for the UI to be honest about them.
 *
 * <p>{@code providers} is not decoration: it is how the page can say
 * "LinkedIn unavailable — searched Remotive" instead of quietly returning
 * fewer results and letting the user assume that is all that exists.
 */
public record OpportunitySearchResponse(
        List<OpportunityDto> opportunities,
        @Schema(description = "The candidate profile the search was built from")
        CandidateSummary candidate,
        @Schema(description = "Role phrases that were actually searched")
        List<String> queries,
        @Schema(description = "Per-source availability and result counts")
        List<ProviderStatusDto> providers,
        int totalFound,
        int shown,
        Instant searchedAt
) {

    /**
     * @param source PUBLISHED_PORTFOLIO or PROFILE — the UI tells the user which,
     *               so an unpublished portfolio does not silently change results
     */
    public record CandidateSummary(
            String source,
            String title,
            String experienceLevel,
            double yearsOfExperience,
            List<String> skills,
            int projectCount,
            @Schema(description = "Skills evidenced by a project or role, not merely listed")
            List<String> demonstratedSkills
    ) {
        public static CandidateSummary from(CandidateProfile candidate) {
            return new CandidateSummary(
                    candidate.source(),
                    candidate.title(),
                    candidate.experienceLevel(),
                    candidate.yearsOfExperience(),
                    candidate.displaySkills(),
                    candidate.projects().size(),
                    List.copyOf(candidate.evidence()));
        }
    }

    public record ProviderStatusDto(String name, boolean available, String reason, int resultCount) {
        public static ProviderStatusDto from(JobSearchService.ProviderStatus status) {
            return new ProviderStatusDto(status.name(), status.available(), status.reason(), status.resultCount());
        }
    }

    /** One ranked opportunity. {@code url} is always the provider's real listing. */
    public record OpportunityDto(
            String id,
            String source,
            String title,
            String company,
            String companyLogoUrl,
            String location,
            String workType,
            String employmentType,
            String salary,
            Instant postedAt,
            @Schema(example = "87") int matchScore,
            List<String> matchedSkills,
            @Schema(description = "Listed in skills but not evidenced by a project or role")
            List<String> partialSkills,
            @Schema(description = "Not demonstrated in the current portfolio")
            List<String> missingSkills,
            ScoreBreakdown breakdown,
            String explanation,
            @Schema(description = "Plain text; provider HTML is stripped server-side")
            String description,
            String url
    ) {

        public static OpportunityDto from(ScoredOpportunity scored) {
            var listing = scored.listing();
            var b = scored.breakdown();
            return new OpportunityDto(
                    listing.externalId(),
                    listing.source(),
                    listing.title(),
                    listing.company(),
                    listing.companyLogoUrl(),
                    listing.location(),
                    listing.workType(),
                    listing.employmentType(),
                    listing.salary(),
                    listing.postedAt(),
                    scored.matchScore(),
                    scored.matchedSkills(),
                    scored.partialSkills(),
                    scored.missingSkills(),
                    new ScoreBreakdown(b.skills(), b.role(), b.experience(),
                            b.projects(), b.education(), b.location()),
                    scored.explanation(),
                    listing.description(),
                    listing.url());
        }
    }

    /** Per-component sub-scores, so the total is auditable rather than opaque. */
    public record ScoreBreakdown(
            int skills, int role, int experience, int projects, int education, int location) {
    }
}
