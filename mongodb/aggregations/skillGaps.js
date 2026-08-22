'use strict';

/**
 * ADMIN DASHBOARD - skill intelligence
 * ====================================
 *
 * Backs the "top skill gaps across all users" bar chart, "most requested
 * skills" and "most analysed job roles".
 *
 * CRITICAL: every pipeline groups on a *Normalized field, never on the display
 * field. Grouping on `analysis.skillGaps` would split one real skill across
 * several bars ("React", "React.js", "ReactJS") and make the chart wrong.
 *
 * Shape, exactly as specified:
 *   jobAnalyses -> $match window -> $unwind gaps -> $group by skill
 *               -> $count -> $sort desc -> $limit N
 */

const { daysAgo } = require('../lib/dates');

/**
 * TOP SKILL GAPS - the flagship admin chart.
 *
 * `usersAffected` (a $addToSet of userId, then $size) matters more than the raw
 * occurrence count: one power user analysing 50 Docker jobs must not outweigh
 * 20 distinct users who each lack Docker once.
 *
 * @param {number}      limit
 * @param {number|null} days  rolling window; null = all time
 */
function topSkillGapsPipeline(limit = 10, days = 90, offsetMinutes = 0) {
  const match = { deleted: false };
  if (days) match.createdAt = { $gte: daysAgo(days, offsetMinutes) };

  return [
    // 1. bound the scan using the createdAt index
    { $match: match },

    // 2. keep only what the rest of the pipeline needs (reduces memory pressure
    //    before $unwind multiplies the document count)
    { $project: { userId: 1, gaps: '$analysis.skillGapsNormalized', display: '$analysis.skillGaps' } },

    // 3. one document per (analysis, gap)
    { $unwind: '$gaps' },

    // 4. collapse by canonical skill
    {
      $group: {
        _id: '$gaps',
        occurrences: { $sum: 1 },
        users: { $addToSet: '$userId' },
        sampleDisplayName: { $first: { $arrayElemAt: ['$display', 0] } },
      },
    },

    // 5. shape for the chart
    {
      $project: {
        _id: 0,
        skill: '$_id',
        occurrences: 1,
        usersAffected: { $size: '$users' },
      },
    },

    // 6. rank
    { $sort: { usersAffected: -1, occurrences: -1, skill: 1 } },
    { $limit: limit },
  ];
}

/**
 * MOST REQUESTED SKILLS - union of strong skills and gaps, i.e. everything the
 * market asked for. `$concatArrays` merges the two arrays before unwinding so a
 * skill is counted once per analysis whether the user had it or not.
 *
 * `demandCount` vs `gapCount` gives the training-need ratio the admin actually
 * cares about: "80% of the people who see this requirement do not have it".
 */
function mostRequestedSkillsPipeline(limit = 15, days = 90, offsetMinutes = 0) {
  const match = { deleted: false };
  if (days) match.createdAt = { $gte: daysAgo(days, offsetMinutes) };

  return [
    { $match: match },
    {
      $project: {
        userId: 1,
        requested: {
          $setUnion: [
            { $ifNull: ['$analysis.strongSkillsNormalized', []] },
            { $ifNull: ['$analysis.skillGapsNormalized', []] },
          ],
        },
        gaps: { $ifNull: ['$analysis.skillGapsNormalized', []] },
      },
    },
    { $unwind: '$requested' },
    {
      $group: {
        _id: '$requested',
        demandCount: { $sum: 1 },
        gapCount: { $sum: { $cond: [{ $in: ['$requested', '$gaps'] }, 1, 0] } },
        users: { $addToSet: '$userId' },
      },
    },
    {
      $project: {
        _id: 0,
        skill: '$_id',
        demandCount: 1,
        gapCount: 1,
        usersAffected: { $size: '$users' },
        gapRatio: {
          $round: [
            { $multiply: [{ $divide: ['$gapCount', { $max: ['$demandCount', 1] }] }, 100] },
            0,
          ],
        },
      },
    },
    { $sort: { demandCount: -1, skill: 1 } },
    { $limit: limit },
  ];
}

/**
 * MOST ANALYSED JOB ROLES.
 * Groups on the pre-normalised title so "Java Backend Developer" and
 * "java backend developer (remote)" land in the same bucket.
 * Uses: jobAnalyses.jobTitle_createdAt.
 */
