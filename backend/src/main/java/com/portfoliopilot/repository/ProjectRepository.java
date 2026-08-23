package com.portfoliopilot.repository;

import com.portfoliopilot.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code projects}.
 *
 * <p>Note that EVERY finder is scoped by {@code userId}. That is not a
 * convenience - it is the ownership boundary. There is deliberately no
 * {@code findById(String)} usage anywhere in the services: fetching a project by
 * id alone is the classic IDOR hole, so the repository only exposes the safe
 * {@code findByIdAndUserIdAndDeletedFalse} form.
 */
@Repository
public interface ProjectRepository extends MongoRepository<Project, String> {

    /** The /projects grid. Uses index {@code userId_deleted_createdAt}. */
    Page<Project> findByUserIdAndDeletedFalse(String userId, Pageable pageable);

    List<Project> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(String userId);

    /** Ownership-safe single fetch. */
    Optional<Project> findByIdAndUserIdAndDeletedFalse(String id, String userId);

    /** Portfolio and public-page rendering: only the opted-in, non-deleted subset. */
    List<Project> findByUserIdAndIncludeInPortfolioTrueAndDeletedFalse(String userId);

    /** Resolve an explicit ordering list without losing the ownership filter. */
    List<Project> findByUserIdAndIdInAndDeletedFalse(String userId, Collection<String> ids);

    long countByUserIdAndDeletedFalse(String userId);

    long countByDeletedFalse();
}
