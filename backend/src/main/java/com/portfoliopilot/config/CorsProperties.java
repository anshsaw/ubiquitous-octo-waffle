package com.portfoliopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * CORS settings bound from {@code app.cors.*}.
 *
 * <p>Driven by {@code FRONTEND_URL} so no production domain is ever hardcoded.
 * The value REPLACES the built-in development defaults and may be a
 * comma-separated list.
 *
 * @param allowedOrigins comma-separated list of browser origins
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(String allowedOrigins) {

    public List<String> originList() {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return List.of();
        }

        // LinkedHashSet: de-duplicate while keeping the configured order, which
        // keeps the startup log readable.
        LinkedHashSet<String> origins = new LinkedHashSet<>();
        Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(origin -> {
                    origins.add(origin);
                    origins.addAll(loopbackTwins(origin));
                });

        return new ArrayList<>(origins);
    }

    /**
     * For a loopback origin, also allows the other spelling of the same host.
     *
     * <p>{@code http://localhost:5500} and {@code http://127.0.0.1:5500} are
     * DIFFERENT origins to a browser, but the same machine to a developer.
     * VS Code Live Server opens {@code 127.0.0.1} while the documentation says
     * {@code localhost}, so setting {@code FRONTEND_URL} exactly as documented
     * used to produce a 403 preflight and an unexplained login failure.
     *
     * <p>This only ever fires for loopback hosts - a real domain gets no extra
     * origins, so production is unaffected.
     */
    private static List<String> loopbackTwins(String origin) {
        if (origin.contains("://localhost")) {
            return List.of(origin.replace("://localhost", "://127.0.0.1"));
        }
        if (origin.contains("://127.0.0.1")) {
            return List.of(origin.replace("://127.0.0.1", "://localhost"));
        }
        return List.of();
    }
}
