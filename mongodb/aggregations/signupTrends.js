'use strict';

/**
 * ADMIN DASHBOARD - time-series charts
 * ====================================
 *
 * Backs: "daily signups" line chart and the analyses-per-day / avg-score-per-day
 * trend lines.
 *
 * Bucketing strategy: `$dateToString` with an explicit `timezone`, producing
 * "YYYY-MM-DD" string keys. Chosen over `$dateTrunc` (5.0+) for wider server
 * compatibility, and because a string key maps cleanly to a Recharts x-axis and
 * to a Java `String`/`LocalDate` without BSON date juggling.
 *
 * Gap filling: MongoDB returns NO row for a day with zero events. Charts need a
 * zero. `fillGaps()` below does that in application code - cheaper and simpler
 * than `$densify` (5.1+), and it keeps the pipelines portable.
 */

const { daysAgo, DAY_MS, DEFAULT_TIMEZONE } = require('../lib/dates');

/**
 * Daily new-user signups over a rolling window.
 * Uses: users.createdAt (indexed range) -> group -> sort.
 *
 * @param {number} days
 * @param {string} timezone IANA zone, e.g. 'Asia/Kolkata'
 * @param {number} offsetMinutes offset matching `timezone`, for the boundary
 */
function dailySignupsPipeline(days = 30, timezone = DEFAULT_TIMEZONE, offsetMinutes = 0) {
  return [
    { $match: { createdAt: { $gte: daysAgo(days, offsetMinutes) }, deleted: false } },
    {
      $group: {
        _id: { $dateToString: { format: '%Y-%m-%d', date: '$createdAt', timezone } },
        signups: { $sum: 1 },
        admins: { $sum: { $cond: [{ $eq: ['$role', 'ADMIN'] }, 1, 0] } },
      },
    },
    { $project: { _id: 0, date: '$_id', signups: 1, admins: 1 } },
    { $sort: { date: 1 } },
  ];
}

/**
 * Daily analysis volume plus the average score that day.
 * Two series on one chart: "how much are people analysing" and "how good are
 * the matches" - the second is the real product-health signal.
 */
function dailyAnalysesPipeline(days = 30, timezone = DEFAULT_TIMEZONE, offsetMinutes = 0) {
  return [
    { $match: { createdAt: { $gte: daysAgo(days, offsetMinutes) }, deleted: false } },
    {
      $group: {
        _id: { $dateToString: { format: '%Y-%m-%d', date: '$createdAt', timezone } },
        analyses: { $sum: 1 },
        avgMatchScore: { $avg: '$analysis.matchScore' },
        uniqueUsers: { $addToSet: '$userId' },
      },
    },
    {
      $project: {
        _id: 0,
        date: '$_id',
        analyses: 1,
        avgMatchScore: { $round: ['$avgMatchScore', 0] },
        uniqueUsers: { $size: '$uniqueUsers' },
      },
    },
    { $sort: { date: 1 } },
  ];
}

/** Daily publish events - adoption of the final step in the funnel. */
function dailyPublishedPortfoliosPipeline(days = 30, timezone = DEFAULT_TIMEZONE, offsetMinutes = 0) {
  return [
    {
      $match: {
        isPublished: true,
        deleted: false,
        publishedAt: { $gte: daysAgo(days, offsetMinutes) },
      },
    },
    {
      $group: {
        _id: { $dateToString: { format: '%Y-%m-%d', date: '$publishedAt', timezone } },
        published: { $sum: 1 },
      },
    },
    { $project: { _id: 0, date: '$_id', published: 1 } },
    { $sort: { date: 1 } },
  ];
}

/**
 * Conversion funnel: registered -> has profile -> has project -> analysed -> published.
 *
 * Runs on `users` and uses `$lookup` with a pipeline (not a raw localField
 * join) so each stage can filter on `deleted: false`. `$limit: 1` inside every
 * sub-pipeline makes each lookup an existence check rather than a full fetch.
 *
 * This is an ADMIN-ONLY, on-demand report. Do not put it on a hot path: it
 * touches four collections. Cache the result for a few minutes.
 */
