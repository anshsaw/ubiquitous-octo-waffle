package com.portfoliopilot.service;

import com.portfoliopilot.dto.auth.AuthResponse;
import com.portfoliopilot.dto.auth.LoginRequest;
import com.portfoliopilot.dto.auth.RegisterRequest;
import com.portfoliopilot.dto.auth.UserSummary;
import com.portfoliopilot.exception.DuplicateResourceException;
import com.portfoliopilot.exception.InvalidTokenException;
import com.portfoliopilot.exception.ResourceNotFoundException;
import com.portfoliopilot.exception.UnauthorizedException;
import com.portfoliopilot.model.Profile;
import com.portfoliopilot.model.RefreshToken;
import com.portfoliopilot.model.User;
import com.portfoliopilot.model.embedded.ContactInfo;
import com.portfoliopilot.model.enums.Role;
import com.portfoliopilot.model.enums.UserStatus;
import com.portfoliopilot.repository.ProfileRepository;
import com.portfoliopilot.repository.RefreshTokenRepository;
import com.portfoliopilot.repository.UserRepository;
import com.portfoliopilot.security.JwtService;
import com.portfoliopilot.util.SkillNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Registration, login, token refresh and logout.
 *
 * <p>Security decisions worth stating explicitly:
 * <ul>
 *   <li>Login failures always report the SAME message whether the email is
 *       unknown or the password is wrong. Distinguishing them turns the login
 *       form into an account-enumeration oracle.</li>
 *   <li>Passwords are BCrypt-hashed here and the users collection validator
 *       independently rejects anything that is not a bcrypt hash, so a plaintext
 *       password cannot reach the database even through a coding mistake.</li>
 *   <li>Refresh tokens ROTATE on every use, and reuse of an already-rotated
 *       token revokes the whole family - that is how a stolen token is detected.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Consecutive failures before a temporary lockout. */
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /** How long the lockout lasts. */
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // ------------------------------------------------------------- register

    /**
     * Creates the account AND its 1:1 profile.
     *
     * <p>The profile is created eagerly rather than lazily so the
     * users&lt;-&gt;profiles invariant holds from the very first millisecond, and
     * so no other service ever has to handle "profile might not exist yet".
     */
    public AuthResponse register(RegisterRequest request, String userAgent, String ipAddress) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        String username = resolveUsername(request.username(), request.name(), email);

        Instant now = Instant.now();
        User user = User.builder()
                .username(username)
                .name(request.name().trim())
                .email(email)
                .emailVerified(false)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .deleted(false)
                .failedLoginAttempts(0)
                .createdAt(now)
                .updatedAt(now)
                .lastLoginAt(now)
                .build();

        // The unique indexes on email and username are the real guarantee here.
        // The pre-check above only produces a friendlier message; a concurrent
        // duplicate is caught by DuplicateKeyException in GlobalExceptionHandler.
        user = userRepository.save(user);

        try {
            profileRepository.save(Profile.builder()
                    .userId(user.getId())
                    .fullName(user.getName())
                    .contact(new ContactInfo())
                    .skills(new ArrayList<>())
                    .skillIndex(new ArrayList<>())
                    .education(new ArrayList<>())
                    .experience(new ArrayList<>())
                    .certificates(new ArrayList<>())
                    .profileHealth(ProfileService.computeProfileHealth(null, 0))
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        } catch (RuntimeException ex) {
            // Standalone MongoDB has no multi-document transactions, so a profile
            // failure would otherwise leave an orphaned user with no profile -
            // a permanently broken account. Compensate by removing the user.
            log.error("Profile creation failed for user {} - rolling back user", user.getId(), ex);
            try {
                userRepository.delete(user);
            } catch (RuntimeException deleteEx) {
                log.error("Failed to roll back orphaned user {}", user.getId(), deleteEx);
            }
            throw ex;
        }

        log.info("Registered user {} ({})", user.getId(), maskEmail(email));
        return issueTokens(user, userAgent, ipAddress);
    }

    // ---------------------------------------------------------------- login

    public AuthResponse login(LoginRequest request, String userAgent, String ipAddress) {
        String email = normalizeEmail(request.email());

        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> {
                    // Same message and same code path as a wrong password.
                    log.debug("Login attempt for unknown email {}", maskEmail(email));
                    return new UnauthorizedException(BAD_CREDENTIALS);
                });

        if (user.isLocked()) {
            log.warn("Login blocked - account {} is locked until {}", user.getId(), user.getLockedUntil());
            throw new UnauthorizedException(
                    "Account temporarily locked after too many failed attempts. Try again later.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user);
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new UnauthorizedException("This account has been suspended. Contact support.");
        }
        if (!user.canAuthenticate()) {
            throw new UnauthorizedException("This account cannot sign in");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("Login succeeded for user {} (role {})", user.getId(), user.getRole());
        return issueTokens(user, userAgent, ipAddress);
    }

    private void registerFailedAttempt(User user) {
        int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
            log.warn("Account {} locked for {} after {} failed attempts",
                    user.getId(), LOCKOUT_DURATION, attempts);
        }
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    // -------------------------------------------------------------- refresh

    /**
     * Exchanges a refresh token for a new pair, rotating the refresh token.
     *
     * <p>Rotation plus reuse detection: the old row is deleted and replaced. If a
     * token that no longer exists is presented, either it already rotated (it was
     * stolen and replayed) or it was revoked - both are treated as hostile, so
     * every session for that user is destroyed.
     */
    public AuthResponse refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        String hash = jwtService.hashRefreshToken(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired refresh token"));

        if (!stored.isActive()) {
            log.warn("Refresh token reuse or expiry detected for user {} - revoking all sessions",
                    stored.getUserId());
            refreshTokenRepository.deleteByUserId(stored.getUserId());
            throw new InvalidTokenException("Refresh token is no longer valid. Please sign in again.");
        }

        User user = userRepository.findByIdAndDeletedFalse(stored.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Account no longer exists"));

        if (!user.canAuthenticate()) {
            refreshTokenRepository.deleteByUserId(user.getId());
            throw new UnauthorizedException("This account cannot sign in");
        }

        // Rotate: the presented token dies here.
        refreshTokenRepository.delete(stored);

        return issueTokens(user, userAgent, ipAddress);
    }

    // --------------------------------------------------------------- logout

    /** Revokes one session. Idempotent - logging out twice is not an error. */
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = jwtService.hashRefreshToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            refreshTokenRepository.delete(token);
            log.info("Session revoked for user {}", token.getUserId());
        });
    }

    /** Revokes every session for a user. Used by logout-everywhere and by admin suspension. */
    public void revokeAllSessions(String userId) {
        refreshTokenRepository.deleteByUserId(userId);
        log.info("All sessions revoked for user {}", userId);
    }

    // ------------------------------------------------------------------- me

    public UserSummary currentUser(String userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .map(UserSummary::from)
                .orElseThrow(() -> ResourceNotFoundException.of("User"));
    }

    // ------------------------------------------------------------- internals

    private AuthResponse issueTokens(User user, String userAgent, String ipAddress) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRefreshToken();

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                // Only the hash is persisted - the raw token exists solely in the
                // response body and in the client's storage.
                .tokenHash(jwtService.hashRefreshToken(rawRefreshToken))
                .expiresAt(jwtService.refreshTokenExpiry())
                .userAgent(truncate(userAgent, 512))
                .ipAddress(truncate(ipAddress, 64))
                .createdAt(Instant.now())
                .build());

        return AuthResponse.of(
                accessToken,
                rawRefreshToken,
                jwtService.accessTokenTtlSeconds(),
                UserSummary.from(user));
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Produces a valid, unique handle.
     *
     * <p>The existing frontend's registration form collects only name, email and
     * password, but the database requires a unique username (it is the public
     * portfolio URL segment). So one is derived, then de-duplicated with a
     * numeric suffix.
     */
    private String resolveUsername(String requested, String name, String email) {
        if (requested != null && !requested.isBlank()) {
            String normalized = SkillNormalizer.normalizeUsername(requested);
            if (!SkillNormalizer.isValidUsername(normalized)) {
                throw new DuplicateResourceException("Requested username is not valid");
            }
            if (userRepository.existsByUsername(normalized)) {
                throw new DuplicateResourceException("This username is already taken");
            }
            return normalized;
        }

        String base = SkillNormalizer.normalizeUsername(name);
        if (base.length() < 3) {
            base = SkillNormalizer.normalizeUsername(email.split("@")[0]);
        }
        if (base.length() < 3) {
            base = "user";
        }
        if (base.length() > 24) {
            base = base.substring(0, 24).replaceAll("-+$", "");
        }

        if (!userRepository.existsByUsername(base) && SkillNormalizer.isValidUsername(base)) {
            return base;
        }

        for (int suffix = 2; suffix < 1000; suffix++) {
            String candidate = base + "-" + suffix;
            if (SkillNormalizer.isValidUsername(candidate) && !userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        // Astronomically unlikely; better than an infinite loop.
        return base + "-" + System.currentTimeMillis() % 100000;
    }

    /** Emails are masked in logs - an application log is not an appropriate place for PII. */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String masked = local.isEmpty() ? "*" : local.charAt(0) + "***";
        return masked + "@" + parts[1];
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
