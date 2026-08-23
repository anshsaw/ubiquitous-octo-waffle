package com.portfoliopilot.repository;

import com.portfoliopilot.model.SkillDictionaryEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code skillDictionary}.
 *
 * <p>Alias resolution runs for every skill token in every job description, so
 * {@code SkillDictionaryService} loads the whole (small) collection into memory
 * once rather than issuing one query per token.
 */
@Repository
public interface SkillDictionaryRepository extends MongoRepository<SkillDictionaryEntry, String> {

    List<SkillDictionaryEntry> findByActiveTrue();

    Optional<SkillDictionaryEntry> findByNormalizedName(String normalizedName);

    /** Alias lookup. Uses the multikey index {@code aliases_multikey}. */
    Optional<SkillDictionaryEntry> findByAliasesContaining(String alias);
}
