'use strict';

/**
 * INDEX STRATEGY - PortfolioPilot AI
 * ==================================
 *
 * Every index below is declared with:
 *   why   -> the reason it exists
 *   query -> the concrete query/aggregation stage it optimises
 *
 * No index is created "just in case". Indexes are not free: each one is written
 * on every insert/update, consumes RAM in the WiredTiger cache, and on Atlas
 * M0/M2/M5 the working set is tiny. The set below is derived directly from the
 * screens in the app (login, dashboard, analyzer, public portfolio, admin).
 *
 * ESR rule (Equality, Sort, Range) is applied to every compound key.
 *
 * Run:  npm run indexes
 */

const { runScript } = require('../lib/db');

/** Retention for the admin audit trail. 2 years. Set to null to disable TTL. */
const ADMIN_LOG_TTL_SECONDS = 60 * 60 * 24 * 365 * 2;

/**
 * @typedef {Object} IndexSpec
 * @property {Record<string, 1|-1|'text'>} key
 * @property {import('mongodb').CreateIndexesOptions} [options]
 * @property {string} why
 * @property {string} query
 */

/** @type {Record<string, IndexSpec[]>} */
const INDEXES = {
  // ---------------------------------------------------------------------------
  // users
  // ---------------------------------------------------------------------------
  users: [
    {
      key: { email: 1 },
      options: { name: 'uniq_email', unique: true },
      why: 'Email is the login identifier and must be globally unique. The unique constraint is the only reliable defence against a duplicate-registration race - two concurrent POST /register requests both pass an application-level "does it exist" check.',
      query: 'db.users.findOne({ email: "student@example.com" })  // every login',
    },
    {
      key: { username: 1 },
      options: { name: 'uniq_username', unique: true },
      why: 'Username is a public URL segment (/portfolio/:username). Duplicates would make the public route ambiguous.',
      query: 'db.users.findOne({ username: "demo-student" })  // availability check at registration',
    },
    {
      key: { role: 1, status: 1, createdAt: -1 },
      options: { name: 'role_status_createdAt' },
      why: 'Admin > Users table. ESR: equality on role + status, then sort on createdAt. Also serves role-only filters via the index prefix.',
      query: 'db.users.find({ role: "USER", status: "ACTIVE" }).sort({ createdAt: -1 }).limit(25)',
    },
    {
      key: { status: 1, createdAt: -1 },
      options: { name: 'status_createdAt' },
      why: 'The status-only filter is not a prefix of role_status_createdAt, so it would otherwise need a collection scan. Admins filter by "SUSPENDED" far more often than by role.',
      query: 'db.users.find({ status: "SUSPENDED" }).sort({ createdAt: -1 })',
    },
    {
      key: { createdAt: 1 },
      options: { name: 'createdAt' },
      why: 'Signup-trend aggregation does a date-range $match before grouping. Without this the pipeline scans the entire users collection on every admin dashboard load.',
      query: 'db.users.aggregate([{ $match: { createdAt: { $gte: <30d ago> } } }, { $group: ... }])',
    },
    {
      key: { name: 'text', email: 'text', username: 'text' },
      options: {
        name: 'user_search_text',
        weights: { name: 5, username: 3, email: 1 },
        default_language: 'english',
      },
      why: 'Admin > Users free-text search. The alternative - an unanchored /regex/i - can never use a b-tree index and degrades linearly with user count. MongoDB permits only ONE text index per collection, so all three searchable fields are combined here and weighted.',
      query: 'db.users.find({ $text: { $search: "priya" } }, { score: { $meta: "textScore" } })',
    },
  ],

  // ---------------------------------------------------------------------------
  // profiles
  // ---------------------------------------------------------------------------
  profiles: [
    {
      key: { userId: 1 },
      options: { name: 'uniq_userId', unique: true },
      why: 'Enforces the 1:1 users<->profiles relationship at the database level, and serves the single hottest authenticated read in the app.',
      query: 'db.profiles.findOne({ userId: ObjectId(...) })  // /profile, /analyzer, /builder, public portfolio',
    },
    {
      key: { skillIndex: 1 },
      options: { name: 'skillIndex_multikey' },
      why: 'Multikey index over the derived normalised-skill array. Answers "which users already have skill X" for admin analytics and for skill-gap benchmarking, without unwinding the skills array of every profile.',
      query: 'db.profiles.countDocuments({ skillIndex: "docker" })',
    },
    {
      key: { updatedAt: -1 },
      options: { name: 'updatedAt' },
      why: 'Admin "recently active profiles" listing and incremental export/backfill jobs (re-normalise skills, recompute profileHealth) that must page through profiles in change order.',
      query: 'db.profiles.find({ updatedAt: { $gte: <cursor> } }).sort({ updatedAt: -1 })',
    },
  ],

  // ---------------------------------------------------------------------------
  // projects
  // ---------------------------------------------------------------------------
  projects: [
    {
      key: { userId: 1, deleted: 1, createdAt: -1 },
      options: { name: 'userId_deleted_createdAt' },
      why: 'The /projects grid. ESR: equality on userId + deleted, sort on createdAt. Every project read is scoped by userId anyway (ownership enforcement), so userId is the correct leading field.',
      query: 'db.projects.find({ userId: ObjectId(...), deleted: false }).sort({ createdAt: -1 })',
    },
    {
      key: { userId: 1, includeInPortfolio: 1, deleted: 1 },
      options: { name: 'userId_includeInPortfolio_deleted' },
      why: 'Portfolio and public-page rendering only ever selects the opted-in, non-deleted subset. Keeping it a covered equality triple avoids fetching (and then discarding) private projects.',
      query: 'db.projects.find({ userId: ObjectId(...), includeInPortfolio: true, deleted: false })',
    },
    {
      key: { userId: 1, techStackNormalized: 1 },
      options: { name: 'userId_techStack_multikey' },
      why: 'The Opportunity Analyzer intersects the skills extracted from a job description with the user\'s project tech stacks. This index turns that into an indexed $in over one user\'s projects instead of loading them all into the JVM.',
      query: 'db.projects.find({ userId: ObjectId(...), techStackNormalized: { $in: ["java","spring boot"] } })',
    },
    {
      key: { createdAt: -1 },
      options: { name: 'createdAt' },
      why: 'Admin reports ("projects created this week") and cross-user analytics. Not used by any user-facing screen; justified solely by the admin Reports/Analytics page.',
      query: 'db.projects.countDocuments({ createdAt: { $gte: <7d ago> } })',
    },
  ],

  // ---------------------------------------------------------------------------
  // jobAnalyses
  // ---------------------------------------------------------------------------
  jobAnalyses: [
    {
      key: { userId: 1, deleted: 1, createdAt: -1 },
      options: { name: 'userId_deleted_createdAt' },
      why: 'The "Recent Analyses" list on /dashboard - the second-hottest authenticated query. Also backs pagination of a user\'s analysis history.',
      query: 'db.jobAnalyses.find({ userId: ObjectId(...), deleted: false }).sort({ createdAt: -1 }).limit(5)',
    },
    {
      key: { createdAt: -1 },
      options: { name: 'createdAt' },
      why: 'Admin > Job Analyses Log (global, newest first) AND the "jobs analyzed today" stat card. Both are pure date-range operations over the whole collection - by far the fastest-growing collection in the system.',
      query: 'db.jobAnalyses.countDocuments({ createdAt: { $gte: <today 00:00> } })',
    },
    {
      key: { 'job.normalizedTitle': 1, createdAt: -1 },
      options: { name: 'jobTitle_createdAt' },
      why: 'Powers "most analysed job roles". Grouping on the pre-normalised title lets the $group run off an index scan instead of computing $toLower over every document at query time.',
      query: 'db.jobAnalyses.aggregate([{ $match: { createdAt: {...} } }, { $group: { _id: "$job.normalizedTitle", count: { $sum: 1 } } }])',
    },
    {
      key: { userId: 1, 'analysis.matchScore': -1 },
      options: { name: 'userId_matchScore' },
      why: 'The "your best-matching opportunities" ranking, and the per-user average-match-score card. Sorting by score without this index requires an in-memory sort that fails past the 100MB limit for heavy users.',
      query: 'db.jobAnalyses.find({ userId: ObjectId(...) }).sort({ "analysis.matchScore": -1 }).limit(10)',
    },
    {
      key: { 'analysis.skillGapsNormalized': 1 },
      options: { name: 'skillGaps_multikey' },
      why: 'Drill-down from the admin "Top skill gaps" bar chart: clicking a bar lists the analyses (and therefore users) missing that skill. NOTE: the chart aggregation itself is a $unwind over a date window and is served by the createdAt index - this multikey index exists for the targeted lookup, not the chart.',
      query: 'db.jobAnalyses.find({ "analysis.skillGapsNormalized": "docker" })',
    },
    {
      key: { 'job.title': 'text', 'job.company': 'text' },
      options: {
        name: 'job_search_text',
        weights: { 'job.title': 5, 'job.company': 2 },
        default_language: 'english',
      },
      why: 'Search box on the admin Job Analyses Log. job.description is deliberately EXCLUDED: indexing 30k-character raw postings would inflate the index by orders of magnitude for negligible admin value.',
      query: 'db.jobAnalyses.find({ $text: { $search: "backend developer" } })',
    },
  ],

  // ---------------------------------------------------------------------------
  // resumes
  // ---------------------------------------------------------------------------
  resumes: [
    {
      key: { userId: 1, deleted: 1, createdAt: -1 },
      options: { name: 'userId_deleted_createdAt' },
      why: 'The user\'s "my generated resumes" list, plus the ownership check on every download request.',
      query: 'db.resumes.find({ userId: ObjectId(...), deleted: false }).sort({ createdAt: -1 })',
    },
    {
      key: { jobAnalysisId: 1, createdAt: -1 },
      options: { name: 'jobAnalysisId_createdAt' },
      why: 'Answers "which resume(s) were generated for this job analysis" when /match-analysis loads. NOT unique on purpose: users legitimately regenerate a resume after editing their profile, and keeping the older versions is valuable history. Add `unique: true` only if you decide to enforce one-resume-per-analysis.',
      query: 'db.resumes.find({ jobAnalysisId: ObjectId(...) }).sort({ createdAt: -1 }).limit(1)',
    },
  ],

  // ---------------------------------------------------------------------------
  // portfolios
  // ---------------------------------------------------------------------------
  portfolios: [
    {
      key: { username: 1 },
      options: {
        name: 'uniq_published_username',
        unique: true,
        partialFilterExpression: { isPublished: true, deleted: false },
      },
      why: 'THE most important index in the schema. A PARTIAL unique index expresses the exact business rule: a user may keep many drafts and many job-specific portfolios, but at most ONE may be live at /portfolio/:username. A plain unique index would wrongly forbid drafts; an application-level check would lose a concurrent double-publish race.',
      query: 'db.portfolios.findOne({ username: "demo-student", isPublished: true, deleted: false })  // public page, no auth',
    },
    {
      key: { userId: 1, deleted: 1, updatedAt: -1 },
      options: { name: 'userId_deleted_updatedAt' },
      why: 'The user\'s portfolio list in /builder, most-recently-edited first.',
      query: 'db.portfolios.find({ userId: ObjectId(...), deleted: false }).sort({ updatedAt: -1 })',
    },
    {
      key: { isPublished: 1, publishedAt: -1 },
      options: { name: 'isPublished_publishedAt' },
      why: 'The "portfolios published" admin stat card and the recently-published feed. A low-cardinality leading field is acceptable here because the follow-on sort on publishedAt does the selective work.',
      query: 'db.portfolios.countDocuments({ isPublished: true, deleted: false })',
    },
    {
      key: { sourceJobAnalysisId: 1 },
      options: {
        name: 'sourceJobAnalysisId_sparse',
        sparse: true,
      },
      why: 'Resolves "has a portfolio already been adapted for this job?" from /match-analysis. SPARSE because most portfolios are generic and store null here - a sparse index skips them and stays small.',
      query: 'db.portfolios.findOne({ sourceJobAnalysisId: ObjectId(...) })',
    },
    {
      key: { templateId: 1 },
      options: { name: 'templateId' },
      why: 'Referential safety: before an admin deactivates or deletes a template, the backend must count portfolios still using it. Also feeds the "template popularity" admin chart.',
      query: 'db.portfolios.countDocuments({ templateId: ObjectId(...), deleted: false })',
    },
  ],

  // ---------------------------------------------------------------------------
  // portfolioTemplates
  // ---------------------------------------------------------------------------
  portfolioTemplates: [
    {
      key: { templateKey: 1 },
      options: { name: 'uniq_templateKey', unique: true },
      why: 'templateKey maps 1:1 to a React component. A duplicate would make rendering non-deterministic.',
      query: 'db.portfolioTemplates.findOne({ templateKey: "MODERN_DEV" })',
    },
    {
      key: { active: 1, sortOrder: 1 },
      options: { name: 'active_sortOrder' },
      why: 'The /builder template picker fetches active templates in display order. Tiny collection, but this makes the query fully covered and index-sorted.',
      query: 'db.portfolioTemplates.find({ active: true }).sort({ sortOrder: 1 })',
    },
  ],

  // ---------------------------------------------------------------------------
  // skillDictionary
  // ---------------------------------------------------------------------------
  skillDictionary: [
    {
      key: { normalizedName: 1 },
      options: { name: 'uniq_normalizedName', unique: true },
      why: 'The canonical key must be unique - two dictionary rows claiming "react" would make normalisation non-deterministic.',
      query: 'db.skillDictionary.findOne({ normalizedName: "react" })',
    },
    {
      key: { aliases: 1 },
      options: { name: 'aliases_multikey' },
      why: 'Alias -> canonical resolution runs for EVERY skill token extracted from EVERY job description. Without a multikey index this becomes a full collection scan per token.',
      query: 'db.skillDictionary.findOne({ aliases: "react js" })',
    },
    {
      key: { category: 1, active: 1 },
      options: { name: 'category_active' },
      why: 'Grouped skill pickers in the UI ("choose from FRONTEND skills") and category breakdowns in admin analytics.',
      query: 'db.skillDictionary.find({ category: "FRONTEND", active: true })',
    },
  ],

  // ---------------------------------------------------------------------------
  // adminLogs
  // ---------------------------------------------------------------------------
  adminLogs: [
    {
      key: { createdAt: 1 },
      options: {
        name: 'createdAt_ttl',
        ...(ADMIN_LOG_TTL_SECONDS ? { expireAfterSeconds: ADMIN_LOG_TTL_SECONDS } : {}),
      },
      why: 'Serves the newest-first audit feed AND bounds growth via TTL. A single-field ascending index also satisfies a descending sort (MongoDB walks it backwards), so one index does both jobs. WARNING: TTL means automatic deletion - if your jurisdiction requires longer audit retention, set ADMIN_LOG_TTL_SECONDS to null and archive externally instead.',
      query: 'db.adminLogs.find({}).sort({ createdAt: -1 }).limit(50)',
    },
    {
      key: { adminId: 1, createdAt: -1 },
      options: { name: 'adminId_createdAt' },
      why: '"What did this administrator do?" - the primary accountability query during an incident review.',
      query: 'db.adminLogs.find({ adminId: ObjectId(...) }).sort({ createdAt: -1 })',
    },
    {
      key: { targetUserId: 1, createdAt: -1 },
      options: { name: 'targetUserId_createdAt', sparse: true },
      why: '"What was done TO this user?" - shown on the admin user-detail page. Sparse because template/portfolio actions leave targetUserId null.',
      query: 'db.adminLogs.find({ targetUserId: ObjectId(...) }).sort({ createdAt: -1 })',
    },
  ],

  // ---------------------------------------------------------------------------
  // refreshTokens
  // ---------------------------------------------------------------------------
  refreshTokens: [
    {
      key: { tokenHash: 1 },
      options: { name: 'uniq_tokenHash', unique: true },
      why: 'Token lookup happens on every silent refresh; uniqueness also makes token-reuse detection reliable.',
      query: 'db.refreshTokens.findOne({ tokenHash: "<sha256>" })',
    },
    {
      key: { userId: 1, createdAt: -1 },
      options: { name: 'userId_createdAt' },
      why: 'Bulk revocation: "log out everywhere", and forced invalidation when an admin suspends or deletes an account.',
      query: 'db.refreshTokens.updateMany({ userId: ObjectId(...) }, { $set: { revokedAt: new Date() } })',
    },
    {
      key: { expiresAt: 1 },
      options: { name: 'expiresAt_ttl', expireAfterSeconds: 0 },
      why: 'TTL with expireAfterSeconds: 0 makes MongoDB delete each row at its own expiresAt timestamp. Session cleanup becomes a database responsibility - no cron job, no unbounded growth.',
      query: '(background) mongod TTL monitor removes documents where expiresAt <= now',
    },
  ],
};

