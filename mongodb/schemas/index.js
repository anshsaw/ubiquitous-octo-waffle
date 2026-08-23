'use strict';

/**
 * Schema registry.
 *
 * Each `*.schema.json` file is a self-contained descriptor:
 *   {
 *     collection:       "users",
 *     description:      "...",
 *     validationLevel:  "strict",
 *     validationAction: "error",
 *     validator:        { $jsonSchema: { ... } }
 *   }
 *
 * ORDER MATTERS: collections are created parent-first so that, if you later add
 * cross-collection checks or run a migration that walks references, the targets
 * already exist.
 */

const users = require('./users.schema.json');
const profiles = require('./profiles.schema.json');
const projects = require('./projects.schema.json');
const jobAnalyses = require('./jobAnalyses.schema.json');
const resumes = require('./resumes.schema.json');
const portfolioTemplates = require('./portfolioTemplates.schema.json');
const portfolios = require('./portfolios.schema.json');
const skillDictionary = require('./skillDictionary.schema.json');
const adminLogs = require('./adminLogs.schema.json');
const refreshTokens = require('./refreshTokens.schema.json');

/** Creation order: independent -> dependent. */
const SCHEMAS = [
  users,
  profiles,
  projects,
  portfolioTemplates,
  jobAnalyses,
  resumes,
  portfolios,
  skillDictionary,
  adminLogs,
  refreshTokens,
];

/** Every collection name managed by this package. */
const COLLECTION_NAMES = SCHEMAS.map((s) => s.collection);

/** @param {string} name */
function getSchema(name) {
  const schema = SCHEMAS.find((s) => s.collection === name);
  if (!schema) throw new Error(`Unknown collection schema: ${name}`);
  return schema;
}

module.exports = { SCHEMAS, COLLECTION_NAMES, getSchema };
