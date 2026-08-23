'use strict';

/**
 * ADMIN DASHBOARD - stat cards
 * ============================
 *
 * Backs: total users | portfolios published | jobs analyzed today | avg match score
 *
 * Every function returns a PLAIN PIPELINE ARRAY so it can be:
 *   - executed here (npm run stats), and
 *   - transliterated 1:1 into Spring Data's `Aggregation.newAggregation(...)`
 *     or handed to `MongoTemplate.aggregate(..., Document.class)` as raw BSON.
 *
 * Indexing note: each pipeline starts with a `$match` on an indexed field so the
 * planner can bound the scan. Never reorder those stages.
 */

const { startOfToday, startOfTomorrow, daysAgo } = require('../lib/dates');

/**
 * Total non-deleted end users (admins excluded - they are staff, not customers).
 * Uses: users.role_status_createdAt (index prefix `role`).
 */
function totalUsersPipeline() {
  return [
    { $match: { role: 'USER', deleted: false } },
    { $count: 'value' },
  ];
}

/** Breakdown by status for the ACTIVE / SUSPENDED sub-labels on the card. */
function usersByStatusPipeline() {
  return [
    { $match: { role: 'USER', deleted: false } },
    { $group: { _id: '$status', value: { $sum: 1 } } },
    { $project: { _id: 0, status: '$_id', value: 1 } },
    { $sort: { status: 1 } },
  ];
}

/**
 * Portfolios currently live on a public URL.
 * Uses: portfolios.isPublished_publishedAt.
 */
function publishedPortfoliosPipeline() {
  return [
    { $match: { isPublished: true, deleted: false } },
    { $count: 'value' },
  ];
}

/**
 * Job analyses created today (local midnight to local midnight).
 * Uses: jobAnalyses.createdAt - a pure indexed range scan.
 *
 * @param {number} offsetMinutes UTC offset of the reporting timezone
 */
function jobsAnalyzedTodayPipeline(offsetMinutes = 0) {
  return [
    {
      $match: {
        deleted: false,
        createdAt: { $gte: startOfToday(offsetMinutes), $lt: startOfTomorrow(offsetMinutes) },
      },
    },
    { $count: 'value' },
  ];
}

/**
 * Average overall match score across a rolling window.
 *
 * `$avg` ignores missing values, but the validator already guarantees
 * `analysis.matchScore` is an int in 0..100, so the mean is always meaningful.
 * Rounded server-side so the API returns a display-ready integer.
 *
 * @param {number|null} days window size; null = all time
 */
function averageMatchScorePipeline(days = 30, offsetMinutes = 0) {
  const match = { deleted: false };
  if (days) match.createdAt = { $gte: daysAgo(days, offsetMinutes) };

  return [
    { $match: match },
    {
      $group: {
        _id: null,
        value: { $avg: '$analysis.matchScore' },
        samples: { $sum: 1 },
        best: { $max: '$analysis.matchScore' },
        worst: { $min: '$analysis.matchScore' },
      },
    },
    {
      $project: {
        _id: 0,
        value: { $round: [{ $ifNull: ['$value', 0] }, 0] },
        samples: 1,
        best: 1,
        worst: 1,
      },
    },
  ];
}

/**
 * Average of the three sub-scores - feeds the admin radar/bar comparison.
 */
function averageSubScoresPipeline(days = 30, offsetMinutes = 0) {
  const match = { deleted: false };
  if (days) match.createdAt = { $gte: daysAgo(days, offsetMinutes) };

  return [
    { $match: match },
    {
      $group: {
        _id: null,
        skillsMatch: { $avg: '$analysis.skillsMatch' },
        projectsMatch: { $avg: '$analysis.projectsMatch' },
        requirementsMatch: { $avg: '$analysis.requirementsMatch' },
      },
    },
    {
      $project: {
        _id: 0,
        skillsMatch: { $round: [{ $ifNull: ['$skillsMatch', 0] }, 0] },
        projectsMatch: { $round: [{ $ifNull: ['$projectsMatch', 0] }, 0] },
        requirementsMatch: { $round: [{ $ifNull: ['$requirementsMatch', 0] }, 0] },
      },
    },
  ];
}

/**
 * Distribution of match scores into buckets - "how well are users matching?".
 * `$bucket` requires sorted, exhaustive boundaries; `default` catches nothing
 * here because the validator caps the value at 100, but it is kept as a guard.
 */
