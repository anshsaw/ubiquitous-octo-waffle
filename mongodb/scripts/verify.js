'use strict';

/**
 * DATABASE INTEGRITY VERIFIER
 *
 * MongoDB has no foreign keys, so referential integrity is a CONVENTION that
 * something has to police. This script is that something. Run it after seeding,
 * after a migration, and in CI.
 *
 * It checks:
 *   - every managed collection exists and has a validator attached
 *   - every declared index is present
 *   - every reference resolves (no orphans)
 *   - the "one published portfolio per username" rule holds
 *   - derived fields (skillIndex, techStackNormalized) are in sync
 *   - scores are int32 and within 0..100
 *   - no password is stored in a non-bcrypt form
 *   - the validator actually REJECTS a knowingly-invalid document
 *
 * Exit code is non-zero if any check fails, so CI can gate on it.
 *
 * Run:  npm run verify
 */

const { runScript } = require('../lib/db');
const { SCHEMAS, COLLECTION_NAMES } = require('../schemas');
const { INDEXES } = require('../indexes/indexes');

let failures = 0;

function check(label, ok, detail = '') {
  const mark = ok ? 'PASS' : 'FAIL';
  if (!ok) failures += 1;
  console.log(`  [${mark}] ${label}${detail ? ` - ${detail}` : ''}`);
}

/** Counts documents whose reference does not resolve in the target collection. */
async function countOrphans(db, from, localField, to, foreignField = '_id', extraMatch = {}) {
  const rows = await db
    .collection(from)
    .aggregate([
      { $match: { [localField]: { $ne: null }, ...extraMatch } },
      { $lookup: { from: to, localField, foreignField, as: '_ref' } },
      { $match: { _ref: { $size: 0 } } },
      { $count: 'n' },
    ])
    .toArray();
  return rows[0] ? rows[0].n : 0;
}

