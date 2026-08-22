# PortfolioPilot AI — Spring Boot Backend

REST API connecting the existing static frontend to the existing MongoDB database.

> `ONE PROFILE → MANY OPPORTUNITIES → MATCH ANALYSIS → TAILORED RESUME → ADAPTIVE PORTFOLIO → PUBLISHED PORTFOLIO`

**Java 21 · Spring Boot 3.3.4 · Spring Data MongoDB · Spring Security + JWT · springdoc-openapi**

Neither the frontend nor the database was redesigned. This service adapts to both.

---

## 1. Quick start

```bash
# 1. Database (from the repo root)
cd mongodb
npm install
npm run seed            # collections + validators + indexes + demo data

# 2. Backend
cd ../backend
mvn spring-boot:run     # http://localhost:8080

# 3. Frontend (any static server — it must be http://, not file://)
cd ../portfoliopilot-ai
npx serve -l 5500       # http://localhost:5500
```

Then sign in at `http://localhost:5500/login.html` with:

| Account | Email | Password |
| --- | --- | --- |
| Demo user | `demo@portfoliopilot.local` | `DemoPass123!` |
| Admin | `admin@portfoliopilot.local` | `DemoPass123!` |

Swagger UI: **http://localhost:8080/swagger-ui.html**

> **`file://` will not work.** A page opened from disk sends `Origin: null` and every
> CORS request fails. Serve the frontend over HTTP.

---

## 2. Environment