function matchScoreDistributionPipeline(days = 30, offsetMinutes = 0) {
  const match = { deleted: false };
  if (days) match.createdAt = { $gte: daysAgo(days, offsetMinutes) };

  return [
    { $match: match },
    {
      $bucket: {
        groupBy: '$analysis.matchScore',
        boundaries: [0, 40, 60, 75, 90, 101],
        default: 'unknown',
        output: { count: { $sum: 1 } },
      },
    },
    {
      $project: {
        _id: 0,
        bucket: {
          $switch: {
            branches: [
              { case: { $eq: ['$_id', 0] }, then: '0-39 poor' },
              { case: { $eq: ['$_id', 40] }, then: '40-59 weak' },
              { case: { $eq: ['$_id', 60] }, then: '60-74 fair' },
              { case: { $eq: ['$_id', 75] }, then: '75-89 strong' },
              { case: { $eq: ['$_id', 90] }, then: '90-100 excellent' },
            ],
            default: 'unknown',
          },
        },
        count: 1,
      },
    },
  ];
}

/**
 * SINGLE-ROUND-TRIP variant of the four stat cards.
 *
 * Runs on `jobAnalyses` and pulls the other two collections in with
 * `$unionWith`, so the admin dashboard needs ONE database call instead of four.
 * Output shape: [{ metric: 'totalUsers', value: 42 }, ...]
 *
 * Requires MongoDB >= 4.4 ($unionWith). If you must support 4.2, call the four
 * pipelines above in parallel instead - see `runDashboardStats`.
 */
function dashboardOverviewPipeline(offsetMinutes = 0, avgWindowDays = 30) {
  const todayStart = startOfToday(offsetMinutes);
  const todayEnd = startOfTomorrow(offsetMinutes);
  const avgFrom = daysAgo(avgWindowDays, offsetMinutes);

  return [
    // --- jobs analyzed today (base collection: jobAnalyses) -----------------
    { $match: { deleted: false, createdAt: { $gte: todayStart, $lt: todayEnd } } },
    { $group: { _id: null, value: { $sum: 1 } } },
    { $project: { _id: 0, metric: { $literal: 'jobsAnalyzedToday' }, value: 1 } },

    // --- average match score over the window --------------------------------
    {
      $unionWith: {
        coll: 'jobAnalyses',
        pipeline: [
          { $match: { deleted: false, createdAt: { $gte: avgFrom } } },
          { $group: { _id: null, value: { $avg: '$analysis.matchScore' } } },
          {
            $project: {
              _id: 0,
              metric: { $literal: 'avgMatchScore' },
              value: { $round: [{ $ifNull: ['$value', 0] }, 0] },
            },
          },
        ],
      },
    },

    // --- total users ---------------------------------------------------------
    {
      $unionWith: {
        coll: 'users',
        pipeline: [
          { $match: { role: 'USER', deleted: false } },
          { $group: { _id: null, value: { $sum: 1 } } },
          { $project: { _id: 0, metric: { $literal: 'totalUsers' }, value: 1 } },
        ],
      },
    },

    // --- published portfolios -------------------------------------------------
    {
      $unionWith: {
        coll: 'portfolios',
        pipeline: [
          { $match: { isPublished: true, deleted: false } },
          { $group: { _id: null, value: { $sum: 1 } } },
          { $project: { _id: 0, metric: { $literal: 'publishedPortfolios' }, value: 1 } },
        ],
      },
    },
  ];
}

/**
 * Convenience runner used by `npm run stats`.
 * Executes the individual pipelines in parallel and normalises the result into
 * the exact object the admin dashboard renders.
 */
async function runDashboardStats(db, offsetMinutes = 0) {
  const first = (rows) => (rows[0] ? rows[0] : null);

  const [totalUsers, published, today, avg, subScores, distribution, byStatus] = await Promise.all([
    db.collection('users').aggregate(totalUsersPipeline()).toArray(),
    db.collection('portfolios').aggregate(publishedPortfoliosPipeline()).toArray(),
    db.collection('jobAnalyses').aggregate(jobsAnalyzedTodayPipeline(offsetMinutes)).toArray(),
    db.collection('jobAnalyses').aggregate(averageMatchScorePipeline(30, offsetMinutes)).toArray(),
    db.collection('jobAnalyses').aggregate(averageSubScoresPipeline(30, offsetMinutes)).toArray(),
    db.collection('jobAnalyses').aggregate(matchScoreDistributionPipeline(null)).toArray(),
    db.collection('users').aggregate(usersByStatusPipeline()).toArray(),
  ]);

  return {
    totalUsers: first(totalUsers)?.value ?? 0,
    publishedPortfolios: first(published)?.value ?? 0,
    jobsAnalyzedToday: first(today)?.value ?? 0,
    avgMatchScore: first(avg)?.value ?? 0,
    avgSubScores: first(subScores) ?? { skillsMatch: 0, projectsMatch: 0, requirementsMatch: 0 },
    matchScoreDistribution: distribution,
    usersByStatus: byStatus,
  };
}

module.exports = {
  totalUsersPipeline,
  usersByStatusPipeline,
  publishedPortfoliosPipeline,
  jobsAnalyzedTodayPipeline,
  averageMatchScorePipeline,
  averageSubScoresPipeline,
  matchScoreDistributionPipeline,
  dashboardOverviewPipeline,
  runDashboardStats,
};
