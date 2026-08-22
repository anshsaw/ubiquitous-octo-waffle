'use strict';

/**
 * Date-window helpers used by the aggregation pipelines.
 *
 * WHY BOUNDARIES ARE COMPUTED IN THE DRIVER, NOT IN THE PIPELINE
 * --------------------------------------------------------------
 * A `$match` against a pre-computed `Date` constant is a plain range predicate
 * and CAN use the `createdAt` index. A `$match` that computes the boundary with
 * `$dateTrunc`/`$$NOW` inside the pipeline is an expression, which the planner
 * cannot turn into an index bound - it degrades to a collection scan.
 *
 * So: compute the boundary in Java/Node, pass it in as a literal.
 *
 * Spring Boot equivalent:
 *   Instant start = LocalDate.now(zone).atStartOfDay(zone).toInstant();
 */

/** Milliseconds in one day. */
const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * Start of "today" for a fixed UTC offset in minutes.
 * `offsetMinutes = 330` -> IST (UTC+05:30).
 *
 * @param {number} [offsetMinutes=0]
 * @param {Date}   [now=new Date()]
 * @returns {Date} UTC instant of local midnight
 */
function startOfToday(offsetMinutes = 0, now = new Date()) {
  const shifted = new Date(now.getTime() + offsetMinutes * 60_000);
  shifted.setUTCHours(0, 0, 0, 0);
  return new Date(shifted.getTime() - offsetMinutes * 60_000);
}

/**
 * Start of the day `days` days ago (inclusive lower bound for trend charts).
 * `daysAgo(30)` -> midnight 29 days before today, i.e. a 30-bucket window.
 */
function daysAgo(days, offsetMinutes = 0, now = new Date()) {
  return new Date(startOfToday(offsetMinutes, now).getTime() - (days - 1) * DAY_MS);
}

/** Exclusive upper bound: start of tomorrow. */
function startOfTomorrow(offsetMinutes = 0, now = new Date()) {
  return new Date(startOfToday(offsetMinutes, now).getTime() + DAY_MS);
}

/**
 * IANA timezone string handed to `$dateToString` / `$dateTrunc` so bucket
 * labels match the boundary computed above. Keep the two consistent.
 */
const DEFAULT_TIMEZONE = 'UTC';

module.exports = { DAY_MS, startOfToday, startOfTomorrow, daysAgo, DEFAULT_TIMEZONE };
