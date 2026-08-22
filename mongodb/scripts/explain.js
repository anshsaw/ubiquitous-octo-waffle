'use strict';

/**
 * QUERY PLAN AUDIT
 *
 * Runs `explain("executionStats")` on every hot query in the application and
 * reports the winning plan. A `COLLSCAN` on any of these is a regression.
 *
 * This is the objective check behind the index strategy: it proves each index
 * is actually used, rather than assuming it.
 *
 * Run:  node scripts/explain.js
 */

const { ObjectId } = require('mongodb');
const { runScript } = require('../lib/db');
const { startOfToday, daysAgo } = require('../lib/dates');
const { ID } = require('../seed/ids');

/** Walks the winning plan tree and collects stage names + index names. */
function summarisePlan(plan) {
  const stages = [];
  let indexName = null;

  (function walk(node) {
    if (!node) return;
    stages.push(node.stage);
    if (node.indexName) indexName = node.indexName;
    if (node.inputStage) walk(node.inputStage);
    if (node.inputStages) node.inputStages.forEach(walk);
  })(plan);

  return { stages, indexName };
}

const QUERIES = [
  {
    label: 'LOGIN               users.findOne({email})',
    collection: 'users',
    run: (c) => c.find({ email: 'demo@portfoliopilot.local', deleted: false }),
  },
  {
    label: 'REGISTER            users.findOne({username})',
    collection: 'users',
    run: (c) => c.find({ username: 'demo-student' }),
  },
  {
    label: 'PROFILE             profiles.findOne({userId})',
    collection: 'profiles',
    run: (c) => c.find({ userId: ID.user(2) }),
  },
  {
    label: 'MY PROJECTS         projects.find({userId,deleted}).sort(createdAt)',
    collection: 'projects',
    run: (c) => c.find({ userId: ID.user(2), deleted: false }).sort({ createdAt: -1 }).limit(20),
  },
  {
    label: 'ANALYZER            projects.find({userId, techStackNormalized $in})',
    collection: 'projects',
    run: (c) => c.find({ userId: ID.user(2), techStackNormalized: { $in: ['java', 'spring boot'] } }),
  },
  {
    label: 'DASHBOARD           jobAnalyses.find({userId}).sort(createdAt).limit(5)',
    collection: 'jobAnalyses',
    run: (c) => c.find({ userId: ID.user(2), deleted: false }).sort({ createdAt: -1 }).limit(5),
  },
  {
    label: 'BEST MATCHES        jobAnalyses.find({userId}).sort(matchScore)',
    collection: 'jobAnalyses',
    run: (c) => c.find({ userId: ID.user(2) }).sort({ 'analysis.matchScore': -1 }).limit(10),
  },
  {
    label: 'ADMIN today         jobAnalyses.count({createdAt >= today})',
    collection: 'jobAnalyses',
    run: (c) => c.find({ deleted: false, createdAt: { $gte: startOfToday() } }),
  },
  {
    label: 'ADMIN log           jobAnalyses.find().sort(createdAt).limit(25)',
    collection: 'jobAnalyses',
    run: (c) => c.find({}).sort({ createdAt: -1 }).limit(25),
  },
  {
    label: 'ADMIN users list    users.find({role,status}).sort(createdAt)',
    collection: 'users',
    run: (c) => c.find({ role: 'USER', status: 'ACTIVE' }).sort({ createdAt: -1 }).limit(25),
  },
  {
    label: 'ADMIN user search   users.find({$text})',
    collection: 'users',
    run: (c) => c.find({ $text: { $search: 'priya' } }),
  },
  {
    label: 'ADMIN signups       users.find({createdAt >= 30d})',
    collection: 'users',
    run: (c) => c.find({ createdAt: { $gte: daysAgo(30) }, deleted: false }),
  },
  {
    label: 'PUBLIC PAGE         portfolios.findOne({username,isPublished})',
    collection: 'portfolios',
    run: (c) => c.find({ username: 'demo-student', isPublished: true, deleted: false }),
  },
  {
    label: 'RESUME FOR ANALYSIS resumes.find({jobAnalysisId})',
    collection: 'resumes',
    run: (c) => c.find({ jobAnalysisId: ID.analysis(101) }).sort({ createdAt: -1 }).limit(1),
  },
  {
    label: 'TEMPLATE PICKER     portfolioTemplates.find({active}).sort(sortOrder)',
    collection: 'portfolioTemplates',
    run: (c) => c.find({ active: true }).sort({ sortOrder: 1 }),
  },
  {
    label: 'SKILL ALIAS         skillDictionary.findOne({aliases})',
    collection: 'skillDictionary',
    run: (c) => c.find({ aliases: 'react js' }),
  },
  {
    label: 'SESSION LOOKUP      refreshTokens.findOne({tokenHash})',
    collection: 'refreshTokens',
    run: (c) => c.find({ tokenHash: 'a'.repeat(64) }),
  },
];

async function explainAll(db) {
  let scans = 0;

  const LABEL_W = 64;
  console.log('  ' + 'query'.padEnd(LABEL_W) + 'plan'.padEnd(16) + 'index');
  console.log('  ' + '-'.repeat(118));

  for (const q of QUERIES) {
    const stats = await q.run(db.collection(q.collection)).explain('executionStats');
    const { stages, indexName } = summarisePlan(stats.queryPlanner.winningPlan);

    const isCollScan = stages.includes('COLLSCAN');
    if (isCollScan) scans += 1;

    const plan = isCollScan ? 'COLLSCAN' : stages.includes('IXSCAN') ? 'IXSCAN' : stages.join('>');
    const examined = stats.executionStats ? stats.executionStats.totalDocsExamined : '?';
    const returned = stats.executionStats ? stats.executionStats.nReturned : '?';

    console.log(
      `  ${q.label.padEnd(LABEL_W)}${plan.padEnd(16)}${(indexName || '-').padEnd(28)}examined ${examined}, returned ${returned}`
    );
  }

  console.log('\n  ' + (scans === 0 ? 'No collection scans. Every hot query is index-backed.' : `${scans} COLLSCAN(s) - investigate.`));
  console.log('  NOTE: on a tiny seeded dataset the planner may still pick a COLLSCAN because');
  console.log('        it is genuinely cheaper. Re-check against production-sized data.');
}

if (require.main === module) {
  runScript('explain', explainAll);
}

module.exports = { explainAll, QUERIES };
