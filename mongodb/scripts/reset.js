'use strict';

/**
 * DESTRUCTIVE. Drops every collection this package manages.
 *
 * Guarded three ways:
 *   1. requires ALLOW_DESTRUCTIVE=true in the environment
 *   2. refuses any database whose name contains "prod"
 *   3. prints exactly what it is about to destroy, then waits 3 seconds
 *
 * Intended for "my local data is a mess, start over". Never wire this into a
 * pipeline.
 *
 * Run:  ALLOW_DESTRUCTIVE=true npm run reset
 */

const { runScript } = require('../lib/db');
const { getConfig } = require('../lib/env');
const { COLLECTION_NAMES } = require('../schemas');

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function reset(db) {
  const cfg = getConfig();

  if (!cfg.allowDestructive) {
    throw new Error('Refusing to drop collections. Set ALLOW_DESTRUCTIVE=true to confirm.');
  }
  if (/prod/i.test(cfg.dbName)) {
    throw new Error(`Refusing to drop collections in "${cfg.dbName}" - the name looks like production.`);
  }

  const existing = new Set((await db.listCollections({}, { nameOnly: true }).toArray()).map((c) => c.name));
  const targets = COLLECTION_NAMES.filter((name) => existing.has(name));

  if (targets.length === 0) {
    console.log('  Nothing to drop.');
    return;
  }

  console.log(`  About to DROP ${targets.length} collections from "${cfg.dbName}":`);
  for (const name of targets) {
    const count = await db.collection(name).estimatedDocumentCount();
    console.log(`    - ${name.padEnd(20)} ~${count} documents`);
  }
  console.log('\n  Press Ctrl+C within 3 seconds to abort...');
  await sleep(3000);

  for (const name of targets) {
    await db.collection(name).drop();
    console.log(`  dropped ${name}`);
  }
}

if (require.main === module) {
  runScript('reset', reset);
}

module.exports = { reset };
