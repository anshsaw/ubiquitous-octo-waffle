# PortfolioPilot AI — MongoDB Database Layer

> *One profile → many job opportunities → match analysis → tailored resume → adaptive portfolio → public portfolio.*

This folder is the **complete database layer** for PortfolioPilot AI: collections, JSON Schema
validators, indexes, aggregation pipelines, seed data and tooling.

It contains **no application code**. No Spring Boot, no REST controllers, no JWT issuing, no AI, and
nothing that touches the React frontend. A separate Java/Spring Boot backend connects to the database
this package provisions.

---

## Table of contents

1. [Database overview](#1-database-overview)
2. [Collection list](#2-collection-list)
3. [Schema explanation](#3-schema-explanation)
4. [Relationship explanation](#4-relationship-explanation)
5. [Index strategy](#5-index-strategy)
6. [Validation strategy](#6-validation-strategy)
7. [Delete & soft-delete strategy](#7-delete--soft-delete-strategy)
8. [Seed instructions](#8-seed-instructions)
9. [Local MongoDB setup](#9-local-mongodb-setup)
10. [MongoDB Atlas setup](#10-mongodb-atlas-setup)
11. [Aggregation examples](#11-aggregation-examples)
12. [Query examples](#12-query-examples)
13. [Spring Boot integration notes](#13-spring-boot-integration-notes)
14. [Backup considerations](#14-backup-considerations)
15. [Security recommendations](#15-security-recommendations)
16. [Performance considerations](#16-performance-considerations)
17. [File map](#17-file-map)

---

## 1. Database overview

**Database name:** `portfoliopilot`

```
mongodb://localhost:27017/portfoliopilot                 # local
mongodb+srv://<user>:<pw>@<cluster>.mongodb.net/...      # Atlas
```

Configuration comes from two environment variables and nothing else:

| Variable       | Purpose                    | Default                     |
| -------------- | -------------------------- | --------------------------- |
| `MONGODB_URI`  | Cluster connection string  | `mongodb://localhost:27017` |
| `MONGODB_DB`   | Database name              | `portfoliopilot`            |

No credential is hardcoded anywhere in this package. Copy `.env.example` to `.env` for local work;
`.env` is git-ignored.

### Architecture

```
                                MongoDB / portfoliopilot
                                          │
        ┌───────────────┬─────────────────┼──────────────────┬───────────────────┐
        │               │                 │                  │                   │
      users ─1:1─→  profiles          projects        portfolioTemplates   skillDictionary
        │               (skills,          (referenced by            │          (global
        │                education,        everything below)        │           alias table)
        │                experience,            │                   │
        │                certificates           │                   │
        │                embedded)              │                   │
        │                                       │                   │
        └──1:N──→  jobAnalyses ────ref──────────┘                   │
                        │  (job snapshot + scores + skill gaps      │
                        │   + recommendedProjects[projectId])       │
                        │                                           │
              ┌─────────┴──────────┐                                │
              │                    │                                │
           resumes            portfolios ──────────ref──────────────┘
        (jobAnalysisId,      (username, sections,
         snapshot + refs)     orderedSkills, orderedProjects,
                              isPublished)

   support:  adminLogs (audit)     refreshTokens (revocable sessions, TTL)
```

### The one rule that shapes everything

> **Adapting a portfolio for a job must never mutate the user's profile.**

`profiles` and `projects` hold the single source of truth. A `portfolio` stores only a *template
choice*, *section toggles* and *ordering* (`orderedSkills`, `orderedProjects`). A `resume` stores an
immutable *snapshot* because it is a delivered artifact. Nothing job-specific ever writes back into
the profile.

---

## 2. Collection list

| Collection           | Required? | Purpose                                                    | Growth        |
| -------------------- | --------- | ---------------------------------------------------------- | ------------- |
| `users`              | core      | Auth identity: email, username, bcrypt hash, role, status  | 1 / user      |
| `profiles`           | core      | The one canonical CV dataset (skills, education, …)        | 1 / user      |
| `projects`           | core      | Portfolio projects, referenced everywhere                  | N / user      |
| `jobAnalyses`        | core      | Immutable record of every "Analyze Opportunity"            | **fastest**   |
| `resumes`            | core      | Generated job-targeted resumes                             | N / user      |
| `portfolios`         | core      | Portfolio configurations (draft / published / job-specific)| N / user      |
| `portfolioTemplates` | core      | Admin-managed layout catalogue                             | ~10 total     |
| `skillDictionary`    | added     | Skill alias → canonical mapping                            | ~500 total    |
| `adminLogs`          | added     | Append-only audit trail for privileged actions             | slow, TTL'd   |
| `refreshTokens`      | added     | Revocable server-side sessions, self-expiring              | TTL-bounded   |

### Why the three extra collections exist

**`skillDictionary`** — Without it the product is quietly broken. A user with `"ReactJS"` would show
a skill *gap* for a job asking `"React.js"`, and the admin "top skill gaps" chart would split one
real skill across three bars. It is shared reference data, so it cannot be embedded anywhere.

**`adminLogs`** — The admin panel can suspend and delete accounts. An audit trail is not optional
for destructive privileged actions.

**`refreshTokens`** — A stateless access token cannot be revoked. This gives the backend
"log out everywhere" and forced invalidation on suspension. *Storage only* — issuing and validating
tokens is the backend's job.

### Collections deliberately NOT created

**`analytics`** — Every admin metric in the spec is derivable from `users`, `jobAnalyses` and
`portfolios` with an indexed aggregation (see `aggregations/`). A pre-aggregated collection would add
a write path, a staleness window and a consistency bug surface for no benefit at this scale. Revisit
only when `jobAnalyses` passes roughly 10M documents and the dashboard aggregation exceeds ~200ms —
at that point add a nightly rollup, not a live-updated counter.

---

## 3. Schema explanation

Every collection has a `$jsonSchema` validator in `schemas/*.schema.json`. Each file is a
self-contained descriptor:

```jsonc
{
  "collection": "users",
  "description": "why this collection is shaped this way",
  "validationLevel": "strict",
  "validationAction": "error",
  "validator": { "$jsonSchema": { /* ... */ } }
}
```

### `_id` / ObjectId strategy

* Every document uses a native **`ObjectId`** `_id`. No UUID strings, no auto-increment.
  ObjectIds are 12 bytes (vs 36 for a UUID string), index densely, and their leading 4 bytes encode
  the creation timestamp — which makes `_id` a usable secondary sort key.
* **Embedded array elements also carry `_id`** in `profiles.education`, `profiles.experience` and
  `profiles.certificates`. This lets the UI `PATCH`/`DELETE` a single entry with the positional `$`
  operator instead of rewriting the whole array (which loses concurrent edits).
* `skills` has **no** element `_id` — `normalizedName` is already its natural key.
* Seed data uses deterministic ids from `seed/ids.js` (prefix `1` = users, `2` = profiles, …) so the
  seed is idempotent and seeded rows are recognisable in logs. Application writes always use
  `new ObjectId()`.

### Skill data model

```jsonc
{
  "name":              "Spring Boot",   // exactly what the user typed — display value
  "normalizedName":    "spring boot",   // canonical key — ALL matching/analytics use this
  "proficiency":       "ADVANCED",      // BEGINNER | INTERMEDIATE | ADVANCED | EXPERT
  "yearsOfExperience": 2,
  "category":          "BACKEND"        // denormalised from skillDictionary
}
```

Normalisation (`lib/normalize.js#normalizeSkill`) lowercases, folds accents, converts `.` `/` `-` to
spaces, strips punctuation (keeping `+` and `#` for `c++` / `c#`) and collapses whitespace. The
result is then resolved through `skillDictionary` by `normalizedName` **or** `aliases`:

```
"React.js"  → normalize → "react js"  → alias hit → canonical "React" / key "react"
"Mongo DB"  → normalize → "mongo db"  → alias hit → canonical "MongoDB" / key "mongodb"
"Spring-Boot" → normalize → "spring boot" → direct hit → "Spring Boot" / "spring boot"
```

### Duplicate-skill prevention

JSON Schema **cannot** express "unique on one field inside an array of objects". So every profile
also stores a derived array:

```jsonc
"skillIndex": ["java", "spring boot", "react", ...]   // uniqueItems: true
```

`uniqueItems: true` on `skillIndex` enforces the rule **at the database level** — an insert with a
duplicate skill fails, it does not silently succeed. The same array doubles as a multikey index
target for "which users have skill X". It must be rebuilt on every skills mutation
(`lib/normalize.js#buildSkillIndex`).

`projects.techStackNormalized` uses the identical pattern.

### Enums

Defined once in `lib/enums.js` and mirrored (by value) inside each validator, because a MongoDB
validator must be a self-contained document.

| Enum             | Values                                                        |
| ---------------- | ------------------------------------------------------------- |
| role             | `USER` `ADMIN`                                                 |
| status           | `ACTIVE` `SUSPENDED` `DELETED`                                 |
| proficiency      | `BEGINNER` `INTERMEDIATE` `ADVANCED` `EXPERT`                  |
| employmentType   | `INTERNSHIP` `FULL_TIME` `PART_TIME` `FREELANCE` `PROJECT`     |
| resume template  | `ATS_CLASSIC` `ATS_COMPACT` `ATS_MODERN`                       |
| portfolio section| `about` `skills` `projects` `education` `experience` `certificates` `contact` |
| skill category   | `FRONTEND` `BACKEND` `DATABASE` `DEVOPS` `CLOUD` `MOBILE` `TESTING` `DATA_AI` `TOOLS` `LANGUAGE` `SOFT_SKILL` `OTHER` |

---

## 4. Relationship explanation

The rule applied throughout: **embed what is small, bounded and owned; reference what is large,
unbounded, shared or independently queried.**

| Relationship                         | Decision      | Reasoning |
| ------------------------------------ | ------------- | --------- |
| `users` → `profiles`                 | **reference**, 1:1 | Login reads `users` on every request. Keeping the 20-field CV out of that document keeps the hottest read tiny. Enforced by `uniq_userId`. |
| `profiles` → skills                  | **embed**     | Bounded (~200), meaningless outside the profile, always co-read on /profile, /analyzer, /builder. One read serves the page. |
| `profiles` → education / experience / certificates | **embed** | Same reasoning. Small, bounded, never shared, never queried on their own. |
| `users` → `projects`                 | **reference**, 1:N | Unbounded growth (a profile document must not creep toward 16MB), independently paginated and edited, and referenced from three other collections — embedding would force N-way duplication. |
| `users` → `jobAnalyses`              | **reference**, 1:N | Fastest-growing collection. A power user could produce hundreds; each carries up to 30k chars of raw JD. |
| `jobAnalyses` → job posting          | **embed snapshot** | A posting has no independent lifecycle here, and the raw text must stay frozen for audit and re-scoring. |
| `jobAnalyses` → recommended projects | **reference + score** | Stores `{ projectId, relevanceScore, reason }`. Copying the project body would duplicate large text/images per analysis and go stale on the next edit. Only `titleSnapshot` is copied, so a soft-deleted project still renders as readable history. |
| `jobAnalyses` → `resumes`            | **reference** (`resumes.jobAnalysisId`) | Mandatory link. It is what makes a resume "tailored" rather than generic, and answers *"which job was this resume created for?"*. `jobAnalyses.resumeId` is a denormalised back-pointer so /match-analysis avoids a second query; `resumes.jobAnalysisId` remains authoritative. |
| `resumes` → skills / education / experience / certificates | **embed snapshot** | A resume is a *delivered artifact*. An already-downloaded PDF must be reproducible byte-identically even after the profile changes. |
| `resumes` → projects                 | **reference + priority** | Project bodies are large and are re-read at render time so edits propagate; only ordering is frozen. |
| `users` → `portfolios`               | **reference**, 1:N | Drafts, published, and one per job. |
| `portfolios` → content               | **reference / ordering only** | The non-destructive rule. `orderedSkills` holds `normalizedName` strings, `orderedProjects` holds `ObjectId`s. Editing a project instantly updates every portfolio. |
| `portfolios` → `portfolioTemplates`  | **reference** | Templates are global; embedding would mean rewriting every portfolio on a template tweak. `templateKey` is denormalised alongside so the public renderer needs no join. |
| `portfolios.username`                | **denormalised** from `users.username` | The public route must resolve in one indexed read with no join. The backend must rewrite it on every username change. |

---

## 5. Index strategy

37 indexes, each declared in `indexes/indexes.js` with a `why` and the exact `query` it optimises.
Compound keys follow **ESR** (Equality → Sort → Range). Verify with `npm run explain`.

### users

| Index | Type | Why |
| --- | --- | --- |
| `uniq_email` `{email:1}` | unique | Login identifier. The unique constraint is the only reliable defence against a duplicate-registration race — two concurrent `POST /register` both pass an app-level existence check. |
| `uniq_username` `{username:1}` | unique | Public URL segment; duplicates make `/portfolio/:username` ambiguous. |
| `role_status_createdAt` | compound | Admin Users table. ESR: equality on role+status, sort on createdAt. Also serves role-only filters via prefix. |
| `status_createdAt` | compound | Status-only filter is *not* a prefix of the above, so it would otherwise scan. Admins filter by `SUSPENDED` more often than by role. |
| `createdAt` | single | Signup-trend `$match` range. Without it the dashboard scans all users on every load. |
| `user_search_text` | text | Admin free-text search. An unanchored `/regex/i` can never use a b-tree. Only one text index is allowed per collection, so name/username/email are combined and weighted 5/3/1. |

### profiles

| Index | Type | Why |
| --- | --- | --- |
| `uniq_userId` | unique | Enforces 1:1 at the DB level; serves the hottest authenticated read. |
| `skillIndex_multikey` | multikey | "Which users have skill X" without unwinding every profile. |
| `updatedAt` | single | Admin recency listing + incremental backfill jobs (re-normalise skills, recompute health) paging in change order. |

### projects

| Index | Why |
| --- | --- |
| `userId_deleted_createdAt` | The /projects grid. Every project read is userId-scoped anyway (ownership), so userId leads. |
| `userId_includeInPortfolio_deleted` | Portfolio/public rendering only selects the opted-in subset — avoids fetching then discarding private projects. |
| `userId_techStack_multikey` | The Analyzer intersects JD skills with project stacks. Turns it into an indexed `$in` instead of loading all projects into the JVM. |
| `createdAt` | Admin reports only. Justified solely by the Reports/Analytics page. |

### jobAnalyses

| Index | Why |
| --- | --- |
| `userId_deleted_createdAt` | "Recent Analyses" on /dashboard — second-hottest authenticated query. |
| `createdAt` | Admin Job Analyses Log **and** the "jobs analyzed today" card. Both are pure date ranges over the fastest-growing collection. |
| `jobTitle_createdAt` | "Most analysed roles". Grouping on the pre-normalised title runs off an index scan instead of `$toLower` per document. |
| `userId_matchScore` | Best-match ranking. Sorting by score without it needs an in-memory sort that fails past the 100MB limit for heavy users. |
| `skillGaps_multikey` | Drill-down from the top-gaps chart ("who is missing Docker?"). The chart *itself* is served by `createdAt`. |
| `job_search_text` | Admin log search. `job.description` is deliberately excluded — indexing 30k-char postings would inflate the index enormously for negligible value. |

### portfolios — the important one

```js
db.portfolios.createIndex(
  { username: 1 },
  { unique: true, partialFilterExpression: { isPublished: true, deleted: false } }
)
```

A **partial unique index** expresses the business rule exactly: a user may keep many drafts and many
job-specific portfolios, but **at most one may be live** at `/portfolio/:username`.
A plain unique index would wrongly forbid drafts. An application-level check would lose a concurrent
double-publish race.

Plus `userId_deleted_updatedAt` (builder list), `isPublished_publishedAt` (admin card),
`sourceJobAnalysisId_sparse` (sparse — most portfolios are generic and store `null`),
and `templateId` (referential safety before an admin retires a template).

### resumes / portfolioTemplates / skillDictionary

`userId_deleted_createdAt`, `jobAnalysisId_createdAt` (**not** unique — users legitimately regenerate
after editing their profile, and older versions are useful history), `uniq_templateKey`,
`active_sortOrder`, `uniq_normalizedName`, `aliases_multikey` (alias resolution runs for *every* skill
token in *every* JD), `category_active`.

### TTL indexes

| Index | TTL | Effect |
| --- | --- | --- |
| `refreshTokens.expiresAt_ttl` | `expireAfterSeconds: 0` | Each row deleted at its own `expiresAt`. Session cleanup becomes a database responsibility — no cron, no unbounded growth. |
| `adminLogs.createdAt_ttl` | 2 years | Bounds audit growth. A single ascending index also satisfies the newest-first sort (MongoDB walks it backwards), so one index does both jobs. **If your jurisdiction requires longer audit retention, set `ADMIN_LOG_TTL_SECONDS = null` in `indexes/indexes.js` and archive externally.** |

### Verified

`npm run explain` runs `explain("executionStats")` over all 17 hot queries:

```
LOGIN               users.findOne({email})            IXSCAN  uniq_email
PROFILE             profiles.findOne({userId})        IXSCAN  uniq_userId
DASHBOARD           jobAnalyses recent 5              IXSCAN  userId_deleted_createdAt
PUBLIC PAGE         portfolios by username            IXSCAN  uniq_published_username
...
No collection scans. Every hot query is index-backed.
```

---

## 6. Validation strategy

**Application-level validation is not enough.** Bulk imports, migration scripts, an admin fixing data
in Compass, and a second service written later all bypass it. Constraints that matter belong in the
database.

* `validationLevel: "strict"`, `validationAction: "error"` — invalid writes are **rejected**, not logged.
* **Required fields** on every document.
* **BSON types** enforced — including `bsonType: "int"` for scores/counters (matching a Java `int`;
  note the Node driver defaults to `double`, so `seed/seed.js` wraps them in `Int32`).
* **Score ranges** — `matchScore`, `skillsMatch`, `projectsMatch`, `requirementsMatch`,
  `relevanceScore` are all `int` with `minimum: 0, maximum: 100`.
* **Enums** — every status/role/proficiency/type field.
* **Array bounds** — `maxItems` everywhere, so no single document can drift toward 16MB.
* **String bounds** — `maxLength` everywhere; `job.description` capped at 30 000 chars.
* **`uniqueItems`** on all derived normalised arrays.
* **`additionalProperties: false`** — unknown fields are rejected, so a typo'd field name fails loudly
  instead of silently creating a shadow field.
* **Regex patterns** where they encode a real invariant:

  ```jsonc
  "passwordHash": { "pattern": "^\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}$" }
  "username":     { "pattern": "^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$" }
  ```

  The first makes it **structurally impossible** to store a plaintext password.

### Deliberately allowed: `_class`

Spring Data MongoDB writes a `_class` type discriminator by default. Every validator explicitly
permits `"_class": { "bsonType": "string" }` so the Java backend works without disabling its type
mapper. (You can still remove it via a `MappingMongoConverter` with `DefaultMongoTypeMapper(null)`.)

### What validators cannot do

JSON Schema cannot compare two sibling fields. These stay in the backend service layer:

* `education.endYear >= startYear`
* `experience.endDate >= startDate`
* `portfolios.sections` keys ⊆ `portfolioTemplates.availableSections`
* cross-document referential integrity

`npm run verify` checks all of these after the fact, including four **negative tests** that prove the
validators actually reject bad documents.

---

## 7. Delete & soft-delete strategy

### Soft delete is the default

`users`, `projects`, `portfolios`, `jobAnalyses` and `resumes` all carry `deleted: boolean` +
`deletedAt`. **Every read path must filter `deleted: false`** — the compound indexes include the flag
precisely so this costs nothing.

Why soft delete for each:

* **`users`** — a hard delete orphans projects, analyses, resumes, portfolios and audit logs in one
  step, with no undo. Support needs "restore my account". `status: "DELETED"` mirrors the flag.
* **`projects`** — hard-deleting a project silently corrupts every historical resume and analysis that
  referenced it. Soft delete keeps `recommendedProjects[].titleSnapshot` meaningful.
* **`portfolios`** — a published URL may be indexed by search engines and pasted into applications.
  Unpublishing must be reversible.
* **`jobAnalyses` / `resumes`** — application history a user may want back.

**Not soft-deleted:** `adminLogs` (append-only by definition, bounded by TTL), `refreshTokens`
(hard-deleted by TTL; a revoked session must genuinely disappear), `portfolioTemplates` (uses
`active: false` instead — never hard-delete a referenced template).

### Cascade behaviour, and its consequences

Cascading deletes are **not** applied blindly. Each has a real cost:

| Event | Action | Consequence you must accept |
| --- | --- | --- |
| **User soft-deleted** | Set `deleted: true, status: "DELETED"` on the user. Set `deleted: true` on their projects, analyses, resumes, portfolios. Set `isPublished: false` on portfolios. Hard-delete their `refreshTokens`. | Data is retained and billable in storage. The email/username stay reserved, so the person cannot re-register with the same address until purged. |
| **User purged** (GDPR erasure) | Hard-delete `profiles`, `projects`, `jobAnalyses`, `resumes`, `portfolios`, `refreshTokens`, then the `users` row. **Keep `adminLogs`**, with `targetUserId` retained but `metadata` scrubbed. | Irreversible. Admin analytics lose those rows, so historical counts change retroactively. Keeping the audit log is intentional: an erasure request must itself be auditable. |
| **Project soft-deleted** | Set `deleted: true`. Do **not** touch analyses or resumes. | Renderers must skip missing/deleted projects. `titleSnapshot` keeps history readable. Portfolio `orderedProjects` may contain ids that no longer resolve — the public pipeline already filters them. |
| **Job analysis soft-deleted** | Set `deleted: true`. Cascade to its resumes. Do **not** delete the derived portfolio — set `sourceJobAnalysisId: null` instead. | A live public portfolio must never disappear because the user tidied their analysis history. |
| **Portfolio deleted** | Set `deleted: true, isPublished: false`. Nothing else. | The URL 404s. Profile, projects and resumes are untouched. |
| **Template deactivated** | Set `active: false`. Never hard-delete while `portfolios.templateId` references it (check via the `templateId` index). | Existing portfolios keep rendering; the template only disappears from the picker. |

Because MongoDB has no foreign keys, `npm run verify` exists to police all of this. Run it in CI.

### Purge job

Documents with `deleted: true` and `deletedAt` older than your retention window (90 days is a
reasonable default) should be hard-deleted by a scheduled backend job. Do **not** put a TTL index on
`deletedAt` — TTL is silent and irreversible, and a bug that sets `deletedAt` wrongly would destroy
live data with no alert.

---

## 8. Seed instructions

```bash
cd mongodb
npm install
cp .env.example .env        # edit MONGODB_URI / MONGODB_DB if needed
```

| Command | What it does | Safe in production? |
| --- | --- | --- |
| `npm run setup` | Collections + validators + indexes. **No data.** | **Yes** — idempotent, run it on every deploy |
| `npm run schemas` | Validators only (`createCollection` / `collMod`) | Yes |
| `npm run indexes` | Indexes only | Yes |
| `npm run seed` | `setup` **+ demo data** | **No — development only** |
| `npm run verify` | Integrity audit; non-zero exit on failure | Yes (read-only except 4 negative-test inserts) |
| `npm run stats` | Runs every analytics pipeline and prints results | Yes (read-only) |
| `npm run explain` | `explain()` over all hot queries | Yes (read-only) |
| `npm run reset` | **Drops** all managed collections | No — requires `ALLOW_DESTRUCTIVE=true` |

### Idempotency

Every seed write is `updateOne({_id: <deterministic>}, {$set}, {upsert: true})` using fixed ObjectIds
from `seed/ids.js`. Re-running converges to the same state — it never duplicates.

`createdAt` is written with `$setOnInsert`, so re-seeding preserves original timestamps. If you want
fresh dates (e.g. so "jobs analyzed today" is non-zero), run `npm run reset` first.

### What gets seeded

```
users               5   1 admin, 3 active users, 1 suspended
profiles            5   skills resolved through skillDictionary
projects            9   including one with includeInPortfolio: false
jobAnalyses        10   spread over 30 days, 2 of them today
resumes             4   each linked to its source analysis
portfolios          4   3 published + 1 draft (proves the partial unique index)
portfolioTemplates  5   4 active + 1 retired
skillDictionary    40   canonical skills with aliases
adminLogs           4   including the suspension of the suspended user
```

### Demo credentials — development only

| Account | Email | Password |
| --- | --- | --- |
| Admin | `admin@portfoliopilot.local` | `DemoPass123!` (from `SEED_PASSWORD`) |
| Demo student | `demo@portfoliopilot.local` | `DemoPass123!` |
| Extra users | `aarav@`, `priya@`, `rohan@` `portfoliopilot.local` | same |

Public portfolio: **`/portfolio/demo-student`**

The `.local` TLD is non-routable, so these can never collide with a real inbox. Passwords are bcrypt-
hashed at seed time using `SEED_BCRYPT_ROUNDS` — keep that equal to your Spring Security
`BCryptPasswordEncoder` strength so seeded users can log in against the real backend.

The demo profile intentionally contains messy skill spellings (`"React.js"`, `"Spring-Boot"`,
`"Mongo DB"`, plus a duplicate `"ReactJS"`) to prove the normaliser, the dictionary and the
`skillIndex` uniqueness rule all work.

---

## 9. Local MongoDB setup

**Community Server**

```bash
# macOS
brew tap mongodb/brew && brew install mongodb-community && brew services start mongodb-community

# Ubuntu/Debian — see https://www.mongodb.com/docs/manual/administration/install-on-linux/
sudo systemctl start mongod

# Windows — install MongoDB Community Server, it registers a "MongoDB" service
```

**Docker (recommended for dev — disposable)**

```bash
docker run -d --name portfoliopilot-mongo -p 27017:27017 \
  -v portfoliopilot-data:/data/db mongo:8
```

Then:

```bash
cd mongodb
npm install
npm run seed
npm run verify
```

**Minimum server version:** 6.0.
Two pipelines use `$sortArray` (5.2+): `skillGapsByCategoryPipeline` and the skill ordering inside
`publicPortfolioPipeline`. On older servers, drop those stages and sort in the application layer —
both are commented accordingly. `$unionWith` in `dashboardOverviewPipeline` needs 4.4+.

---

## 10. MongoDB Atlas setup

1. **Create a cluster** — [cloud.mongodb.com](https://cloud.mongodb.com) → Build a Database.
   M0 (free) is fine for development. Choose the region closest to your backend, not to you.

2. **Create a database user** — Database Access → Add New Database User.
   Use **SCRAM** auth and grant the *minimum* role:

   | Environment | Role |
   | --- | --- |
   | Application runtime | `readWrite` **on `portfoliopilot` only** — never `atlasAdmin`, never `readWriteAnyDatabase` |
   | Migration / setup job | `dbAdmin` + `readWrite` on `portfoliopilot` (needed for `collMod` and `createIndex`) |
   | Analytics / BI | `read` on `portfoliopilot` |

   Generate a long random password. If it contains `@ : / ? # [ ] %`, **URL-encode it** in the URI.

3. **Configure network access** — Network Access → IP Access List.
   Add only your backend's egress IP or a VPC peering / Private Endpoint.
   **Never add `0.0.0.0/0`.** For local development add your own IP with an expiry.

4. **Get the connection string** — Connect → Drivers:

   ```
   mongodb+srv://<user>:<password>@<cluster>.<hash>.mongodb.net/?retryWrites=true&w=majority&appName=portfoliopilot
   ```

5. **Set it as an environment variable** — never in a file that git tracks:

   ```bash
   export MONGODB_URI='mongodb+srv://...'
   export MONGODB_DB='portfoliopilot'
   ```

   In production, inject it from a secret manager (AWS Secrets Manager, Vault, Kubernetes Secret,
   Render/Railway env vars). Atlas connections are **TLS-encrypted by default** — do not disable it.

6. **Provision and connect**

   ```bash
   MONGODB_URI='mongodb+srv://...' npm run setup     # structure only, prod-safe
   MONGODB_URI='mongodb+srv://...' npm run verify
   ```

7. **Enable the safety nets** — Atlas → Backup (continuous or daily snapshots), Alerts (connection
   count, disk usage, slow queries), and the Performance Advisor.

---

## 11. Aggregation examples

All pipelines live in `aggregations/` as functions returning **plain arrays**, so they port 1:1 into
Spring Data. Run them all with `npm run stats`.

### Total users

```js
db.users.aggregate([
  { $match: { role: 'USER', deleted: false } },
  { $count: 'value' }
])
```

### Daily signups (line chart)

```js
db.users.aggregate([
  { $match: { createdAt: { $gte: ISODate('2026-07-23') }, deleted: false } },
  { $group: {
      _id: { $dateToString: { format: '%Y-%m-%d', date: '$createdAt', timezone: 'Asia/Kolkata' } },
      signups: { $sum: 1 }
  }},
  { $project: { _id: 0, date: '$_id', signups: 1 } },
  { $sort: { date: 1 } }
])
```

> The lower bound is computed **in the driver**, not with `$dateTrunc` inside the pipeline. A literal
> `Date` is an index bound; a computed expression is not, and degrades to a collection scan.
> Days with zero signups produce no row — `signupTrends.js#fillGaps` inserts the zeros for the chart.

### Jobs analyzed today

```js
db.jobAnalyses.aggregate([
  { $match: { deleted: false, createdAt: { $gte: <local midnight>, $lt: <local midnight + 1d> } } },
  { $count: 'value' }
])
```

### Average match score

```js
db.jobAnalyses.aggregate([
  { $match: { deleted: false, createdAt: { $gte: <30d ago> } } },
  { $group: { _id: null, value: { $avg: '$analysis.matchScore' }, samples: { $sum: 1 } } },
  { $project: { _id: 0, value: { $round: [{ $ifNull: ['$value', 0] }, 0] }, samples: 1 } }
])
```

### Top skill gaps — the flagship admin chart

```js
db.jobAnalyses.aggregate([
  { $match: { deleted: false, createdAt: { $gte: <90d ago> } } },      // 1. indexed window
  { $project: { userId: 1, gaps: '$analysis.skillGapsNormalized' } },  // 2. shrink before $unwind
  { $unwind: '$gaps' },                                                // 3. one row per gap
  { $group: {                                                          // 4. collapse by skill
      _id: '$gaps',
      occurrences: { $sum: 1 },
      users: { $addToSet: '$userId' }
  }},
  { $project: { _id: 0, skill: '$_id', occurrences: 1, usersAffected: { $size: '$users' } } },
  { $sort: { usersAffected: -1, occurrences: -1 } },                   // 5. rank
  { $limit: 10 }                                                       // 6. top N
])
```

Two details that matter:

* It groups on `skillGapsNormalized`, never the display array — otherwise "React", "React.js" and
  "ReactJS" become three separate bars.
* It ranks by **`usersAffected`**, not raw occurrences. One power user analysing 50 Docker jobs must
  not outweigh 20 distinct users who each lack Docker once.

Actual output from the seeded database:

```
{ skill: 'docker', occurrences: 7, usersAffected: 3 }
{ skill: 'aws',    occurrences: 6, usersAffected: 3 }
{ skill: 'redis',  occurrences: 3, usersAffected: 2 }
```

### Most requested skills

`$setUnion` of strong skills and gaps → everything the market asked for, with a `gapRatio`:

```
{ skill: 'docker', demandCount: 7, gapCount: 7, gapRatio: 100 }   // nobody has it
{ skill: 'react',  demandCount: 5, gapCount: 1, gapRatio:  20 }   // most people do
```

### Most analysed job roles

Groups on `job.normalizedTitle`, so `"Full Stack Engineer (MERN)"` and
`"full stack engineer"` land in one bucket.

### Published portfolios

```js
db.portfolios.aggregate([{ $match: { isPublished: true, deleted: false } }, { $count: 'value' }])
```

### All four stat cards in one round trip

`dashboardOverviewPipeline()` uses `$unionWith` to pull `users` and `portfolios` into a pipeline
rooted at `jobAnalyses`, returning `[{ metric, value }, ...]`. Requires 4.4+. On 4.2, run the four
pipelines in parallel instead (`runDashboardStats` does exactly that).

### Skill gaps by category

`$unwind` → `$group` → **then** `$lookup` into `skillDictionary`. The lookup runs *after* the group,
so it executes once per distinct skill (tens of rows) rather than once per analysis (thousands).
Stage ordering is the whole performance story here.

---

## 12. Query examples

### Auth

```js
db.users.findOne({ email: 'demo@portfoliopilot.local', deleted: false })   // uniq_email
db.users.findOne({ username: 'demo-student' })                             // uniq_username
db.users.updateOne({ _id: uid }, { $set: { lastLoginAt: new Date(), failedLoginAttempts: 0 } })
```

### Dashboard

```js
db.profiles.findOne({ userId: uid })                                       // uniq_userId
db.projects.find({ userId: uid, deleted: false }).sort({ createdAt: -1 })
db.jobAnalyses.find({ userId: uid, deleted: false }).sort({ createdAt: -1 }).limit(5)
db.portfolios.findOne({ userId: uid, isPublished: true, deleted: false })
```

### Opportunity Analyzer

```js
// 1. profile + skills (one document)
db.profiles.findOne({ userId: uid })

// 2. candidate projects, pre-filtered by the skills extracted from the JD
db.projects.find({
  userId: uid,
  deleted: false,
  techStackNormalized: { $in: ['java', 'spring boot', 'mongodb'] }
})

// 3. resolve each JD token to a canonical skill
db.skillDictionary.findOne({ $or: [{ normalizedName: t }, { aliases: t }] })
```

### Public portfolio (no auth)

```js
db.portfolios.findOne({ username: 'demo-student', isPublished: true, deleted: false })
```

One indexed hit on the partial unique index. `aggregations/publicPortfolio.js` provides two variants:

* `publicPortfolioPipeline(username)` — one round trip, `$lookup`s profile + template + ordered
  projects, and ends with an **explicit allow-list `$project`**.
* `fetchPublicPortfolioSimple(db, username)` — three tiny indexed `find()`s, individually cacheable.

> **Security:** the final `$project` is an allow-list, never an exclusion. With an exclusion
> projection, any field added to `profiles` later would leak to the public internet by default.
> `npm run stats` asserts that no credential or account field appears in the output.

### Admin

```js
db.users.find({ $text: { $search: 'priya' } }, { score: { $meta: 'textScore' } })
db.users.find({ role: 'USER', status: 'ACTIVE' }).sort({ createdAt: -1 }).skip(0).limit(25)
db.jobAnalyses.find({}).sort({ createdAt: -1 }).limit(25)
db.portfolios.countDocuments({ templateId: tid, deleted: false })   // before retiring a template
```

> **Pagination:** `skip/limit` is fine for the admin table (bounded page counts, arbitrary jumps).
> For user-facing infinite scroll use range pagination —
> `{ userId, createdAt: { $lt: lastSeenCreatedAt } }` — because `skip(N)` still walks N index entries.

---

## 13. Spring Boot integration notes

This package is backend-agnostic, but a few things are deliberately shaped for Spring Data MongoDB.

```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI}
      database: ${MONGODB_DB:portfoliopilot}
      auto-index-creation: false     # IMPORTANT
```

* **Turn off `auto-index-creation`.** Index definitions belong in `indexes/indexes.js`, versioned and
  reviewed. Letting `@Indexed` annotations build indexes at startup means production index builds
  happen implicitly, at deploy time, with no rationale recorded.
* **`_class` is allowed** by every validator (see §6).
* **Mirror the enums** from `lib/enums.js` as Java `enum`s; Spring persists the *name* by default.
  Do not switch to ordinals — validators only accept strings.
* **Use `int`, not `Integer`/`long`,** for scores and counters so the driver writes BSON `int32` and
  matches `bsonType: "int"`.
* **Port `normalizeSkill()` verbatim** into a Java utility. It must be byte-for-byte identical, or
  seeded and runtime data will not join.
* **Rebuild derived fields on every write:** `profiles.skillIndex`,
  `projects.techStackNormalized`, `experience[].technologiesNormalized`,
  `job.normalizedTitle`, `analysis.skillGapsNormalized`, `analysis.strongSkillsNormalized`.
  These are what every aggregation and index depends on.
* **`generateMatch` output contract** — `seed/mockMatch.js` is *not* the scoring engine, but its
  return shape is the contract the frontend expects:

  ```
  { matchScore, skillsMatch, projectsMatch, requirementsMatch,
    strongSkills, skillGaps, recommendedProjects, tailoredSummary }
  ```

* **Always scope by `userId`** on `projects`, `jobAnalyses`, `resumes` and `portfolios`. Never fetch
  by `_id` alone — that is the IDOR hole. The indexes are built assuming `userId` leads.
* **Run `npm run setup` before the app starts**, and `npm run verify` in CI.

---

## 14. Backup considerations

| Environment | Approach |
| --- | --- |
| **Atlas M10+** | Enable **Continuous Cloud Backup** (point-in-time restore, oplog-based). Set the retention window to at least 7 days. |
| **Atlas M0/M2/M5** | Continuous backup is unavailable. Schedule `mongodump` from CI to encrypted object storage. |
| **Self-hosted** | `mongodump --uri="$MONGODB_URI" --db=portfoliopilot --gzip --archive=pp-$(date +%F).gz` on a cron, shipped off-host. |

```bash
# restore into an isolated database first — never straight over production
mongorestore --uri="$MONGODB_URI" --gzip --archive=pp-2026-08-21.gz \
             --nsFrom='portfoliopilot.*' --nsTo='portfoliopilot_restore.*'
```

Points that are easy to get wrong:

* **Untested backups are not backups.** Restore into a scratch database quarterly and run
  `npm run verify` against it. That is the actual test.
* **Validators and indexes are collection metadata** — `mongodump` captures them, but a restore that
  recreates collections implicitly may not. Always run `npm run setup` after a restore.
* **Avatars, project images and generated PDFs are NOT in MongoDB** — only their URLs are. Your object
  storage needs its own backup and its own lifecycle policy, or a database restore yields a portfolio
  full of broken images.
* **Before any migration**, snapshot first. Schema changes applied via `collMod` are not reversible
  by re-running an older script if documents were rewritten in between.

---

## 15. Security recommendations

**Never:**

* commit `.env`, a real connection string, or any password (`.gitignore` covers this — keep it)
* use `0.0.0.0/0` in the Atlas IP access list
* grant `atlasAdmin` / `readWriteAnyDatabase` to the application user
* store a password as anything but a bcrypt hash (the `passwordHash` regex enforces this)
* store a refresh token in plaintext — only its SHA-256 hash is persisted
* expose raw `ObjectId`s in public URLs (the public route uses `username`, not `_id`)
* disable TLS

**Do:**

| Control | Implementation |
| --- | --- |
| Least privilege | One DB user per role: app = `readWrite` on `portfoliopilot`; migrations = `+dbAdmin`; BI = `read` |
| Secrets | Environment variables injected from a secret manager. `getConfig()` is the only reader; `redactUri()` masks credentials in every log line |
| Network | Atlas IP access list, or VPC peering / Private Endpoint. Self-hosted: bind to a private interface, never `0.0.0.0` |
| Encryption | TLS in transit (Atlas default). Encryption at rest (Atlas default). Consider Client-Side Field Level Encryption for `contact.phone` if you ever store sensitive PII |
| Password storage | bcrypt only, enforced by the validator regex, verified by `npm run verify` |
| Session revocation | `refreshTokens` stores SHA-256 hashes with a TTL; suspending a user must delete their token rows |
| Brute force | `users.failedLoginAttempts` + `lockedUntil` — backend must reject auth while `lockedUntil > now` |
| Audit | `adminLogs` for every privileged action. `metadata` must never contain passwords, hashes or tokens |
| Public data | Allow-list `$project` on the public portfolio pipeline. Account email, phone and all auth fields are omitted |
| Injection | Always pass user input as **values**, never as query operators. Reject request keys starting with `$` or containing `.` at the API boundary |
| Rotation | Rotate the database password on a schedule and after any team change |

---

## 16. Performance considerations

**Working set / document size**

* All embedded arrays are `maxItems`-bounded, so no document can drift toward the 16MB limit.
  A realistic `profiles` document is 3–8KB; `jobAnalyses` is dominated by the JD text (capped at 30k).
* **No binary in MongoDB.** Avatars, project images and generated PDFs are object-storage URLs.
  Storing them inline would multiply the working set and slow every unrelated read.

**Query shape**

* Every hot query is index-backed — proved by `npm run explain`, not assumed.
* `userId` leads every user-scoped compound index. That is also the security boundary, so the fast
  path and the safe path are the same path.
* `deleted` is part of the compound keys, so soft-delete filtering is free rather than a post-filter.

**Aggregations**

* Every pipeline starts with an indexed `$match`. Date boundaries are computed in the driver so they
  become index bounds.
* `$project` before `$unwind` — shrink documents before multiplying them.
* `$lookup` after `$group` — join tens of rows, not thousands.
* Admin analytics are on-demand and cacheable for a few minutes; none of them sit on a user request
  path. `conversionFunnelPipeline` touches four collections — cache it, do not poll it.

**Scaling path, in the order you should actually do it**

1. Cache `portfolioTemplates` and `skillDictionary` in the application (tiny, read-heavy, rarely written).
2. Put the public portfolio route behind a CDN with a short TTL; it is the highest-volume read and
   needs no auth.
3. Cache admin dashboard results for 1–5 minutes.
4. Only when `jobAnalyses` exceeds ~10M documents: add a nightly rollup collection for the charts —
   still not a live-updated counter.
5. Only after that: consider sharding on `userId` (hashed). Every user-scoped query already carries
   `userId`, so it is a natural shard key. `jobAnalyses` would shard first.

**Monitoring**

Enable the Atlas Performance Advisor and profile slow queries (`>100ms`). Re-run `npm run explain`
against production-sized data — on a small seeded dataset the planner may legitimately prefer a
collection scan, so index verification only means something at scale.

---

## 17. File map

```
mongodb/
├── README.md                          this document
├── package.json                       scripts + driver/bcrypt deps
├── .env.example                       configuration template (no real secrets)
├── .gitignore                         blocks .env, node_modules, dumps
│
├── lib/
│   ├── env.js                         config loader + credential redaction
│   ├── db.js                          connection helper, script runner
│   ├── enums.js                       canonical enum values (mirror in Java)
│   ├── normalize.js                   skill/username/title normalisation  ← port to Java
│   └── dates.js                       date-window helpers for aggregations
│
├── schemas/                           $jsonSchema validators, one per collection
│   ├── index.js                       registry, creation order
│   ├── users.schema.json
│   ├── profiles.schema.json
│   ├── projects.schema.json
│   ├── jobAnalyses.schema.json
│   ├── resumes.schema.json
│   ├── portfolios.schema.json
│   ├── portfolioTemplates.schema.json
│   ├── skillDictionary.schema.json
│   ├── adminLogs.schema.json
│   └── refreshTokens.schema.json
│
├── indexes/
│   └── indexes.js                     37 indexes, each with why + query
│
├── aggregations/
│   ├── index.js
│   ├── dashboardStats.js              stat cards, sub-scores, distribution
│   ├── signupTrends.js                daily signups/analyses/publishes, funnel
│   ├── skillGaps.js                   top gaps, most requested, roles, by category
│   └── publicPortfolio.js             /portfolio/:username resolution (allow-list)
│
├── seed/
│   ├── seed.js                        idempotent orchestrator
│   ├── ids.js                         deterministic ObjectIds
│   ├── mockMatch.js                   seed-only scorer (NOT the product engine)
│   └── data/
│       ├── skillDictionary.js         40 canonical skills + aliases
│       ├── portfolioTemplates.js      5 templates (4 active, 1 retired)
│       └── demoData.js                accounts, profiles, projects, jobs
│
└── scripts/
    ├── setup.js                       schemas + indexes (production-safe)
    ├── applySchemas.js                create/collMod validators
    ├── reset.js                       DESTRUCTIVE, triple-guarded
    ├── verify.js                      integrity audit + negative tests
    ├── runAggregations.js             execute and print all analytics
    └── explain.js                     query-plan audit over hot queries
```

---

## Quality checklist

| Item | Status |
| --- | --- |
| Database name configured (`portfoliopilot`, local + Atlas) | done |
| All required collections designed | done — 7 core + 3 justified additions |
| ObjectId strategy defined | done — native ObjectId, `_id` on embedded array elements |
| User / Profile / Skills / Projects schemas | done |
| Education / Experience / Certificates supported | done — embedded, with element `_id`s |
| Job analyses, match scores, skill gaps, recommendations | done — scores are int 0..100, validated |
| Tailored resumes, tailored portfolios, public portfolios | done — snapshot vs reference documented per field |
| Portfolio templates, admin logs | done |
| Indexes created and justified | done — 37, each with `why` + `query`; verified by `npm run explain` |
| Validation rules | done — strict `$jsonSchema` on all 10 collections, proved by negative tests |
| Aggregation pipelines | done — 15 pipelines across 4 modules |
| Seed data | done — idempotent, 5 users, 10 analyses, 4 portfolios |
| MongoDB Atlas compatible | done — `mongodb+srv`, TLS, least-privilege roles documented |
| Delete / soft-delete strategy | done — cascade table with consequences, §7 |
| Security documented | done — §15 |
| Backup documented | done — §14 |
| Performance considered | done — §16, plus `npm run explain` showing zero collection scans |
| README | this file |

Verified against **MongoDB 8.2** — `npm run seed` → `npm run verify` reports **ALL CHECKS PASSED**
(70 checks: structure, 37 indexes, 12 referential-integrity joins, 4 business rules, derived-field
sync, 4 validator negative tests), and `npm run explain` reports **no collection scans**.
