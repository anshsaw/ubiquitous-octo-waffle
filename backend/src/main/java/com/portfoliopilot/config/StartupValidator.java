package com.portfoliopilot.config;

import com.mongodb.client.MongoDatabase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fail-fast and loud-warning checks at startup.
 *
 * <p>Every problem caught here is one that would otherwise surface as a
 * confusing runtime failure much later: a scoring formula that silently does not
 * sum to 100%, a production deployment running the placeholder JWT secret, or a
 * database that was never provisioned by {@code mongodb/scripts/setup.js}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupValidator implements ApplicationRunner {

    /** Collections the /mongodb package is expected to have created. */
    private static final List<String> REQUIRED_COLLECTIONS = List.of(
            "users", "profiles", "projects", "jobAnalyses",
            "resumes", "portfolios", "portfolioTemplates");

    private final JwtProperties jwtProperties;
    private final MatchingProperties matchingProperties;
    private final CorsProperties corsProperties;
    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        validateMatchingWeights();
        warnOnDevelopmentSecret();
        logCorsOrigins();
        checkDatabaseProvisioned();
    }

    /**
     * The three weights must sum to 1.0, otherwise the "overall match score" is
     * not a percentage and every stored score becomes incomparable.
     */
    private void validateMatchingWeights() {
        double sum = matchingProperties.weightSum();
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new IllegalStateException(
                    "app.matching weights must sum to 1.0 but sum to %.3f (skills=%.2f, projects=%.2f, requirements=%.2f)"
                            .formatted(sum,
                                    matchingProperties.skillsWeight(),
                                    matchingProperties.projectsWeight(),
                                    matchingProperties.requirementsWeight()));
        }
        log.info("Match weights: skills={} projects={} requirements={}",
                matchingProperties.skillsWeight(),
                matchingProperties.projectsWeight(),
                matchingProperties.requirementsWeight());
    }

    private void warnOnDevelopmentSecret() {
        if (jwtProperties.isDevelopmentSecret()) {
            String profiles = System.getenv("SPRING_PROFILES_ACTIVE");
            boolean isProd = profiles != null && profiles.toLowerCase().contains("prod");
            if (isProd) {
                throw new IllegalStateException(
                        "JWT_SECRET is the development placeholder but SPRING_PROFILES_ACTIVE contains 'prod'. "
                                + "Set a real JWT_SECRET (openssl rand -base64 48) before running in production.");
            }
            log.warn("""

                    ****************************************************************
                    *  JWT_SECRET is the built-in DEVELOPMENT placeholder.          *
                    *  Every token this instance issues can be forged by anyone     *
                    *  who has read the source. Set a real JWT_SECRET before this   *
                    *  is reachable by anyone but you:                              *
                    *      openssl rand -base64 48                                  *
                    ****************************************************************
                    """);
        }
    }

    private void logCorsOrigins() {
        List<String> origins = corsProperties.originList();
        if (origins.isEmpty()) {
            log.warn("No CORS origins configured (FRONTEND_URL is empty). Browser requests will be blocked.");
        } else {
            log.info("CORS allowed origins: {}", origins);
        }
    }

    /**
     * Verifies the database was provisioned. This backend does NOT create
     * collections or indexes - that is owned by {@code mongodb/scripts/setup.js},
     * where every index carries a documented rationale.
     */
    private void checkDatabaseProvisioned() {
        try {
            MongoDatabase database = mongoTemplate.getDb();
            List<String> existing = new ArrayList<>();
            database.listCollectionNames().forEach(existing::add);

            List<String> missing = REQUIRED_COLLECTIONS.stream()
                    .filter(name -> !existing.contains(name))
                    .toList();

            if (!missing.isEmpty()) {
                log.warn("""

                        ****************************************************************
                        *  MongoDB is missing collections: {}
                        *  The database has not been provisioned. Run:                  *
                        *      cd mongodb && npm run setup     (structure only)         *
                        *      cd mongodb && npm run seed      (structure + demo data)  *
                        ****************************************************************
                        """, missing);
            } else {
                Document stats = database.runCommand(new Document("dbStats", 1));
                log.info("MongoDB '{}' ready - {} collections, {} objects",
                        database.getName(),
                        stats.get("collections"),
                        stats.get("objects"));
            }
        } catch (RuntimeException ex) {
            log.error("Could not reach MongoDB at startup: {}", ex.getMessage());
        }
    }
}
