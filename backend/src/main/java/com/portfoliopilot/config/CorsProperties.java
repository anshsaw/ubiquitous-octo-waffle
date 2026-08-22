package com.portfoliopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * CORS settings bound from {@code app.cors.*}.
 *
 * <p>Driven by the {@code FRONTEND_URL} environment variable so no production
 * domain is ever hardcoded.
 *
 * @param allowedOrigins comma-separated list of browser origins
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(String allowedOrigins) {

    public List<String> originList() {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
