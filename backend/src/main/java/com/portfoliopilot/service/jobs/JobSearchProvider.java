package com.portfoliopilot.service.jobs;

import java.util.List;

/**
 * A source of real job listings.
 *
 * <p>The contract has one rule above all others: <strong>a provider returns
 * real listings or it returns nothing.</strong> It must never fabricate a
 * company, a title or a URL to fill a gap. When a provider cannot serve a
 * request it reports {@link #isConfigured()} {@code false} with a human
 * {@link #unavailableReason()}, and the UI says so plainly.
 *
 * <p>That is why LinkedIn is implemented as a provider that is normally
 * unavailable rather than omitted: the integration boundary exists and is
 * honest about its state, instead of the product pretending LinkedIn data was
 * retrieved.
 */
public interface JobSearchProvider {

    /** Display name shown next to every listing, e.g. {@code "Remotive"}. */
    String name();

    /**
     * Whether this provider can currently serve requests — credentials present,
     * feature enabled. Checked before every search.
     */
    boolean isConfigured();

    /**
     * Why the provider is unavailable, phrased for a user. Returns {@code null}
     * when {@link #isConfigured()} is true.
     */
    String unavailableReason();

    /**
     * Executes the search.
     *
     * <p>Implementations must never throw for a routine failure — a timeout or
     * a 500 from the upstream API returns an empty list, so one failing
     * provider cannot take down the whole search.
     */
    List<JobListing> search(JobQuery query);
}