async function verify(db) {
  // ---------------------------------------------------------------- structure
  console.log('  STRUCTURE');
  const live = await db.listCollections({}, { nameOnly: false }).toArray();
  const byName = new Map(live.map((c) => [c.name, c]));

  for (const name of COLLECTION_NAMES) {
    const info = byName.get(name);
    check(`collection ${name}`, !!info);
    if (info) {
      const hasValidator = !!(info.options && info.options.validator && info.options.validator.$jsonSchema);
      check(`  validator on ${name}`, hasValidator);
    }
  }

  // ------------------------------------------------------------------ indexes
  console.log('\n  INDEXES');
  for (const [collection, specs] of Object.entries(INDEXES)) {
    if (!byName.has(collection)) continue;
    const liveIndexes = new Set((await db.collection(collection).indexes()).map((i) => i.name));
    for (const spec of specs) {
      check(`${collection}.${spec.options.name}`, liveIndexes.has(spec.options.name));
    }
  }

  // -------------------------------------------------------- referential links
  console.log('\n  REFERENTIAL INTEGRITY');
  const refChecks = [
    ['profiles.userId -> users', await countOrphans(db, 'profiles', 'userId', 'users')],
    ['projects.userId -> users', await countOrphans(db, 'projects', 'userId', 'users')],
    ['jobAnalyses.userId -> users', await countOrphans(db, 'jobAnalyses', 'userId', 'users')],
    ['resumes.userId -> users', await countOrphans(db, 'resumes', 'userId', 'users')],
    ['resumes.jobAnalysisId -> jobAnalyses', await countOrphans(db, 'resumes', 'jobAnalysisId', 'jobAnalyses')],
    ['portfolios.userId -> users', await countOrphans(db, 'portfolios', 'userId', 'users')],
    ['portfolios.templateId -> portfolioTemplates', await countOrphans(db, 'portfolios', 'templateId', 'portfolioTemplates')],
    ['portfolios.sourceJobAnalysisId -> jobAnalyses', await countOrphans(db, 'portfolios', 'sourceJobAnalysisId', 'jobAnalyses')],
    ['portfolios.resumeId -> resumes', await countOrphans(db, 'portfolios', 'resumeId', 'resumes')],
    ['jobAnalyses.recommendedProjects[].projectId -> projects', await countOrphans(db, 'jobAnalyses', 'recommendedProjects.projectId', 'projects')],
    ['resumes.projects[].projectId -> projects', await countOrphans(db, 'resumes', 'projects.projectId', 'projects')],
    ['portfolios.orderedProjects[] -> projects', await countOrphans(db, 'portfolios', 'orderedProjects', 'projects')],
  ];
  for (const [label, orphans] of refChecks) {
    check(label, orphans === 0, orphans ? `${orphans} orphan(s)` : '');
  }

  // ------------------------------------------------------------ business rules
  console.log('\n  BUSINESS RULES');

  const dupPublished = await db
    .collection('portfolios')
    .aggregate([
      { $match: { isPublished: true, deleted: false } },
      { $group: { _id: '$username', n: { $sum: 1 } } },
      { $match: { n: { $gt: 1 } } },
    ])
    .toArray();
  check('at most one published portfolio per username', dupPublished.length === 0, JSON.stringify(dupPublished));

  const profilesPerUser = await db
    .collection('profiles')
    .aggregate([{ $group: { _id: '$userId', n: { $sum: 1 } } }, { $match: { n: { $gt: 1 } } }])
    .toArray();
  check('exactly one profile per user', profilesPerUser.length === 0);

  const badScores = await db.collection('jobAnalyses').countDocuments({
    $or: [
      { 'analysis.matchScore': { $not: { $type: 'int' } } },
      { 'analysis.matchScore': { $lt: 0 } },
      { 'analysis.matchScore': { $gt: 100 } },
      { 'analysis.skillsMatch': { $not: { $gte: 0, $lte: 100 } } },
      { 'analysis.projectsMatch': { $not: { $gte: 0, $lte: 100 } } },
      { 'analysis.requirementsMatch': { $not: { $gte: 0, $lte: 100 } } },
    ],
  });
  check('all match scores are int32 within 0..100', badScores === 0, badScores ? `${badScores} bad` : '');

  const plaintext = await db.collection('users').countDocuments({
    passwordHash: { $not: /^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$/ },
  });
  check('every passwordHash is a bcrypt hash', plaintext === 0, plaintext ? `${plaintext} suspicious` : '');

  // ------------------------------------------------------------ derived fields
  console.log('\n  DERIVED FIELDS');

  const profiles = await db.collection('profiles').find({}, { projection: { skills: 1, skillIndex: 1 } }).toArray();
  const skillIndexDrift = profiles.filter((p) => {
    const expected = [...new Set((p.skills || []).map((s) => s.normalizedName))].sort();
    const actual = [...(p.skillIndex || [])].sort();
    return JSON.stringify(expected) !== JSON.stringify(actual);
  });
  check('profiles.skillIndex matches skills[].normalizedName', skillIndexDrift.length === 0, `${skillIndexDrift.length} drifted`);

  const techDrift = await db.collection('projects').countDocuments({
    $expr: { $ne: [{ $size: '$techStack' }, { $size: '$techStackNormalized' }] },
  });
  // Not necessarily a bug: two display variants can normalise to one key.
  console.log(`  [INFO] projects where |techStack| != |techStackNormalized|: ${techDrift} (expected when aliases collapse)`);

  const gapDrift = await db.collection('jobAnalyses').countDocuments({
    $expr: { $ne: [{ $size: '$analysis.skillGaps' }, { $size: '$analysis.skillGapsNormalized' }] },
  });
  check('skillGaps and skillGapsNormalized are aligned', gapDrift === 0, `${gapDrift} misaligned`);

  // --------------------------------------------------- validators really work
  console.log('\n  VALIDATOR ENFORCEMENT (negative tests)');

  const negatives = [
    {
      label: 'rejects matchScore > 100',
      collection: 'jobAnalyses',
      doc: {
        userId: null,
        job: { title: 'x', description: 'this description is definitely long enough' },
        analysis: {
          matchScore: 150,
          skillsMatch: 10,
          projectsMatch: 10,
          requirementsMatch: 10,
          strongSkills: [],
          skillGaps: [],
        },
        recommendedProjects: [],
        deleted: false,
        createdAt: new Date(),
      },
    },
    {
      label: 'rejects a plaintext password',
      collection: 'users',
      doc: {
        username: 'bad-user-test',
        name: 'Bad User',
        email: 'bad-user-test@example.com',
        passwordHash: 'hunter2',
        role: 'USER',
        status: 'ACTIVE',
        deleted: false,
        createdAt: new Date(),
        updatedAt: new Date(),
      },
    },
    {
      label: 'rejects an unknown role',
      collection: 'users',
      doc: {
        username: 'bad-role-test',
        name: 'Bad Role',
        email: 'bad-role-test@example.com',
        passwordHash: '$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ012',
        role: 'SUPERADMIN',
        status: 'ACTIVE',
        deleted: false,
        createdAt: new Date(),
        updatedAt: new Date(),
      },
    },
    {
      label: 'rejects duplicate skills in skillIndex',
      collection: 'profiles',
      doc: {
        userId: null,
        fullName: 'Dup Skills',
        skills: [],
        skillIndex: ['react', 'react'],
        education: [],
        experience: [],
        certificates: [],
        createdAt: new Date(),
        updatedAt: new Date(),
      },
    },
  ];

  for (const negative of negatives) {
    let rejected = false;
    try {
      await db.collection(negative.collection).insertOne(negative.doc);
      await db.collection(negative.collection).deleteOne({ _id: negative.doc._id });
    } catch (err) {
      rejected = err.code === 121 || /validation/i.test(err.message);
    }
    check(negative.label, rejected);
  }

  // --------------------------------------------------------------- collections
  console.log('\n  DOCUMENT COUNTS');
  for (const schema of SCHEMAS) {
    const n = await db.collection(schema.collection).countDocuments();
    console.log(`    ${schema.collection.padEnd(20)} ${n}`);
  }

  console.log(`\n  ${failures === 0 ? 'ALL CHECKS PASSED' : `${failures} CHECK(S) FAILED`}`);
  if (failures > 0) throw new Error(`${failures} verification check(s) failed`);
}

if (require.main === module) {
  runScript('verify', verify);
}

module.exports = { verify };
