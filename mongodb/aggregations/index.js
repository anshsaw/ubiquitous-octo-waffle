'use strict';

/**
 * Aggregation registry.
 *
 * Every export is either:
 *   - a `*Pipeline(...)` function returning a plain array (portable to Spring
 *     Data / MongoTemplate verbatim), or
 *   - a `run*(db, ...)` helper that executes those pipelines and normalises the
 *     result into the exact shape the UI renders.
 *
 * The Java backend should port the PIPELINES, not the runners.
 */

const dashboardStats = require('./dashboardStats');
const signupTrends = require('./signupTrends');
const skillGaps = require('./skillGaps');
const publicPortfolio = require('./publicPortfolio');

module.exports = {
  ...dashboardStats,
  ...signupTrends,
  ...skillGaps,
  ...publicPortfolio,
  dashboardStats,
  signupTrends,
  skillGaps,
  publicPortfolio,
};
