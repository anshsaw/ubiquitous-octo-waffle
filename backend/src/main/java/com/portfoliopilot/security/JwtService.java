package com.portfoliopilot.security;

import com.portfoliopilot.config.JwtProperties;
import com.portfoliopilot.exception.InvalidTokenException;
import com.portfoliopilot.model.User;
import com.portfoliopilot.model.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

/**
 * Issues and verifies access tokens, and mints opaque refresh tokens.
 *
 * <p>Two token types, on purpose:
 * <ul>
 *   <li><b>Access token</b> - a signed JWT, short-lived (15 min), stateless.
 *       Cannot be revoked, which is exactly why it must be short-lived.</li>
 *   <li><b>Refresh token</b> - an opaque 256-bit random string, long-lived.
 *       Only its SHA-256 hash is persisted, so a database dump yields no usable
 *       sessions, and it can be revoked by deleting the row.</li>
 * </ul>
 *
 * <p>A JWT is deliberately NOT used for the refresh token: a self-contained
 * refresh JWT is unrevocable, which defeats the entire point of having one.
 */
@Slf4j
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_USERNAME = "username";
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(JwtProperties properties) {
        this.properties = properties;

        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // HS256 requires >= 256 bits. Failing at startup is far better than
            // discovering it on the first login attempt.
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 bytes for HS256 (got " + keyBytes.length + ")");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // ---------------------------------------------------------------- access

    /** Signs a short-lived access token for the given user. */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(properties.expiration());

        return Jwts.builder()
                .issuer(properties.issuer())
                // Subject is the immutable user id, never the email or username -
                // both of those can change without invalidating the account.
                .subject(user.getId())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_USERNAME, user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies signature and expiry, then rebuilds the principal from the claims.
     *
     * @throws InvalidTokenException on any failure - expired, tampered, malformed
     */
    public UserPrincipal parseAccessToken(String token) {
        Claims claims = parseClaims(token);

        String roleName = claims.get(CLAIM_ROLE, String.class);
        Role role;
        try {
            role = Role.valueOf(roleName);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidTokenException("Token contains an unknown role");
        }

        return new UserPrincipal(
                claims.getSubject(),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_USERNAME, String.class),
                role
        );
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new InvalidTokenException("Access token has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            // The specific reason is intentionally not echoed to the caller.
            log.debug("Rejected JWT: {}", ex.getMessage());
            throw new InvalidTokenException("Invalid access token");
        }
    }

    /** Access-token lifetime in seconds, returned to the client so it can pre-emptively refresh. */
    public long accessTokenTtlSeconds() {
        return properties.expiration() / 1000;
    }

    // --------------------------------------------------------------- refresh

    /** A cryptographically random, URL-safe, opaque refresh token. */
    public String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 of a refresh token. This - never the token itself - is what gets
     * persisted, so the stored value is useless to anyone who reads the database.
     *
     * <p>Plain SHA-256 rather than bcrypt is correct here: the input is already
     * 256 bits of entropy, so there is nothing to brute-force, and lookups must
     * be a single indexed equality match rather than a scan-and-compare.
     */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable in this JVM", ex);
        }
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plusMillis(properties.refreshExpiration());
    }
}
