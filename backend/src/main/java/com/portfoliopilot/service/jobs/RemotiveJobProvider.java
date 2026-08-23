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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Remotive — the default provider.
 *
 * <p>Chosen because its API is public, free, requires no key and returns real,
 * currently-open listings with genuine apply URLs. That means the feature works
 * out of the box for anyone who clones this repo, without a fake-data mode.
 *
 * <p>Two things about Remotive shape the implementation:
 * <ul>
 *   <li>Its {@code search} parameter is loose — querying "java spring boot"
 *       returns unrelated roles. So results are treated as CANDIDATES and the
 *       real relevance filtering is done by {@code OpportunityRankingService}.</li>
 *   <li>Descriptions are HTML, flattened by {@link HtmlText} at ingestion.</li>
 * </ul>
 *
 * <p>Every listing is remote by definition, which is recorded honestly as
 * {@code workType = REMOTE} rather than echoing whatever the user asked for.
 */
@Slf4j
@Component
public class RemotiveJobProvider implements JobSearchProvider {

    private static final String ENDPOINT = "https://remotive.com/api/remote-jobs";
    private static final int MAX_DESCRIPTION_CHARS = 12_000;

    /**
     * Remotive categories that contain engineering roles.
     *
     * <p>Filtering happens HERE, client-side, because Remotive's own
     * {@code category} query parameter is silently ignored — verified against
     * the live API: requesting {@code category=software-dev} still returns
     * "Marketing" and "All others" rows. Its {@code search} parameter is
     * similarly loose, which is why a query for "Java Backend Developer"
     * returned "Freelance Writer".
     *
     * <p>The {@code category} field on each listing IS accurate, so it is the
     * reliable signal.
     */
    private static final Set<String> TECHNICAL_CATEGORIES = Set.of(
            "software development",
            "devops / sysadmin",
            "data",
            "data analysis",
            "qa",
            "product");

    private final JobsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RemotiveJobProvider(JobsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.timeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String name() {
        return "Remotive";
    }

    @Override
    public boolean isConfigured() {
        return properties.remotiveEnabled();
    }

    @Override
    public String unavailableReason() {
        return isConfigured() ? null : "Remotive is disabled in this deployment.";
    }

    @Override
    public List<JobListing> search(JobQuery query) {
        if (!isConfigured()) {
            return List.of();
        }

        // De-duplicate across queries by listing id: several role variations
        // legitimately return the same posting.
        Map<String, JobListing> byId = new LinkedHashMap<>();

        // Two or three focused queries, not all ten — each is a network round
        // trip, and Remotive's loose matching means extra queries add noise
        // rather than coverage.
        List<String> queries = query.roleQueries().stream().limit(3).toList();

        for (String term : queries) {
            for (JobListing listing : fetch(term)) {
                byId.putIfAbsent(listing.externalId(), listing);
            }
        }

        log.debug("Remotive returned {} distinct listings for {}", byId.size(), queries);
        return new ArrayList<>(byId.values());
    }

    private List<JobListing> fetch(String term) {
        String url = ENDPOINT
                + "?limit=" + properties.maxPerProvider()
                + "&search=" + URLEncoder.encode(term, StandardCharsets.UTF_8);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Accept", "application/json")
                    .header("User-Agent", "PortfolioPilot-AI/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Remotive responded {} for '{}'", response.statusCode(), term);
                return List.of();
            }
            return parse(response.body());

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception ex) {
            // A provider outage must degrade the feature, not break it.
            log.warn("Remotive request failed for '{}': {}", term, ex.getMessage());
            return List.of();
        }
    }

    private List<JobListing> parse(String body) throws Exception {
        JsonNode jobs = objectMapper.readTree(body).path("jobs");
        List<JobListing> listings = new ArrayList<>();

        for (JsonNode node : jobs) {
            String id = node.path("id").asText(null);
            String url = node.path("url").asText(null);
            if (id == null || url == null || url.isBlank()) {
                continue; // no real URL means we cannot honestly link to it
            }

            // Drop non-engineering roles at the source. A "Freelance Writer"
            // posting that happens to mention Git is not an opportunity for a
            // Java developer, and letting it through corrupts the ranking.
            String category = node.path("category").asText("").toLowerCase();
            if (!TECHNICAL_CATEGORIES.contains(category)) {
                continue;
            }

            List<String> tags = new ArrayList<>();
            node.path("tags").forEach(t -> tags.add(t.asText()));

            listings.add(new JobListing(
                    "remotive:" + id,
                    name(),
                    node.path("title").asText(""),
                    node.path("company_name").asText(""),
                    emptyToNull(node.path("company_logo_url").asText("")),
                    emptyToNull(node.path("candidate_required_location").asText("")),
                    "REMOTE",
                    normaliseJobType(node.path("job_type").asText("")),
                    emptyToNull(node.path("salary").asText("")),
                    parseDate(node.path("publication_date").asText(null)),
                    tags,
                    HtmlText.toPlainText(node.path("description").asText(""), MAX_DESCRIPTION_CHARS),
                    url));
        }
        return listings;
    }

    /** Remotive uses {@code full_time}; the app's enum uses {@code FULL_TIME}. */
    private String normaliseJobType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.toLowerCase()) {
            case "full_time" -> "FULL_TIME";
            case "part_time" -> "PART_TIME";
            case "contract", "freelance" -> "FREELANCE";
            case "internship" -> "INTERNSHIP";
            default -> null;
        };
    }

    /** Remotive publishes a local date-time with no zone; treat it as UTC. */
    private Instant parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC);
        } catch (Exception ex) {
            return null;
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
