'use strict';

/**
 * STRUCTURE-ONLY BOOTSTRAP - safe for every environment, including production.
 *
 * Creates collections, applies JSON Schema validators, and builds indexes.
 * Writes NO data.
 *
 *   npm run setup   -> structure only          (prod-safe)
 *   npm run seed    -> structure + demo data   (DEVELOPMENT ONLY)
 *
 * Run this as part of your deployment pipeline before starting the Spring Boot
 * application. It is idempotent, so running it on every deploy is fine.
 */

const { runScript } = require('../lib/db');
const { applySchemas } = require('./applySchemas');
const { createIndexes, reportIndexes } = require('../indexes/indexes');

async function setup(db) {
  console.log('  [1/2] schema validators');
  await applySchemas(db);

  console.log('\n  [2/2] indexes');
  await createIndexes(db);
  await reportIndexes(db);

  console.log('\n  Structure ready. No demo data was written.');
  console.log('  For a development dataset run:  npm run seed');
}

if (require.main === module) {
  runScript('setup', setup);
}

module.exports = { setup };
