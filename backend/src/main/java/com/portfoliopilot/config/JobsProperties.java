package com.portfoliopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Job-provider configuration, bound from {@code app.jobs.*}.
 *
 * <p>Every credential here comes from an environment variable and stays on the
 * server. Nothing in this record is ever serialised to the browser — the
 * frontend only learns a provider's NAME and whether it is available.
 *
 * @param remotiveEnabled     Remotive needs no credentials, so it is the default source
 * @param adzunaAppId         from developer.adzuna.com
 * @param adzunaAppKey        from developer.adzuna.com
 * @param adzunaCountry       Adzuna partitions by country, e.g. {@code gb}, {@code in}, {@code us}
 * @param linkedinClientId    LinkedIn OAuth app id
 * @param linkedinClientSecret LinkedIn OAuth secret — server-side only
 * @param linkedinPartnerEnabled set true ONLY with an approved LinkedIn Talent
 *                               Solutions agreement; job search is not part of
 *                               the self-serve LinkedIn API
 * @param timeoutMs           per-provider HTTP timeout
 * @param maxPerProvider      cap on listings fetched from each provider
 */
@ConfigurationProperties(prefix = "app.jobs")
public record JobsProperties(
        boolean remotiveEnabled,
        String adzunaAppId,
        String adzunaAppKey,
        String adzunaCountry,
        String linkedinClientId,
        String linkedinClientSecret,
        boolean linkedinPartnerEnabled,
        int timeoutMs,
        int maxPerProvider
) {

    public boolean hasAdzunaCredentials() {
        return notBlank(adzunaAppId) && notBlank(adzunaAppKey);
    }

    public boolean hasLinkedInCredentials() {
        return notBlank(linkedinClientId) && notBlank(linkedinClientSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
