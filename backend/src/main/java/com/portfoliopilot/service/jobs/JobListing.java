package com.portfoliopilot.service.jobs;

import java.time.Instant;
import java.util.List;

/**
 * A normalised job listing, provider-agnostic.
 *
 * <p>Every provider maps its own payload into this shape, so ranking, matching
 * and the UI never learn where a listing came from. Adding a provider means
 * adding one mapper, not touching the scoring or the frontend.
 *
 * <p>{@code description} is ALWAYS plain text. Providers return HTML (Remotive
 * does), and it is stripped at the boundary — before it reaches the skill
 * extractor, and long before it reaches a browser.
 *
 * @param externalId provider-scoped id, used for de-duplication
 * @param source     provider name, e.g. {@code "Remotive"} — shown in the UI so
 *                   the user always knows where a listing came from
 * @param url        the real listing URL. Never synthesised.
 */
public record JobListing(
        String externalId,
        String source,
        String title,
        String company,
        String companyLogoUrl,
        String location,
        String workType,
        String employmentType,
        String salary,
        Instant postedAt,
        List<String> tags,
        String description,
        String url
) {
}
