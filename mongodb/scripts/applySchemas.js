'use strict';

/**
 * Creates every collection and applies its `$jsonSchema` validator.
 *
 * IDEMPOTENT:
 *   - collection missing -> `createCollection` with the validator
 *   - collection present -> `collMod` to update the validator in place
 *
 * Safe to re-run after editing any `schemas/*.schema.json`; existing documents
 * are untouched (only the rules change).
 *
 * Run:  npm run schemas
 */

const { runScript } = require('../lib/db');
const { SCHEMAS } = require('../schemas');

async function applySchemas(db) {
  const existing = new Set(
    (await db.listCollections({}, { nameOnly: true }).toArray()).map((c) => c.name)
  );

  for (const schema of SCHEMAS) {
    const { collection, validator, validationLevel, validationAction } = schema;

    const options = {
      validator,
      validationLevel: validationLevel || 'strict',
      validationAction: validationAction || 'error',
    };

    if (existing.has(collection)) {
      // collMod swaps the validator without touching stored documents.
      await db.command({ collMod: collection, ...options });
      console.log(`  updated  ${collection.padEnd(20)} validator`);
    } else {
      await db.createCollection(collection, options);
      console.log(`  created  ${collection.padEnd(20)} + validator`);
    }
  }

  console.log(`\n  ${SCHEMAS.length} collections validated.`);
}

if (require.main === module) {
  runScript('applySchemas', applySchemas);
}

module.exports = { applySchemas };