function conversionFunnelPipeline() {
  const exists = (coll, extra = {}) => ({
    $lookup: {
      from: coll,
      let: { uid: '$_id' },
      pipeline: [
        { $match: { $expr: { $eq: ['$userId', '$$uid'] }, deleted: false, ...extra } },
        { $limit: 1 },
        { $project: { _id: 1 } },
      ],
      as: `_${coll}`,
    },
  });

  return [
    { $match: { role: 'USER', deleted: false } },
    {
      $lookup: {
        from: 'profiles',
        let: { uid: '$_id' },
        pipeline: [{ $match: { $expr: { $eq: ['$userId', '$$uid'] } } }, { $limit: 1 }, { $project: { _id: 1 } }],
        as: '_profiles',
      },
    },
    exists('projects'),
    exists('jobAnalyses'),
    exists('portfolios', { isPublished: true }),
    {
      $group: {
        _id: null,
        registered: { $sum: 1 },
        withProfile: { $sum: { $cond: [{ $gt: [{ $size: '$_profiles' }, 0] }, 1, 0] } },
        withProject: { $sum: { $cond: [{ $gt: [{ $size: '$_projects' }, 0] }, 1, 0] } },
        analyzedJob: { $sum: { $cond: [{ $gt: [{ $size: '$_jobAnalyses' }, 0] }, 1, 0] } },
        publishedPortfolio: { $sum: { $cond: [{ $gt: [{ $size: '$_portfolios' }, 0] }, 1, 0] } },
      },
    },
    { $project: { _id: 0 } },
  ];
}

/**
 * Inserts zero rows for days that produced no documents, so the line chart has
 * a continuous x-axis.
 *
 * @param {Array<Record<string, any>>} rows result of one of the pipelines above
 * @param {number} days window size
 * @param {Record<string, number>} zeroFields e.g. { signups: 0, admins: 0 }
 */
function fillGaps(rows, days, zeroFields, offsetMinutes = 0) {
  const byDate = new Map(rows.map((r) => [r.date, r]));
  const start = daysAgo(days, offsetMinutes).getTime();
  const out = [];

  for (let i = 0; i < days; i += 1) {
    const date = new Date(start + i * DAY_MS).toISOString().slice(0, 10);
    out.push(byDate.get(date) || { date, ...zeroFields });
  }
  return out;
}

/** Convenience runner used by `npm run stats`. */
async function runSignupTrends(db, days = 30, timezone = DEFAULT_TIMEZONE, offsetMinutes = 0) {
  const [signups, analyses, published, funnel] = await Promise.all([
    db.collection('users').aggregate(dailySignupsPipeline(days, timezone, offsetMinutes)).toArray(),
    db.collection('jobAnalyses').aggregate(dailyAnalysesPipeline(days, timezone, offsetMinutes)).toArray(),
    db.collection('portfolios').aggregate(dailyPublishedPortfoliosPipeline(days, timezone, offsetMinutes)).toArray(),
    db.collection('users').aggregate(conversionFunnelPipeline()).toArray(),
  ]);

  return {
    dailySignups: fillGaps(signups, days, { signups: 0, admins: 0 }, offsetMinutes),
    dailyAnalyses: fillGaps(analyses, days, { analyses: 0, avgMatchScore: 0, uniqueUsers: 0 }, offsetMinutes),
    dailyPublished: fillGaps(published, days, { published: 0 }, offsetMinutes),
    funnel: funnel[0] || null,
  };
}

module.exports = {
  dailySignupsPipeline,
  dailyAnalysesPipeline,
  dailyPublishedPortfoliosPipeline,
  conversionFunnelPipeline,
  fillGaps,
  runSignupTrends,
};
