package com.portfoliopilot.service.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfoliopilot.config.JobsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adzuna — optional second provider, covering non-remote and location-specific
 * roles that Remotive structurally cannot return.
 *
 * <p>Free developer credentials from developer.adzuna.com. Without them the
 * provider reports itself unavailable and is skipped; it never degrades into
 * placeholder data.
 *
 * <p>Adzuna partitions its index by country, so {@code ADZUNA_COUNTRY} must
 * match where the user is searching — a query for "Pune" against the {@code gb}
 * index legitimately returns nothing.
 */
@Slf4j
@Component
public class AdzunaJobProvider implements JobSearchProvider {

    private static final String ENDPOINT = "https://api.adzuna.com/v1/api/jobs/%s/search/1";
    private static final int MAX_DESCRIPTION_CHARS = 12_000;

    private final JobsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AdzunaJobProvider(JobsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.timeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String name() {
        return "Adzuna";
    }

    @Override
    public boolean isConfigured() {
        return properties.hasAdzunaCredentials();
    }

    @Override
    public String unavailableReason() {
        return isConfigured() ? null
                : "Adzuna is not configured. Add ADZUNA_APP_ID and ADZUNA_APP_KEY "
                + "(free at developer.adzuna.com) to include location-based roles.";
    }

    @Override
    public List<JobListing> search(JobQuery query) {
        if (!isConfigured()) {
            return List.of();
        }

        Map<String, JobListing> byId = new LinkedHashMap<>();
        for (String term : query.roleQueries().stream().limit(2).toList()) {
            for (JobListing listing : fetch(term, query)) {
                byId.putIfAbsent(listing.externalId(), listing);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private List<JobListing> fetch(String term, JobQuery query) {
        String country = properties.adzunaCountry() == null ? "gb" : properties.adzunaCountry();

        StringBuilder url = new StringBuilder(String.format(ENDPOINT, country))
                .append("?app_id=").append(URLEncoder.encode(properties.adzunaAppId(), StandardCharsets.UTF_8))
                .append("&app_key=").append(URLEncoder.encode(properties.adzunaAppKey(), StandardCharsets.UTF_8))
                .append("&results_per_page=").append(properties.maxPerProvider())
                .append("&content-type=application/json")
                .append("&what=").append(URLEncoder.encode(term, StandardCharsets.UTF_8));

        if (query.location() != null && !query.location().isBlank()) {
            url.append("&where=").append(URLEncoder.encode(query.location(), StandardCharsets.UTF_8));
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Adzuna responded {} for '{}'", response.statusCode(), term);
                return List.of();
            }
            return parse(response.body());

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception ex) {
            log.warn("Adzuna request failed for '{}': {}", term, ex.getMessage());
            return List.of();
        }
    }

    private List<JobListing> parse(String body) throws Exception {
        JsonNode results = objectMapper.readTree(body).path("results");
        List<JobListing> listings = new ArrayList<>();

        for (JsonNode node : results) {
            String id = node.path("id").asText(null);
            String url = node.path("redirect_url").asText(null);
            if (id == null || url == null || url.isBlank()) {
                continue;
            }

            String salary = null;
            if (node.hasNonNull("salary_min") && node.hasNonNull("salary_max")) {
                salary = String.format("%,.0f - %,.0f",
                        node.path("salary_min").asDouble(), node.path("salary_max").asDouble());
            }

            listings.add(new JobListing(
                    "adzuna:" + id,
                    name(),
                    node.path("title").asText(""),
                    node.path("company").path("display_name").asText(""),
                    null,
                    node.path("location").path("display_name").asText(null),
                    null, // Adzuna does not classify remote/hybrid reliably
                    node.path("contract_time").asText(null),
                    salary,
                    parseDate(node.path("created").asText(null)),
                    List.of(),
                    HtmlText.toPlainText(node.path("description").asText(""), MAX_DESCRIPTION_CHARS),
                    url));
        }
        return listings;
    }

    private Instant parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (Exception ex) {
            return null;
        }
    }
}
