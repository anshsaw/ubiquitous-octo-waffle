package com.portfoliopilot.repository;

import com.portfoliopilot.model.Portfolio;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** {@code portfolios}. */
@Repository
public interface PortfolioRepository extends MongoRepository<Portfolio, String> {

    List<Portfolio> findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(String userId);

    Optional<Portfolio> findByIdAndUserIdAndDeletedFalse(String id, String userId);

    /**
     * THE public route. One hit on the partial unique index
     * {@code uniq_published_username}. No authentication, no join.
     */
    Optional<Portfolio> findByUsernameAndPublishedTrueAndDeletedFalse(String username);

    /** The user's own currently-live portfolio, if any. */
    Optional<Portfolio> findByUserIdAndPublishedTrueAndDeletedFalse(String userId);

    /** "Has a portfolio already been adapted for this job?" */
    Optional<Portfolio> findBySourceJobAnalysisIdAndDeletedFalse(String sourceJobAnalysisId);

    /** Referential safety before an admin retires a template. */
    long countByTemplateIdAndDeletedFalse(String templateId);

    long countByPublishedTrueAndDeletedFalse();

    long countByUserIdAndDeletedFalse(String userId);

    List<Portfolio> findByUserIdAndDeletedFalse(String userId);
}
