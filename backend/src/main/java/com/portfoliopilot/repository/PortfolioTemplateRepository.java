package com.portfoliopilot.repository;

import com.portfoliopilot.model.PortfolioTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** {@code portfolioTemplates}. Tiny and read-heavy - an ideal cache candidate. */
@Repository
public interface PortfolioTemplateRepository extends MongoRepository<PortfolioTemplate, String> {

    /** The /builder picker. Uses index {@code active_sortOrder}. */
    List<PortfolioTemplate> findByActiveTrueOrderBySortOrderAsc();

    List<PortfolioTemplate> findAllByOrderBySortOrderAsc();

    Optional<PortfolioTemplate> findByTemplateKey(String templateKey);

    boolean existsByTemplateKey(String templateKey);
}
