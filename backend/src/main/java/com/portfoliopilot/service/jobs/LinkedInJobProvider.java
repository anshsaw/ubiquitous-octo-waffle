package com.portfoliopilot.service.jobs;

import com.portfoliopilot.config.JobsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LinkedIn — integration boundary.
 *
 * <p><strong>Read this before wiring anything to it.</strong>
 *
 * <p>LinkedIn does not expose job search in its self-serve API. Retrieving
 * listings programmatically requires a signed LinkedIn Talent Solutions
 * partnership; the public {@code api.linkedin.com} surface returns 401 for job
 * endpoints regardless of how a normal OAuth app is configured. There is no
 * key you can generate today that makes this class return data.
 *
 * <p>So the class exists to define the seam, not to pretend. It reports itself
 * unavailable with a truthful reason, and {@link #search} returns an empty list
 * — never a fabricated listing, a fabricated company or a synthesised
 * {@code linkedin.com/jobs/view/...} URL. Inventing those would be worse than
 * having no integration, because the user cannot tell the difference until they
 * click.
 *
 * <p>The deliberately rejected alternative is HTML scraping. It breaches
 * LinkedIn's terms, breaks without warning, and gets the deployment's IP
 * blocked. It is not implemented and should not be added.
 *
 * <p><b>To enable it once you hold a partner agreement:</b>
 * <ol>
 *   <li>set {@code LINKEDIN_CLIENT_ID} and {@code LINKEDIN_CLIENT_SECRET};</li>
 *   <li>set {@code LINKEDIN_PARTNER_ENABLED=true};</li>
 *   <li>implement the OAuth 2.0 client-credentials exchange and the partner
 *       job-search call in {@link #search}, mapping into {@link JobListing}.</li>
 * </ol>
 * Nothing else in the application changes: the provider registry picks it up,
 * ranking and the UI already treat every provider identically.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkedInJobProvider implements JobSearchProvider {

    private final JobsProperties properties;

    @Override
    public String name() {
        return "LinkedIn";
    }

    /**
     * Requires BOTH credentials and an explicit partner flag. The flag is
     * separate on purpose: having OAuth credentials is not the same as being
     * authorised for job data, and conflating the two would make the UI claim
     * an integration that cannot work.
     */
    @Override
    public boolean isConfigured() {
        return properties.linkedinPartnerEnabled() && properties.hasLinkedInCredentials();
    }

    @Override
    public String unavailableReason() {
        if (isConfigured()) {
            return null;
        }
        if (!properties.hasLinkedInCredentials()) {
            return "LinkedIn is not connected. Job retrieval needs a LinkedIn Talent Solutions "
                    + "partnership - job search is not part of the public LinkedIn API.";
        }
        return "LinkedIn credentials are present but partner access is not enabled "
                + "(set LINKEDIN_PARTNER_ENABLED=true once your agreement is approved).";
    }

    @Override
    public List<JobListing> search(JobQuery query) {
        if (!isConfigured()) {
            return List.of();
        }

        // Reached only with partner access configured. Until the partner call
        // is implemented, returning empty is the honest behaviour - the search
        // response will report LinkedIn as returning no results rather than the
        // UI implying LinkedIn data was retrieved.
        log.warn("LinkedIn partner mode is enabled but the partner job-search call is not implemented; "
                + "returning no listings rather than fabricating any.");
        return List.of();
    }
}
