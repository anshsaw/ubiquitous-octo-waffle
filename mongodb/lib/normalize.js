'use strict';

/**
 * Skill / text normalisation rules.
 *
 * WHY THIS LIVES IN THE DATABASE LAYER
 * ------------------------------------
 * Match scoring, skill-gap analytics and duplicate prevention all compare skill
 * strings. If "React", "React.js" and "ReactJS" are stored as three different
 * values, every aggregation in `aggregations/skillGaps.js` produces garbage.
 *
 * So every skill string is persisted TWICE:
 *   - `name`           -> exactly what the user typed  (display value)
 *   - `normalizedName` -> the canonical key            (query/analytics value)
 *
 * Indexes and aggregations only ever touch `normalizedName`.
 *
 * Spring Boot integration point
 * -----------------------------
 * Port `normalizeSkill()` verbatim into a Java utility (e.g.
 * `SkillNormalizer.normalize(String)`) and call it in the service layer BEFORE
 * every write. The algorithm must stay byte-for-byte identical to what is used
 * here, otherwise seeded and runtime data will not join.
 *
 * Resolution order used by the backend:
 *   1. `normalizeSkill(input)`                      -> naive normalised form
 *   2. look up `skillDictionary` by `normalizedName` OR `aliases`
 *   3. if found -> use the dictionary's `canonicalName` / `normalizedName`
 *   4. if not found -> keep the naive form (dictionary is a helper, not a gate)
 */

/**
 * Lowercase, strip punctuation noise, collapse whitespace.
 *
 *   "  React.js  "     -> "react js"   -> alias hit  -> "react"
 *   "Spring-Boot"      -> "spring boot"
 *   "C++"              -> "c++"        (++ deliberately preserved)
 *   "Node.JS"          -> "node js"    -> alias hit  -> "nodejs"
 *
 * @param {string} raw
 * @returns {string}
 */
function normalizeSkill(raw) {
  if (raw === null || raw === undefined) return '';

  return String(raw)
    .normalize('NFKD')            // fold accents/compatibility forms
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim()
    .replace(/[._/\\|]+/g, ' ')   // "react.js" / "ci/cd" -> spaced tokens
    .replace(/[^a-z0-9+#\- ]+/g, '') // keep c++, c#, objective-c
    .replace(/\s*-\s*/g, ' ')     // "spring-boot" -> "spring boot"
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Normalises a URL-facing portfolio/user handle.
 * Must satisfy the `^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$` pattern in the validators.
 *
 * @param {string} raw
 * @returns {string}
 */
function normalizeUsername(raw) {
  return String(raw || '')
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9-]+/g, '-')
    .replace(/-{2,}/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 30);
}

/**
 * Deduplicates a skill array by `normalizedName`, keeping the entry with the
 * highest proficiency. Used by the seed script; mirror it in the Spring Boot
 * service that handles `PUT /api/profile/skills`.
 *
 * @param {Array<{name: string, normalizedName?: string, proficiency?: string, yearsOfExperience?: number}>} skills
 */
function dedupeSkills(skills) {
  const RANK = { BEGINNER: 1, INTERMEDIATE: 2, ADVANCED: 3, EXPERT: 4 };
  const byKey = new Map();

  for (const skill of skills || []) {
    const normalizedName = skill.normalizedName || normalizeSkill(skill.name);
    if (!normalizedName) continue;

    const candidate = { ...skill, normalizedName };
    const existing = byKey.get(normalizedName);

    if (!existing || (RANK[candidate.proficiency] || 0) > (RANK[existing.proficiency] || 0)) {
      byKey.set(normalizedName, candidate);
    }
  }

  return [...byKey.values()];
}

/**
 * Builds the denormalised `skillIndex` array stored on `profiles`.
 *
 * This array exists so that:
 *   a) `uniqueItems: true` in the validator enforces "no duplicate skill per
 *      profile" AT THE DATABASE LEVEL (JSON Schema cannot express uniqueness on
 *      one field of an array of objects);
 *   b) a single multikey index on `skillIndex` answers "which users have skill
 *      X" without unwinding `skills`.
 *
 * It MUST be rewritten on every skills mutation.
 */
function buildSkillIndex(skills) {
  return [...new Set((skills || []).map((s) => s.normalizedName || normalizeSkill(s.name)).filter(Boolean))];
}

/** Same idea as `buildSkillIndex`, for `projects.techStack`. */
function buildTechStackNormalized(techStack) {
  return [...new Set((techStack || []).map(normalizeSkill).filter(Boolean))];
}

/**
 * Normalises a job title for the "most analysed roles" aggregation.
 * "Senior Java Backend Developer (Remote)" -> "senior java backend developer"
 */
function normalizeJobTitle(raw) {
  return String(raw || '')
    .toLowerCase()
    .replace(/\(.*?\)/g, ' ')
    .replace(/[^a-z0-9+#\- ]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

module.exports = {
  normalizeSkill,
  normalizeUsername,
  normalizeJobTitle,
  dedupeSkills,
  buildSkillIndex,
  buildTechStackNormalized,
};
