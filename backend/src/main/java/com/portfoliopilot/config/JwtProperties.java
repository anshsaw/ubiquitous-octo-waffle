package com.portfoliopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings bound from {@code app.jwt.*}.
 *
 * @param secret            HMAC-SHA key material. Must be >= 32 bytes for HS256.
 * @param expiration        access-token lifetime in milliseconds
 * @param refreshExpiration refresh-token lifetime in milliseconds
 * @param issuer            {@code iss} claim
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long expiration,
        long refreshExpiration,
        String issuer
) {
    /** The placeholder shipped in application.yml. Never acceptable outside local dev. */
    public static final String DEV_SECRET_PREFIX = "dev-only-insecure-secret";

    public boolean isDevelopmentSecret() {
        return secret != null && secret.startsWith(DEV_SECRET_PREFIX);
    }
}