/**
 * Creates every declared index. Idempotent.
 *
 * `createIndex` is a no-op when an identical index already exists. When the
 * NAME matches but the definition changed (conflict codes 85/86), the old index
 * is dropped and recreated, so editing this file and re-running is always safe.
 */
async function createIndexes(db) {
  let created = 0;
  let recreated = 0;

  for (const [collection, specs] of Object.entries(INDEXES)) {
    for (const spec of specs) {
      const options = { ...(spec.options || {}) };
      try {
        await db.collection(collection).createIndex(spec.key, options);
        created += 1;
        console.log(`  ok       ${collection}.${options.name}`);
      } catch (err) {
        // 85 IndexOptionsConflict | 86 IndexKeySpecsConflict
        if (err.code === 85 || err.code === 86) {
          await db.collection(collection).dropIndex(options.name);
          await db.collection(collection).createIndex(spec.key, options);
          recreated += 1;
          console.log(`  rebuilt  ${collection}.${options.name}`);
        } else {
          throw new Error(`${collection}.${options.name}: ${err.message}`);
        }
      }
    }
  }

  console.log(`\n  ${created} indexes ensured, ${recreated} rebuilt.`);
}

/** Prints the live index inventory, for verification / documentation. */
async function reportIndexes(db) {
  for (const collection of Object.keys(INDEXES)) {
    const live = await db.collection(collection).indexes();
    console.log(`\n  ${collection}`);
    for (const idx of live) {
      const flags = [
        idx.unique ? 'unique' : null,
        idx.sparse ? 'sparse' : null,
        idx.partialFilterExpression ? 'partial' : null,
        idx.expireAfterSeconds !== undefined ? `ttl=${idx.expireAfterSeconds}s` : null,
      ]
        .filter(Boolean)
        .join(',');
      console.log(`    - ${idx.name.padEnd(34)} ${JSON.stringify(idx.key)}${flags ? `  [${flags}]` : ''}`);
    }
  }
}

if (require.main === module) {
  runScript('indexes', async (db) => {
    await createIndexes(db);
    await reportIndexes(db);
  });
}

module.exports = { INDEXES, createIndexes, reportIndexes, ADMIN_LOG_TTL_SECONDS };
