package com.portfoliopilot.repository;

import com.portfoliopilot.model.Resume;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** {@code resumes}. */
@Repository
public interface ResumeRepository extends MongoRepository<Resume, String> {

    Page<Resume> findByUserIdAndDeletedFalse(String userId, Pageable pageable);

    List<Resume> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(String userId);

    Optional<Resume> findByIdAndUserIdAndDeletedFalse(String id, String userId);

    /**
     * "Which resume was generated for this analysis?"
     *
     * <p>Returns the newest. NOT unique on purpose: regenerating after a profile
     * edit is a legitimate action and the older versions are useful history.
     */
    Optional<Resume> findFirstByJobAnalysisIdAndDeletedFalseOrderByCreatedAtDesc(String jobAnalysisId);

    long countByUserIdAndDeletedFalse(String userId);

    long countByDeletedFalse();
}
