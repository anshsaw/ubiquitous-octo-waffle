'use strict';

/**
 * DETERMINISTIC ObjectIds for seed data.
 *
 * Why fixed ids instead of generated ones?
 *   1. IDEMPOTENCY - `updateOne({_id: FIXED}, {$set: ...}, {upsert: true})` can
 *      be re-run forever without ever creating a duplicate.
 *   2. STABLE CROSS-REFERENCES - a jobAnalysis can point at a project id that
 *      is known before either document is written.
 *   3. RECOGNISABLE IN LOGS - a seeded id starts with a type prefix, so
 *      `3000...0002` is obviously "seed project #2" and never gets mistaken for
 *      real user data.
 *
 * Prefix map (first hex digit):
 *   1 users   2 profiles   3 projects   4 templates   5 jobAnalyses
 *   6 resumes 7 portfolios 8 skillDictionary          9 embedded sub-documents
 *   a adminLogs
 *
 * Real application writes must ALWAYS use `new ObjectId()`.
 */

const { ObjectId } = require('mongodb');

/**
 * @param {string} prefix single hex character
 * @param {number} n      sequence number
 */
function fixedId(prefix, n) {
  const hex = `${prefix}${String(n).padStart(23, '0')}`;
  return new ObjectId(hex);
}

const ID = {
  user: (n) => fixedId('1', n),
  profile: (n) => fixedId('2', n),
  project: (n) => fixedId('3', n),
  template: (n) => fixedId('4', n),
  analysis: (n) => fixedId('5', n),
  resume: (n) => fixedId('6', n),
  portfolio: (n) => fixedId('7', n),
  skill: (n) => fixedId('8', n),
  sub: (n) => fixedId('9', n),
  adminLog: (n) => fixedId('a', n),
};

module.exports = { fixedId, ID };
