package com.portfoliopilot.service;

import com.portfoliopilot.model.SkillDictionaryEntry;
import com.portfoliopilot.model.enums.SkillCategory;
import com.portfoliopilot.repository.SkillDictionaryRepository;
import com.portfoliopilot.util.SkillNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves any user- or JD-supplied skill string to its canonical form, using
 * the {@code skillDictionary} collection as the alias table.
 *
 * <p>Resolution order (identical to the one documented in
 * {@code mongodb/lib/normalize.js}):
 * <ol>
 *   <li>{@link SkillNormalizer#normalizeSkill} -&gt; naive normalised form</li>
 *   <li>look up the dictionary by {@code normalizedName} OR {@code aliases}</li>
 *   <li>hit  -&gt; use the dictionary's canonical name and key</li>
 *   <li>miss -&gt; keep the naive form; the dictionary is a helper, not a gate</li>
 * </ol>
 *
 * <p>The whole collection is cached in memory. It holds tens of rows, is read on
 * every skill write and on every token of every job description, and changes
 * only when an admin edits it - so a per-token query would be pure waste.
 * Call {@link #refresh()} after mutating the collection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillDictionaryService {

    private final SkillDictionaryRepository repository;

    /** Immutable snapshot swapped atomically, so readers never see a half-built map. */
    private final AtomicReference<Map<String, SkillDictionaryEntry>> lookup =
            new AtomicReference<>(Map.of());

    /**
     * One canonical skill, after resolution.
     *
     * @param displayName    correctly-cased name for the UI
     * @param normalizedName canonical key used for all matching and analytics
     * @param category       for grouping; {@code OTHER} when unknown
     * @param weight         0..1 scoring importance
     * @param known          true when the dictionary recognised it
     */
    public record ResolvedSkill(
            String displayName,
            String normalizedName,
            SkillCategory category,
            double weight,
            boolean known
    ) {
    }

    /** Rebuilds the in-memory index. Called lazily on first use and after admin edits. */
    public synchronized void refresh() {
        List<SkillDictionaryEntry> entries = repository.findByActiveTrue();
        Map<String, SkillDictionaryEntry> map = new HashMap<>();

        for (SkillDictionaryEntry entry : entries) {
            if (entry.getNormalizedName() != null) {
                map.put(entry.getNormalizedName(), entry);
            }
            if (entry.getAliases() != null) {
                // putIfAbsent: a canonical name always wins over another entry's alias.
                entry.getAliases().forEach(alias -> map.putIfAbsent(alias, entry));
            }
        }

        lookup.set(Map.copyOf(map));
        log.info("Skill dictionary loaded: {} canonical skills, {} lookup keys", entries.size(), map.size());
    }

    private Map<String, SkillDictionaryEntry> index() {
        Map<String, SkillDictionaryEntry> current = lookup.get();
        if (current.isEmpty()) {
            refresh();
            current = lookup.get();
        }
        return current;
    }

    /**
     * Resolves a raw skill string. Never returns {@code null} - an unknown skill
     * is still a usable skill, it simply keeps its naive normalised form.
     */
    public ResolvedSkill resolve(String raw) {
        String naive = SkillNormalizer.normalizeSkill(raw);
        if (naive.isEmpty()) {
            return new ResolvedSkill("", "", SkillCategory.OTHER, 0.5, false);
        }

        SkillDictionaryEntry entry = index().get(naive);
        if (entry == null) {
            String display = raw == null ? naive : raw.trim();
            return new ResolvedSkill(display, naive, SkillCategory.OTHER, 0.5, false);
        }

        return new ResolvedSkill(
                entry.getCanonicalName(),
                entry.getNormalizedName(),
                entry.getCategory() == null ? SkillCategory.OTHER : entry.getCategory(),
                entry.getWeight() == null ? 0.7 : entry.getWeight(),
                true);
    }

    /** Canonical display name for an already-normalised key. Falls back to the key. */
    public String displayNameFor(String normalizedName) {
        SkillDictionaryEntry entry = index().get(normalizedName);
        return entry == null ? normalizedName : entry.getCanonicalName();
    }

    /**
     * Skills adjacent to the given one, e.g. {@code "spring boot" -> ["java", "spring"]}.
     * Used for partial-credit scoring and "learn this next" hints on skill gaps.
     */
    public List<String> relatedSkills(String normalizedName) {
        SkillDictionaryEntry entry = index().get(normalizedName);
        return entry == null || entry.getRelatedSkills() == null ? List.of() : entry.getRelatedSkills();
    }

    /** Every canonical key currently known. The skill extractor scans a JD for these. */
    public List<SkillDictionaryEntry> allActiveEntries() {
        return List.copyOf(new java.util.LinkedHashSet<>(index().values()));
    }

    public Optional<SkillDictionaryEntry> findByNormalizedName(String normalizedName) {
        return Optional.ofNullable(index().get(normalizedName));
    }
}
