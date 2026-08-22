'use strict';

/**
 * Canonical enum values for the PortfolioPilot AI database.
 *
 * These are the SINGLE SOURCE OF TRUTH. They are duplicated (by value, not by
 * import) inside the `$jsonSchema` validators in `schemas/*.schema.json`,
 * because MongoDB validators must be self-contained JSON documents.
 *
 * Spring Boot integration point
 * -----------------------------
 * Mirror each of these as a Java `enum` and persist the enum NAME (the default
 * behaviour of Spring Data MongoDB). Do NOT persist ordinals: the validators
 * below only accept strings, and ordinals break on reordering.
 */

const USER_ROLES = ['USER', 'ADMIN'];

const USER_STATUSES = ['ACTIVE', 'SUSPENDED', 'DELETED'];

const PROFICIENCY_LEVELS = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'];

const EMPLOYMENT_TYPES = ['INTERNSHIP', 'FULL_TIME', 'PART_TIME', 'FREELANCE', 'PROJECT'];

const SKILL_CATEGORIES = [
  'FRONTEND',
  'BACKEND',
  'DATABASE',
  'DEVOPS',
  'CLOUD',
  'MOBILE',
  'TESTING',
  'DATA_AI',
  'TOOLS',
  'LANGUAGE',
  'SOFT_SKILL',
  'OTHER',
];

/** Resume rendering templates. ATS_* templates are single-column, parser-safe. */
const RESUME_TEMPLATES = ['ATS_CLASSIC', 'ATS_COMPACT', 'ATS_MODERN'];

/** Toggleable portfolio sections. Order here is the default render order. */
const PORTFOLIO_SECTIONS = [
  'about',
  'skills',
  'projects',
  'education',
  'experience',
  'certificates',
  'contact',
];

/** Audit trail actions written to `adminLogs`. */
const ADMIN_ACTIONS = [
  'ADMIN_LOGIN',
  'SUSPEND_USER',
  'ACTIVATE_USER',
  'SOFT_DELETE_USER',
  'RESTORE_USER',
  'PURGE_USER',
  'CREATE_TEMPLATE',
  'UPDATE_TEMPLATE',
  'DEACTIVATE_TEMPLATE',
  'DELETE_TEMPLATE',
  'UNPUBLISH_PORTFOLIO',
  'VIEW_USER_DETAIL',
];

/** Where a job description came from. Useful for admin analytics segmentation. */
const JOB_SOURCES = ['PASTED', 'URL', 'UPLOAD', 'SEED'];

module.exports = {
  USER_ROLES,
  USER_STATUSES,
  PROFICIENCY_LEVELS,
  EMPLOYMENT_TYPES,
  SKILL_CATEGORIES,
  RESUME_TEMPLATES,
  PORTFOLIO_SECTIONS,
  ADMIN_ACTIONS,
  JOB_SOURCES,
};