function mostAnalyzedRolesPipeline(limit = 10, days = 90, offsetMinutes = 0) {
  const match = { deleted: false };
  if (days) match.createdAt = { $gte: daysAgo(days, offsetMinutes) };

  return [
    { $match: match },
    {
      $group: {
        _id: '$job.normalizedTitle',
        count: { $sum: 1 },
        avgMatchScore: { $avg: '$analysis.matchScore' },
        displayTitle: { $first: '$job.title' },
        companies: { $addToSet: '$job.company' },
      },
    },
    {
      $project: {
        _id: 0,
        role: '$displayTitle',
        normalizedRole: '$_id',
        count: 1,
        avgMatchScore: { $round: ['$avgMatchScore', 0] },
        distinctCompanies: { $size: '$companies' },
      },
    },
    { $sort: { count: -1, role: 1 } },
    { $limit: limit },
  ];
}

/**
 * SKILL GAPS GROUPED BY CATEGORY - "our users are weakest in DEVOPS".
 *
 * Joins each gap to `skillDictionary` to resolve its category. The lookup runs
 * AFTER $group, so it executes once per distinct skill (tens of rows) rather
 * than once per analysis (thousands) - the ordering here is the whole
 * performance story.
 */
function skillGapsByCategoryPipeline(days = 90, offsetMinutes = 0) {
  const match = { deleted: false };
  if (days) match.createdAt = { $gte: daysAgo(days, offsetMinutes) };

  return [
    { $match: match },
    { $project: { userId: 1, gaps: '$analysis.skillGapsNormalized' } },
    { $unwind: '$gaps' },
    { $group: { _id: '$gaps', occurrences: { $sum: 1 }, users: { $addToSet: '$userId' } } },
    {
      $lookup: {
        from: 'skillDictionary',
        localField: '_id',
        foreignField: 'normalizedName',
        as: 'dict',
      },
    },
    {
      $group: {
        _id: { $ifNull: [{ $arrayElemAt: ['$dict.category', 0] }, 'OTHER'] },
        occurrences: { $sum: '$occurrences' },
        distinctSkills: { $sum: 1 },
        topSkills: { $push: { skill: '$_id', occurrences: '$occurrences' } },
      },
    },
    {
      $project: {
        _id: 0,
        category: '$_id',
        occurrences: 1,
        distinctSkills: 1,
        // $sortArray requires MongoDB >= 5.2. On 5.0/4.4 drop this stage and
        // sort the `topSkills` array in the application layer instead.
        topSkills: { $slice: [{ $sortArray: { input: '$topSkills', sortBy: { occurrences: -1 } } }, 5] },
      },
    },
    { $sort: { occurrences: -1 } },
  ];
}

/**
 * Per-user skill gaps - "what should I learn next?" on the user dashboard.
 * Scoped by userId first so it reads only that user's analyses.
 */
function userSkillGapsPipeline(userId, limit = 10) {
  return [
    { $match: { userId, deleted: false } },
    { $project: { gaps: '$analysis.skillGapsNormalized', createdAt: 1 } },
    { $unwind: '$gaps' },
    {
      $group: {
        _id: '$gaps',
        timesBlocked: { $sum: 1 },
        lastSeen: { $max: '$createdAt' },
      },
    },
    { $project: { _id: 0, skill: '$_id', timesBlocked: 1, lastSeen: 1 } },
    { $sort: { timesBlocked: -1, lastSeen: -1 } },
    { $limit: limit },
  ];
}

/** Convenience runner used by `npm run stats`. */
async function runSkillGaps(db, days = 90) {
  const coll = db.collection('jobAnalyses');
  const [topGaps, requested, roles, byCategory] = await Promise.all([
    coll.aggregate(topSkillGapsPipeline(10, days)).toArray(),
    coll.aggregate(mostRequestedSkillsPipeline(15, days)).toArray(),
    coll.aggregate(mostAnalyzedRolesPipeline(10, days)).toArray(),
    coll.aggregate(skillGapsByCategoryPipeline(days)).toArray(),
  ]);

  return { topSkillGaps: topGaps, mostRequestedSkills: requested, mostAnalyzedRoles: roles, gapsByCategory: byCategory };
}

module.exports = {
  topSkillGapsPipeline,
  mostRequestedSkillsPipeline,
  mostAnalyzedRolesPipeline,
  skillGapsByCategoryPipeline,
  userSkillGapsPipeline,
  runSkillGaps,
};
