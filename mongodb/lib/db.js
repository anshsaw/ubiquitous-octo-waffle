'use strict';

/**
 * MongoDB connection helper shared by every script in this package.
 *
 * Design notes
 * ------------
 * - One `MongoClient` per process, closed in a `finally` block by `withDb()`.
 * - `w: 'majority'` write concern so setup scripts are durable on replica sets
 *   and on Atlas (Atlas is always a replica set).
 * - Server API v1 is NOT pinned: pinning it would reject `collMod` shapes and
 *   some admin commands used by the schema/index scripts on older servers.
 */

const { MongoClient } = require('mongodb');
const { getConfig, redactUri } = require('./env');

/**
 * Opens a connection, hands the `Db` to `fn`, then always closes the client.
 *
 * @template T
 * @param {(db: import('mongodb').Db, client: MongoClient) => Promise<T>} fn
 * @returns {Promise<T>}
 */
async function withDb(fn) {
  const cfg = getConfig();

  const client = new MongoClient(cfg.uri, {
    // Fail fast during local development instead of hanging for 30s.
    serverSelectionTimeoutMS: 10_000,
    // Durable acknowledgements for schema/index/seed operations.
    writeConcern: { w: 'majority' },
    appName: 'portfoliopilot-db-tooling',
  });

  await client.connect();
  const db = client.db(cfg.dbName);

  try {
    return await fn(db, client);
  } finally {
    await client.close();
  }
}

/** Prints the target the script is about to touch (credentials redacted). */
function logTarget(scriptName) {
  const cfg = getConfig();
  console.log(`\n[${scriptName}]`);
  console.log(`  cluster  : ${redactUri(cfg.uri)}`);
  console.log(`  database : ${cfg.dbName}`);
  console.log(`  mode     : ${cfg.isAtlas ? 'MongoDB Atlas' : 'local / self-hosted'}\n`);
}

/**
 * Standard entrypoint wrapper: prints a header, runs the script, sets a
 * non-zero exit code on failure so CI pipelines detect it.
 *
 * @param {string} scriptName
 * @param {(db: import('mongodb').Db, client: MongoClient) => Promise<void>} fn
 */
function runScript(scriptName, fn) {
  logTarget(scriptName);
  withDb(fn)
    .then(() => {
      console.log(`\n[${scriptName}] done.\n`);
    })
    .catch((err) => {
      console.error(`\n[${scriptName}] FAILED: ${err.message}`);
      if (err.errInfo) console.error(JSON.stringify(err.errInfo, null, 2));
      process.exitCode = 1;
    });
}

/** True when the collection already exists in the database. */
async function collectionExists(db, name) {
  const found = await db.listCollections({ name }, { nameOnly: true }).toArray();
  return found.length > 0;
}

module.exports = { withDb, runScript, logTarget, collectionExists };
