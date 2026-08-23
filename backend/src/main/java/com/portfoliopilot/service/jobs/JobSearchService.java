package com.portfoliopilot.service.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fans a query out across every configured provider and merges the results.
 *
 * <p>Spring injects all {@link JobSearchProvider} beans, so adding a source is
 * one new class and nothing else — no registry to update, no switch to extend.
 *
 * <p>Partial failure is normal and handled: an unconfigured or failing provider
 * contributes zero listings and a reason, while the others still return. The
 * caller always learns exactly which sources ran, so the UI can say
 * "LinkedIn unavailable, searched Remotive" instead of silently showing less.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobSearchService {

    private final List<JobSearchProvider> providers;

    /**
     * @param listings  merged, de-duplicated listings from every source that ran
     * @param statuses  one entry per provider, available or not, with a reason
     */
    public record SearchOutcome(List<JobListing> listings, List<ProviderStatus> statuses) {

        public boolean anyProviderRan() {
            return statuses.stream().anyMatch(ProviderStatus::available);
        }

        public boolean isEmpty() {
            return listings.isEmpty();
        }
    }

    /** @param reason null when available; a user-facing explanation otherwise */
    public record ProviderStatus(String name, boolean available, String reason, int resultCount) {
    }

    public SearchOutcome search(JobQuery query) {
        // Keyed by URL rather than provider id: the same posting syndicated to
        // two boards is one opportunity to the user.
        Map<String, JobListing> merged = new LinkedHashMap<>();
        List<ProviderStatus> statuses = new ArrayList<>();

        for (JobSearchProvider provider : providers) {
            if (!provider.isConfigured()) {
                statuses.add(new ProviderStatus(provider.name(), false, provider.unavailableReason(), 0));
                continue;
            }

            int before = merged.size();
            try {
                for (JobListing listing : provider.search(query)) {
                    if (listing.url() != null && !listing.url().isBlank()) {
                        merged.putIfAbsent(listing.url(), listing);
                    }
                }
                statuses.add(new ProviderStatus(provider.name(), true, null, merged.size() - before));

            } catch (RuntimeException ex) {
                // Defensive: providers are contracted not to throw, but one
                // misbehaving source must not fail the whole search.
                log.error("Provider {} threw during search: {}", provider.name(), ex.getMessage());
                statuses.add(new ProviderStatus(provider.name(), false,
                        "This source failed to respond. Try again shortly.", 0));
            }
        }

        log.info("Job search '{}' -> {} listings from {} provider(s)",
                query.primaryQuery(), merged.size(),
                statuses.stream().filter(ProviderStatus::available).count());

        return new SearchOutcome(new ArrayList<>(merged.values()), statuses);
    }

    /** Provider availability without running a search — powers the status panel. */
    public List<ProviderStatus> statuses() {
        return providers.stream()
                .map(p -> new ProviderStatus(p.name(), p.isConfigured(), p.unavailableReason(), 0))
                .toList();
    }
}
