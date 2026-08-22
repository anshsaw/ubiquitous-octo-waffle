'use strict';

/**
 * Environment loader.
 *
 * Purpose
 * -------
 * Single place where configuration is read. Nothing in this package is allowed
 * to hardcode a connection string or credential; every script goes through
 * `getConfig()`.
 *
 * Spring Boot integration point
 * -----------------------------
 * The Java backend reads the exact same variables:
 *
 *   spring.data.mongodb.uri      = ${MONGODB_URI}
 *   spring.data.mongodb.database = ${MONGODB_DB}
 *
 * Keeping the variable names aligned means one secret works for both the
 * tooling in this folder and the running application.
 */

const path = require('path');

// dotenv is optional at runtime: in CI / containers the variables are usually
// injected by the platform and no .env file exists.
try {
  require('dotenv').config({ path: path.resolve(__dirname, '..', '.env') });
} catch (_err) {
  /* dotenv not installed or no .env present - fall back to process.env */
}

const DEFAULT_URI = 'mongodb://localhost:27017';
const DEFAULT_DB = 'portfoliopilot';

function bool(value, fallback = false) {
  if (value === undefined || value === null || value === '') return fallback;
  return String(value).trim().toLowerCase() === 'true';
}

function int(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

/**
 * @returns {{uri: string, dbName: string, seedPassword: string, bcryptRounds: number, allowDestructive: boolean, isAtlas: boolean}}
 */
function getConfig() {
  const uri = process.env.MONGODB_URI || DEFAULT_URI;
  const dbName = process.env.MONGODB_DB || DEFAULT_DB;

  return {
    uri,
    dbName,
    seedPassword: process.env.SEED_PASSWORD || 'DemoPass123!',
    bcryptRounds: int(process.env.SEED_BCRYPT_ROUNDS, 10),
    allowDestructive: bool(process.env.ALLOW_DESTRUCTIVE, false),
    isAtlas: uri.startsWith('mongodb+srv://') || uri.includes('mongodb.net'),
  };
}

/**
 * Masks credentials so a URI can safely appear in logs.
 * `mongodb+srv://user:secret@host/db` -> `mongodb+srv://****:****@host/db`
 */
function redactUri(uri) {
  return String(uri).replace(/\/\/[^@/]*@/, '//****:****@');
}

module.exports = { getConfig, redactUri, DEFAULT_URI, DEFAULT_DB };
