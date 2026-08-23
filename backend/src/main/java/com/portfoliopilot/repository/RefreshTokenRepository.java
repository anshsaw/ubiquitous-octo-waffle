package com.portfoliopilot.repository;

import com.portfoliopilot.model.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@code refreshTokens}.
 *
 * <p>Lookups are always by {@code tokenHash} - the raw token never reaches the
 * database. Expired rows are removed by the TTL index, so no cleanup query
 * exists here.
 */
@Repository
public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Bulk revocation: "log out everywhere", and forced logout on suspension. */
    void deleteByUserId(String userId);

    long countByUserId(String userId);
}
