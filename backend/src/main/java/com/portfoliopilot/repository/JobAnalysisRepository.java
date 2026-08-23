package com.portfoliopilot.repository;

import com.portfoliopilot.model.JobAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** {@code jobAnalyses} - the fastest-growing collection, so every finder is index-backed. */
@Repository
public interface JobAnalysisRepository extends MongoRepository<JobAnalysis, String> {

    /** Dashboard "Recent Analyses". Uses index {@code userId_deleted_createdAt}. */
    List<JobAnalysis> findTop5ByUserIdAndDeletedFalseOrderByCreatedAtDesc(String userId);

    Page<JobAnalysis> findByUserIdAndDeletedFalse(String userId, Pageable pageable);

    /** Ownership-safe single fetch for /match-analysis. */
    Optional<JobAnalysis> findByIdAndUserIdAndDeletedFalse(String id, String userId);

    long countByUserIdAndDeletedFalse(String userId);

    /** Admin "jobs analyzed today" stat card. Pure indexed range scan on {@code createdAt}. */
    long countByCreatedAtBetweenAndDeletedFalse(Instant from, Instant to);

    long countByDeletedFalse();
}