Copy `.env.example`. Spring Boot does not read `.env` files, so export the variables
(or use your IDE's env config / a secret manager):

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `MONGODB_URI` | no | `mongodb://localhost:27017` | Same variable the `/mongodb` tooling uses |
| `MONGODB_DB` | no | `portfoliopilot` | Database name |
| `JWT_SECRET` | **yes in prod** | dev placeholder | HMAC key, min 32 bytes. `openssl rand -base64 48` |
| `JWT_EXPIRATION` | no | `900000` (15 min) | Access-token lifetime, ms |
| `JWT_REFRESH_EXPIRATION` | no | `2592000000` (30 d) | Refresh-token lifetime, ms |
| `FRONTEND_URL` | no | localhost dev ports | Comma-separated CORS origins |
| `SERVER_PORT` | no | `8080` | |
| `MATCH_*_WEIGHT` | no | `0.45 / 0.30 / 0.25` | Scoring weights; must sum to 1.0 |

The app **fails fast** if the weights do not sum to 1.0, and logs a loud warning if the
placeholder `JWT_SECRET` is in use or if the database has not been provisioned.

```bash
# PowerShell
$env:JWT_SECRET="$(openssl rand -base64 48)"; mvn spring-boot:run
# bash
JWT_SECRET="$(openssl rand -base64 48)" mvn spring-boot:run
```

---

## 3. Architecture

```
Controller  ← thin: HTTP mapping, validation, delegation
    ↓ DTO (Java records)
Service     ← all business logic + ownership enforcement
    ↓
Repository  ← Spring Data MongoDB
    ↓
MongoDB     ← existing `portfoliopilot` database
```

```
src/main/java/com/portfoliopilot/
├── config/       SecurityConfig, OpenApiConfig, StartupValidator, *Properties
├── controller/   13 controllers, all under /api  (admin/ sub-package)
├── dto/          auth · profile · project · opportunity · resume · portfolio · admin · common
├── model/        10 documents + embedded/ value types + enums/
├── repository/   10 Spring Data interfaces + AdminAnalyticsRepository (aggregations)
├── security/     JwtService, JwtAuthenticationFilter, UserPrincipal, SecurityUtils
├── service/      business logic; match/ holds the scoring engine; admin/ the admin services
├── exception/    typed exceptions + GlobalExceptionHandler
└── util/         SkillNormalizer  ← must stay in sync with mongodb/lib/normalize.js
```

### Collections used

Every collection is the one already provisioned by `/mongodb`. **This backend never
creates collections or indexes** — `spring.data.mongodb.auto-index-creation` is `false`,
because index definitions belong in `mongodb/indexes/indexes.js` where each carries a
documented rationale.

| Document | Collection |
| --- | --- |
| `User` | `users` |
| `Profile` | `profiles` |
| `Project` | `projects` |
| `JobAnalysis` | `jobAnalyses` |
| `Resume` | `resumes` |
| `Portfolio` | `portfolios` |
| `PortfolioTemplate` | `portfolioTemplates` |
| `SkillDictionaryEntry` | `skillDictionary` |
| `AdminLog` | `adminLogs` |
| `RefreshToken` | `refreshTokens` |

---

## 4. API reference

Every response uses one envelope:

```json
{ "success": true,  "message": "Profile updated", "data": { } }
{ "success": false, "message": "Validation failed", "errors": { "email": "..." } }
```

### Authentication

| Method | Endpoint | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | — | Create account + profile, return tokens |
| POST | `/api/auth/login` | — | Sign in |
| POST | `/api/auth/refresh` | — | Rotate the refresh token |
| POST | `/api/auth/logout` | user | Revoke this session |
| POST | `/api/auth/logout-all` | user | Revoke every session |
| GET | `/api/auth/me` | user | Current account |

### Dashboard, profile, projects

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/dashboard` | Stat cards + recent analyses in one call |
| GET | `/api/profile` | Profile (joined with account identity) |
| PUT / PATCH | `/api/profile` | Replace / partially update the personal block |
| PUT | `/api/profile/skills` | Replace the skill list (de-duplicated) |
| POST / DELETE | `/api/profile/skills[/{name}]` | Add / remove one skill |
| POST / PUT / DELETE | `/api/profile/education[/{id}]` | Education CRUD |
| POST / PUT / DELETE | `/api/profile/experience[/{id}]` | Experience CRUD |
| POST / DELETE | `/api/profile/certificates[/{id}]` | Certificates |
| GET | `/api/projects` | List (`?paged=false` for all) |
| GET / POST / PUT / DELETE | `/api/projects[/{id}]` | Project CRUD (soft delete) |
| PATCH | `/api/projects/{id}/portfolio?include=` | Toggle portfolio inclusion |

### The core flow

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/api/opportunities/analyze` | **Analyze a job description** |
| GET | `/api/opportunities/recent` | Dashboard list |
| GET | `/api/opportunities` | Paginated history |
| GET / DELETE | `/api/opportunities/{id}` | One analysis |
| POST | `/api/resumes/generate` | **Generate a tailored resume** |
| GET | `/api/resumes`, `/api/resumes/{id}` | List / fetch |
| GET | `/api/resumes/by-analysis/{id}` | Latest resume for an analysis |
| POST | `/api/portfolio/generate` | **Adapt a portfolio for a job** |
| GET / POST / PUT / DELETE | `/api/portfolio[/{id}]` | Portfolio CRUD |
| POST | `/api/portfolio/{id}/publish` / `/unpublish` | Go live / offline |
| GET | `/api/templates` | Template picker (public) |
| GET | `/api/public/portfolio/{username}` | **Published portfolio (no auth)** |

### Admin — all require `ROLE_ADMIN`

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/admin/dashboard/stats` | Four stat cards + distribution |
| GET | `/api/admin/dashboard/signup-trends?days=` | Gap-filled daily series |
| GET | `/api/admin/dashboard/skill-gaps?limit=` | Top gaps, demand, roles |
| GET | `/api/admin/users` | Search / filter / sort / paginate |
| GET | `/api/admin/users/{id}` | Full detail + audit trail |
| PATCH | `/api/admin/users/{id}/suspend` \| `/activate` | Moderation |
| DELETE | `/api/admin/users/{id}` | Soft delete + cascade |
| GET | `/api/admin/job-analyses` | Log with score/date filters |
| GET / POST / PUT / DELETE | `/api/admin/templates[/{id}]` | Template CRUD |
| PATCH | `/api/admin/templates/{id}/status?active=` | Activate / retire |

---

## 5. The match engine

`JobAnalysisService` is an interface; the shipped implementation is
`RuleBasedJobAnalysisService`. **The product never depends on an external AI API** — an
outage or an empty billing account must not break the core feature. A future
`AiJobAnalysisService` can replace or wrap it, and every stored analysis records which
engine produced it (`analysis.engine`) so scores stay comparable across a change.

```
finalScore = skillsMatch × 0.45 + projectsMatch × 0.30 + requirementsMatch × 0.25
```

**Skill extraction** is dictionary-driven n-gram matching, not `text.contains(skill)`:
the JD is normalised with the same function used for stored skills, its 1–4-word n-grams
are hashed, and every dictionary key (canonical + aliases) is tested for membership. That
avoids `contains("go")` firing on "going", "algorithm" and "Google". A skill that is a
strict sub-phrase of another match is dropped, so a posting asking for *Spring Boot* does
not also report a gap for *Spring Framework*.

Required vs nice-to-have comes from section headings; a "What we offer" block is skipped
entirely, so free snacks are never mistaken for a requirement.

Three properties are treated as non-negotiable:

* **Deterministic** — the same input always yields the same score. The original
  client-side mock called `Math.random()` three times.
* **Explainable** — every number traces to a listed skill or a quoted requirement line.
* **Honest** — no floor. The old mock clamped everything to ≥ 45%, which flattered
  every candidate and made the number worthless.

---

## 6. Security

| Control | Implementation |
| --- | --- |
| Authentication | Stateless JWT. Access token 15 min; opaque refresh token, SHA-256 hashed at rest, rotated on use |
| Reuse detection | Presenting an already-rotated refresh token revokes every session for that account |
| Authorization | `/api/admin/**` requires `ROLE_ADMIN` in `SecurityConfig` **and** `@PreAuthorize` on each admin controller |
| Ownership | Every user-scoped query filters by the `userId` from the **token**. A `userId` in a request body is ignored |
| IDOR | Another user's resource returns **404, not 403**, so ids cannot be probed |
| Enumeration | Unknown email and wrong password return the identical 401 |
| Passwords | BCrypt (strength 10). The `users` validator independently rejects any non-bcrypt value |
| Brute force | 5 failed attempts → 15-minute lockout |
| Suspension | Revokes all sessions immediately and unpublishes the portfolio |
| Public data | The public portfolio response is an explicit **allow-list** record — never an exclusion projection |
| Injection | Admin search terms go through `Pattern.quote`; sorting is restricted to an allow-list of fields |
| Logging | Tokens, passwords and hashes are never logged; emails are masked |

---

## 7. Testing

```bash
mvn test          # 54 unit tests, no database required
```

| Suite | Covers |
| --- | --- |
| `SkillNormalizerTest` (26) | Parity with `mongodb/lib/normalize.js` — the contract both sides depend on |
| `ProficiencyTest` (11) | 1–100 slider ↔ enum mapping, boundaries, round-trip |
| `RuleBasedJobAnalysisServiceTest` (17) | Extraction, section handling, sub-phrase de-duplication, determinism, no floor, weighted sum, recommendations |

Manual end-to-end verification (performed against MongoDB 8.2 — see the session log):

```
register → skills → project → analyze → resume → adapt portfolio → publish → public page
```

Security checks that were run and passed: no token → 401, bad token → 401, USER → admin
403, ADMIN → 200, cross-user project read → 404, wrong password → 401, unknown email →
401, weak password → 400, duplicate email → 409, unknown public username → 404, and a
leak scan of the public payload for `passwordHash` / `email` / `userId`.

---

## 8. Frontend integration

`portfoliopilot-ai/js/api.js` (new) is the only place that talks HTTP. `js/app.js` was
rewritten to call it, keeping the `window.PP` facade so the HTML pages needed almost no
changes.

**Why the accessors are still synchronous.** Every page does
`PP.initAppLayout(...); const profile = PP.getProfile();` inline. Making that async would
mean editing every call site in seven files. Instead `app.js` keeps a localStorage cache
that is warmed at login *before* the redirect, refreshed from the API in the background on
each page load, and written through on save. A `pp:data` event fires when a background
refresh changes something.

Point the client at a different backend without a rebuild:

```js
window.PP_API_BASE = 'https://api.example.com/api';   // before js/api.js
localStorage.setItem('pp_api_base', 'http://localhost:9090/api');
```

---

## 9. Known gaps

Stated plainly rather than left to be discovered:

1. **Four frontend pages are still missing** — `register.html`, `projects.html`,
   `analyzer.html`, `preview.html`. They were never in the repository, yet every sidebar
   links to them. The backend endpoints they need all exist and are tested
   (`POST /api/auth/register`, `/api/projects`, `/api/opportunities/analyze`,
   `/api/portfolio/{id}`), so building them is pure frontend work.
2. **Four admin pages are not rewired yet** — `job-logs.html`, `templates.html`,
   `reports.html`, `settings.html` still read the removed mock arrays. Empty-array stubs
   in `app.js` keep them from throwing, so they render empty tables instead of crashing.
   `admin/index.html` and `admin/users.html` are fully wired to the real API and can be
   used as the pattern. `reports.html` has hardcoded numbers in its markup;
   `settings.html` has no backing endpoint at all.
3. **`portfolio.html` is not parameterised.** It renders the current browser's
   localStorage rather than reading `/portfolio/:username`, so it is not yet shareable.
   `GET /api/public/portfolio/{username}` is implemented and verified — the page needs to
   read the username from the URL and call it.
4. **No PDF rendering server-side.** The API returns structured, ordered resume content;
   the frontend prints it. Adding a headless browser to the backend was judged not worth
   the deployment weight.
5. **Avatar upload accepts a URL, not a file.** Binary belongs in object storage, not in
   MongoDB documents.
6. **No integration tests against a live MongoDB.** The 54 tests are pure unit tests. The
   end-to-end flow was verified manually; automating it needs Testcontainers or an
   embedded mongod.

---

## 10. Production checklist

- [ ] Set a real `JWT_SECRET` (the app warns loudly if you have not)
- [ ] Set `FRONTEND_URL` to the exact origin — never `*`
- [ ] Run `cd mongodb && npm run setup` before first start (structure only, prod-safe)
- [ ] Use an Atlas user with `readWrite` on `portfoliopilot` only
- [ ] Restrict the Atlas IP access list; never `0.0.0.0/0`
- [ ] Put the API behind TLS
- [ ] Lock down `/swagger-ui.html` and `/v3/api-docs` or disable springdoc
- [ ] Cache `GET /api/public/portfolio/{username}` at the edge (it already sends `Cache-Control`)
- [ ] Ship logs somewhere; `/actuator/health` is available for probes
