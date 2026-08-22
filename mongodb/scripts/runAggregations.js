'use strict';

/**
 * Executes every admin analytics pipeline against the current database and
 * prints the results.
 *
 * Two purposes:
 *   1. verify the seed produced data the charts can actually render;
 *   2. show the Spring Boot developer the exact response shape each endpoint
 *      is expected to return.
 *
 * Run:  npm run stats
 */

const { runScript } = require('../lib/db');
const { runDashboardStats } = require('../aggregations/dashboardStats');
const { runSignupTrends } = require('../aggregations/signupTrends');
const { runSkillGaps } = require('../aggregations/skillGaps');
const { publicPortfolioPipeline } = require('../aggregations/publicPortfolio');

const table = (rows) => {
  if (!rows || rows.length === 0) return '    (no data)';
  return rows.map((r) => `    ${JSON.stringify(r)}`).join('\n');
};

async function runAggregations(db) {
  // --- stat cards -----------------------------------------------------------
  const stats = await runDashboardStats(db);
  console.log('  ADMIN STAT CARDS');
  console.log(`    totalUsers           ${stats.totalUsers}`);
  console.log(`    publishedPortfolios  ${stats.publishedPortfolios}`);
  console.log(`    jobsAnalyzedToday    ${stats.jobsAnalyzedToday}`);
  console.log(`    avgMatchScore        ${stats.avgMatchScore}%`);
  console.log(`    avgSubScores         ${JSON.stringify(stats.avgSubScores)}`);
  console.log('\n  USERS BY STATUS');
  console.log(table(stats.usersByStatus));
  console.log('\n  MATCH SCORE DISTRIBUTION');
  console.log(table(stats.matchScoreDistribution));

  // --- trends ---------------------------------------------------------------
  const trends = await runSignupTrends(db, 30);
  const nonZero = trends.dailySignups.filter((d) => d.signups > 0);
  console.log('\n  DAILY SIGNUPS (last 30d, non-zero days)');
  console.log(table(nonZero));
  console.log('\n  DAILY ANALYSES (non-zero days)');
  console.log(table(trends.dailyAnalyses.filter((d) => d.analyses > 0)));
  console.log('\n  CONVERSION FUNNEL');
  console.log(`    ${JSON.stringify(trends.funnel)}`);

  // --- skills ---------------------------------------------------------------
  const skills = await runSkillGaps(db, 365);
  console.log('\n  TOP SKILL GAPS');
  console.log(table(skills.topSkillGaps));
  console.log('\n  MOST REQUESTED SKILLS');
  console.log(table(skills.mostRequestedSkills));
  console.log('\n  MOST ANALYZED ROLES');
  console.log(table(skills.mostAnalyzedRoles));
  console.log('\n  SKILL GAPS BY CATEGORY');
  console.log(table(skills.gapsByCategory.map((c) => ({ category: c.category, occurrences: c.occurrences, distinctSkills: c.distinctSkills }))));

  // --- public portfolio -----------------------------------------------------
  const [publicPage] = await db
    .collection('portfolios')
    .aggregate(publicPortfolioPipeline('demo-student'))
    .toArray();

  console.log('\n  PUBLIC PORTFOLIO /portfolio/demo-student');
  if (!publicPage) {
    console.log('    (not found - run npm run seed first)');
  } else {
    console.log(`    template   ${publicPage.templateKey}`);
    console.log(`    owner      ${publicPage.owner.fullName} - ${publicPage.owner.professionalTitle}`);
    console.log(`    skills     ${publicPage.skills.length} (first: ${publicPage.skills.slice(0, 5).map((s) => s.name).join(', ')})`);
    console.log(`    projects   ${publicPage.projects.length} (first: ${publicPage.projects[0] && publicPage.projects[0].title})`);
    // Guard against the single worst possible bug on this route.
    const serialized = JSON.stringify(publicPage);
    const leaks = ['passwordHash', 'failedLoginAttempts', 'tokenHash', '"email"'].filter((f) => serialized.includes(f));
    console.log(`    leak check ${leaks.length === 0 ? 'clean - no credential or account fields exposed' : `LEAKED: ${leaks.join(', ')}`}`);
  }
}

if (require.main === module) {
  runScript('runAggregations', runAggregations);
}

module.exports = { runAggregations };
