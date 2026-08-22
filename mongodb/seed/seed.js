'use strict';

/**
 * IDEMPOTENT SEED SCRIPT
 * ======================
 *
 * Order of operations (each step depends on the previous one):
 *   1. apply JSON Schema validators  -> catches any bad seed document loudly
 *   2. create indexes                -> unique constraints active before writes
 *   3. skillDictionary               -> needed to normalise every skill below
 *   4. portfolioTemplates            -> portfolios reference them
 *   5. users                         -> bcrypt-hashed demo passwords
 *   6. profiles                      -> skills resolved through the dictionary
 *   7. projects                      -> techStackNormalized derived
 *   8. jobAnalyses                   -> scored by seed/mockMatch.js
 *   9. resumes                       -> reference their source jobAnalysis
 *  10. portfolios                    -> one published per user + one draft
 *  11. back-links + adminLogs
 *
 * SAFE TO RE-RUN. Every write is `updateOne({_id: <fixed>}, {$set}, {upsert})`
 * with deterministic ids from seed/ids.js, so re-running converges to the same
 * state instead of duplicating rows.
 *
 * Run:  npm run seed        (or  npm run setup  for schemas+indexes+seed)
 */

const { Int32 } = require('mongodb');
const bcrypt = require('bcryptjs');

const { runScript } = require('../lib/db');
const { getConfig } = require('../lib/env');
const { normalizeSkill, normalizeJobTitle, dedupeSkills, buildSkillIndex } = require('../lib/normalize');
const { applySchemas } = require('../scripts/applySchemas');
const { createIndexes } = require('../indexes/indexes');

const { ID } = require('./ids');
const { SKILL_DICTIONARY, buildLookup } = require('./data/skillDictionary');
const { PORTFOLIO_TEMPLATES } = require('./data/portfolioTemplates');
const { buildAccounts, buildJobDescription, ago } = require('./data/demoData');
const { generateMatch } = require('./mockMatch');

/**
 * The Node driver serialises every JS number as a BSON double. Our validators
 * demand `bsonType: "int"` for counters and scores - which is also what Spring
 * Boot writes for a Java `int`. Wrapping in Int32 keeps seeded documents
 * byte-compatible with backend-written ones.
 */
const i32 = (n) => new Int32(Math.round(n));

/** Upsert helper: set fields, but never overwrite the original createdAt. */
async function upsert(db, collection, _id, doc, createdAt) {
  const $set = { ...doc, updatedAt: doc.updatedAt || new Date() };
  delete $set.createdAt;

  await db.collection(collection).updateOne(
    { _id },
    {
      $set,
      $setOnInsert: { createdAt: createdAt || doc.createdAt || new Date() },
    },
    { upsert: true }
  );
}

/** Same, for immutable documents that carry createdAt but no updatedAt. */
async function upsertImmutable(db, collection, _id, doc) {
  const $set = { ...doc };
  const createdAt = $set.createdAt || new Date();
  delete $set.createdAt;

  await db.collection(collection).updateOne(
    { _id },
    { $set, $setOnInsert: { createdAt } },
    { upsert: true }
  );
}

// ---------------------------------------------------------------------------
// Skill resolution (dictionary-backed)
// ---------------------------------------------------------------------------

const DICT = buildLookup(SKILL_DICTIONARY);

/**
 * raw display string -> { name, normalizedName, category }
 * Mirrors exactly what the Spring Boot service must do on every skill write.
 */
function resolveSkill(raw) {
  const naive = normalizeSkill(raw);
  const entry = DICT.get(naive);
  return entry
    ? { name: entry.canonicalName, normalizedName: entry.normalizedName, category: entry.category }
    : { name: String(raw).trim(), normalizedName: naive, category: 'OTHER' };
}

/** normalised key -> canonical display name (falls back to the key itself). */
function displayFor(normalized) {
  const entry = DICT.get(normalized);
  return entry ? entry.canonicalName : normalized;
}

/** Map consumed by mockMatch for pretty output. */
const SKILL_DISPLAY = new Map(SKILL_DICTIONARY.map((e) => [e.normalizedName, { name: e.canonicalName }]));

/**
 * Profile completeness, cached on the profile so /dashboard needs no
 * aggregation. Weights mirror what the UI nudges users to complete.
 */
function computeProfileHealth(profile, projectCount) {
  const checks = [
    [!!profile.fullName, 10],
    [!!profile.professionalTitle, 10],
    [!!(profile.bio && profile.bio.length > 60), 15],
    [!!profile.location, 5],
    [!!profile.avatarUrl, 5],
    [profile.skills.length >= 5, 20],
    [profile.education.length >= 1, 15],
    [projectCount >= 2, 15],
    [!!(profile.contact && (profile.contact.github || profile.contact.linkedin)), 5],
  ];
  return checks.reduce((total, [ok, pts]) => total + (ok ? pts : 0), 0);
}

// ---------------------------------------------------------------------------
// Seed steps
// ---------------------------------------------------------------------------

async function seedSkillDictionary(db, now) {
  for (let i = 0; i < SKILL_DICTIONARY.length; i += 1) {
    const entry = SKILL_DICTIONARY[i];
    await upsert(
      db,
      'skillDictionary',
      ID.skill(i + 1),
      {
        canonicalName: entry.canonicalName,
        normalizedName: entry.normalizedName,
        aliases: entry.aliases,
        category: entry.category,
        relatedSkills: entry.relatedSkills || [],
        weight: entry.weight ?? 0.7,
        active: true,
      },
      now
    );
  }
  return SKILL_DICTIONARY.length;
}

async function seedTemplates(db, now) {
  for (const tpl of PORTFOLIO_TEMPLATES) {
    const { _id, sortOrder, ...rest } = tpl;
    await upsert(db, 'portfolioTemplates', _id, { ...rest, sortOrder: i32(sortOrder) }, now);
  }
  return PORTFOLIO_TEMPLATES.length;
}

/** Builds the embedded profile document for one account. */
function buildProfileDoc(account, userId, projectCount, now) {
  const p = account.profile;

  const skills = dedupeSkills(
    p.skills.map((s) => {
      const resolved = resolveSkill(s.name);
      return {
        name: resolved.name,
        normalizedName: resolved.normalizedName,
        proficiency: s.proficiency,
        yearsOfExperience: s.yearsOfExperience ?? null,
        category: resolved.category,
      };
    })
  );

  return {
    userId,
    avatarUrl: p.avatarUrl || null,
    fullName: account.name,
    professionalTitle: p.professionalTitle || null,
    bio: p.bio || null,
    location: p.location || null,
    contact: {
      phone: p.contact.phone || null,
      publicEmail: p.contact.publicEmail || null,
      website: p.contact.website || null,
      github: p.contact.github || null,
      linkedin: p.contact.linkedin || null,
      twitter: p.contact.twitter || null,
    },
    skills,
    skillIndex: buildSkillIndex(skills),
    education: p.education.map((e) => ({
      _id: ID.sub(e.subIdx),
      degree: e.degree,
      institution: e.institution,
      fieldOfStudy: e.fieldOfStudy || null,
      startYear: i32(e.startYear),
      endYear: e.endYear === null || e.endYear === undefined ? null : i32(e.endYear),
      grade: e.grade || null,
      description: e.description || null,
    })),
    experience: p.experience.map((x) => ({
      _id: ID.sub(x.subIdx),
      company: x.company,
      role: x.role,
      location: x.location || null,
      employmentType: x.employmentType,
      startDate: x.startDate,
      endDate: x.endDate || null,
      description: x.description || null,
      responsibilities: x.responsibilities || [],
      technologies: x.technologies || [],
      technologiesNormalized: [...new Set((x.technologies || []).map((t) => resolveSkill(t).normalizedName))],
    })),
    certificates: p.certificates.map((c) => ({
      _id: ID.sub(c.subIdx),
      name: c.name,
      issuingOrganization: c.issuingOrganization,
      issueDate: c.issueDate || null,
      expiryDate: null,
      credentialId: c.credentialId || null,
      credentialUrl: c.credentialUrl || null,
    })),
    profileHealth: i32(computeProfileHealth({ ...p, fullName: account.name }, projectCount)),
    updatedAt: now,
  };
}

/** Builds one project document with its derived normalised tech stack. */
function buildProjectDoc(project, userId) {
  return {
    userId,
    title: project.title,
    description: project.description,
    techStack: project.techStack,
    techStackNormalized: [...new Set(project.techStack.map((t) => resolveSkill(t).normalizedName))],
    repositoryUrl: project.repositoryUrl || null,
    liveDemoUrl: project.liveDemoUrl || null,
    imageUrl: project.imageUrl || null,
    images: [],
    role: project.role || null,
    features: project.features || [],
    achievements: project.achievements || [],
    startDate: project.startDate || null,
    endDate: project.endDate || null,
    featured: project.featured,
    includeInPortfolio: project.includeInPortfolio,
    deleted: false,
    deletedAt: null,
  };
}

/** Builds the immutable jobAnalysis document from a scored match. */
function buildAnalysisDoc(job, userId, match, createdAt) {
  return {
    userId,
    job: {
      title: job.title,
      normalizedTitle: normalizeJobTitle(job.title),
      company: job.company || null,
      location: job.location || null,
      employmentType: job.employmentType || null,
      description: job.description,
      sourceUrl: null,
      source: 'SEED',
    },
    analysis: {
      matchScore: i32(match.matchScore),
      skillsMatch: i32(match.skillsMatch),
      projectsMatch: i32(match.projectsMatch),
      requirementsMatch: i32(match.requirementsMatch),
      strongSkills: match.strongSkills,
      strongSkillsNormalized: match.strongSkillsNormalized,
      skillGaps: match.skillGaps,
      skillGapsNormalized: match.skillGapsNormalized,
      extractedRequirements: match.extractedRequirements,
      engine: match.engine,
    },
    recommendedProjects: match.recommendedProjects.map((r) => ({
      projectId: r.projectId,
      titleSnapshot: r.titleSnapshot,
      relevanceScore: i32(r.relevanceScore),
      reason: r.reason,
      matchedSkills: r.matchedSkills,
    })),
    tailoredSummary: match.tailoredSummary,
    resumeId: null,
    portfolioId: null,
    deleted: false,
    createdAt,
  };
}

/**
 * Builds a tailored resume.
 *
 * NOTE the two different strategies, and why:
 *   - skills / education / experience / certificates are SNAPSHOT (copied), so
 *     an already-delivered resume never changes under the candidate's feet;
 *   - projects are REFERENCED, because their bodies are large and are re-read
 *     at render time - only the ordering is frozen.
 */
function buildResumeDoc(job, account, profileDoc, match, userId, analysisId, createdAt) {
  const matched = new Set(match.strongSkillsNormalized);

  const orderedSkills = [...profileDoc.skills].sort((a, b) => {
    const am = matched.has(a.normalizedName) ? 0 : 1;
    const bm = matched.has(b.normalizedName) ? 0 : 1;
    return am - bm;
  });

  return {
    userId,
    jobAnalysisId: analysisId,
    targetRole: job.title,
    targetCompany: job.company || null,
    summary: match.tailoredSummary,
    skills: orderedSkills.map((s) => ({
      name: s.name,
      normalizedName: s.normalizedName,
      proficiency: s.proficiency,
      matched: matched.has(s.normalizedName),
    })),
    projects: match.recommendedProjects.slice(0, 4).map((r, index) => ({
      projectId: r.projectId,
      priority: i32(index + 1),
      titleSnapshot: r.titleSnapshot,
      highlightedBullets: [],
    })),
    education: profileDoc.education.map((e) => ({
      degree: e.degree,
      institution: e.institution,
      fieldOfStudy: e.fieldOfStudy,
      startYear: e.startYear,
      endYear: e.endYear,
      grade: e.grade,
    })),
    experience: profileDoc.experience.map((x) => ({
      company: x.company,
      role: x.role,
      employmentType: x.employmentType,
      startDate: x.startDate,
      endDate: x.endDate,
      bullets: x.responsibilities,
    })),
    certificates: profileDoc.certificates.map((c) => ({
      name: c.name,
      issuingOrganization: c.issuingOrganization,
      issueDate: c.issueDate,
      credentialUrl: c.credentialUrl,
    })),
    template: job.resumeTemplate || 'ATS_CLASSIC',
    pdfUrl: null,
    downloadCount: i32(0),
    deleted: false,
    deletedAt: null,
    createdAt,
    updatedAt: createdAt,
  };
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function seed(db) {
  const cfg = getConfig();
  const now = new Date();

  // Guard rail: refuse to seed a database that is obviously not a dev target.
  if (/prod/i.test(cfg.dbName) && !cfg.allowDestructive) {
    throw new Error(
      `Refusing to seed database "${cfg.dbName}" - the name looks like production. Set ALLOW_DESTRUCTIVE=true to override.`
    );
  }

  console.log('  [1/11] applying schema validators...');
  await applySchemas(db);

  console.log('\n  [2/11] creating indexes...');
  await createIndexes(db);

  console.log('\n  [3/11] skillDictionary...');
  const dictCount = await seedSkillDictionary(db, now);
  console.log(`         ${dictCount} canonical skills`);

  console.log('  [4/11] portfolioTemplates...');
  const tplCount = await seedTemplates(db, now);
  console.log(`         ${tplCount} templates (${PORTFOLIO_TEMPLATES.filter((t) => t.active).length} active)`);

  const passwordHash = await bcrypt.hash(cfg.seedPassword, cfg.bcryptRounds);
  const accounts = buildAccounts(now);

  const counts = { users: 0, profiles: 0, projects: 0, analyses: 0, resumes: 0, portfolios: 0 };

  console.log('  [5/11] users, [6/11] profiles, [7/11] projects...');
  for (const account of accounts) {
    const userId = ID.user(account.idx);

    // ---- 5. user -----------------------------------------------------------
    await upsert(
      db,
      'users',
      userId,
      {
        username: account.username,
        name: account.name,
        email: account.email,
        emailVerified: true,
        passwordHash,
        role: account.role,
        status: account.status,
        deleted: false,
        deletedAt: null,
        lastLoginAt: account.lastLoginAt,
        failedLoginAttempts: i32(0),
        lockedUntil: null,
      },
      account.createdAt
    );
    counts.users += 1;

    // ---- 7. projects (built first: profileHealth needs the count) ----------
    const projectDocs = [];
    for (const project of account.projects) {
      const projectId = ID.project(project.idx);
      const doc = buildProjectDoc(project, userId);
      await upsert(db, 'projects', projectId, doc, project.createdAt);
      projectDocs.push({ _id: projectId, ...doc });
      counts.projects += 1;
    }

    // ---- 6. profile --------------------------------------------------------
    const profileDoc = buildProfileDoc(account, userId, projectDocs.length, now);
    await upsert(db, 'profiles', ID.profile(account.idx), profileDoc, account.createdAt);
    counts.profiles += 1;

    // ---- 8/9. analyses + resumes ------------------------------------------
    const analysisByJobIdx = new Map();

    for (const job of account.jobs) {
      const analysisId = ID.analysis(job.idx);
      const createdAt = ago(now, job.daysAgo, 11);

      job.description = buildJobDescription(job, displayFor);

      const match = generateMatch({
        requiredSkills: job.required,
        niceToHaveSkills: job.niceToHave || [],
        skillDisplay: SKILL_DISPLAY,
        profileSkills: profileDoc.skillIndex,
        projects: projectDocs,
        jobTitle: job.title,
        company: job.company,
        candidateTitle: profileDoc.professionalTitle || 'developer',
      });

      await upsertImmutable(db, 'jobAnalyses', analysisId, buildAnalysisDoc(job, userId, match, createdAt));
      counts.analyses += 1;

      let resumeId = null;
      if (job.generateResume) {
        resumeId = ID.resume(job.idx);
        await upsert(
          db,
          'resumes',
          resumeId,
          buildResumeDoc(job, account, profileDoc, match, userId, analysisId, createdAt),
          createdAt
        );
        counts.resumes += 1;
      }

      analysisByJobIdx.set(job.idx, { analysisId, match, resumeId });
    }

    // ---- 10. portfolios ----------------------------------------------------
    for (const portfolio of account.portfolios) {
      const portfolioId = ID.portfolio(portfolio.idx);
      const linked = portfolio.fromJobIdx ? analysisByJobIdx.get(portfolio.fromJobIdx) : null;
      const template = PORTFOLIO_TEMPLATES.find((t) => t._id.equals(ID.template(portfolio.templateIdx)));

      // Ordering, never copying: relevant items first, everything else after.
      const recommendedIds = linked ? linked.match.recommendedProjects.map((r) => String(r.projectId)) : [];
      const publicProjects = projectDocs.filter((p) => p.includeInPortfolio && !p.deleted);

      const orderedProjects = [
        ...recommendedIds
          .map((id) => publicProjects.find((p) => String(p._id) === id))
          .filter(Boolean)
          .map((p) => p._id),
        ...publicProjects.filter((p) => !recommendedIds.includes(String(p._id))).map((p) => p._id),
      ];

      const strong = linked ? linked.match.strongSkillsNormalized : [];
      const orderedSkills = [
        ...strong.filter((s) => profileDoc.skillIndex.includes(s)),
        ...profileDoc.skillIndex.filter((s) => !strong.includes(s)),
      ];

      const publishedAt = portfolio.isPublished ? ago(now, portfolio.publishedDaysAgo ?? 1, 12) : null;

      await upsert(
        db,
        'portfolios',
        portfolioId,
        {
          userId,
          username: account.username,
          name: portfolio.name,
          templateId: template._id,
          templateKey: template.templateKey,
          sourceJobAnalysisId: linked ? linked.analysisId : null,
          sections: portfolio.sections,
          sectionOrder: [],
          orderedSkills,
          orderedProjects,
          headlineOverride: portfolio.headlineOverride || null,
          summaryOverride: null,
          theme: {
            primaryColor: template.theme.primaryColor,
            accentColor: template.theme.accentColor,
            darkMode: template.theme.darkMode,
          },
          resumeId: linked ? linked.resumeId : null,
          isPublished: portfolio.isPublished,
          publishedAt,
          viewCount: i32(portfolio.isPublished ? 40 + portfolio.idx : 0),
          deleted: false,
          deletedAt: null,
        },
        publishedAt || now
      );
      counts.portfolios += 1;

      // ---- 11. back-link the analysis to what it produced ------------------
      if (linked) {
        await db.collection('jobAnalyses').updateOne(
          { _id: linked.analysisId },
          { $set: { portfolioId, resumeId: linked.resumeId } }
        );
      }
    }

    // Resumes generated for analyses that never became a portfolio still need
    // their back-link.
    for (const [, linked] of analysisByJobIdx) {
      if (linked.resumeId) {
        await db
          .collection('jobAnalyses')
          .updateOne({ _id: linked.analysisId, resumeId: null }, { $set: { resumeId: linked.resumeId } });
      }
    }
  }

  // ---- audit trail ---------------------------------------------------------
  console.log('  [11/11] adminLogs...');
  const adminId = ID.user(1);
  const adminEmail = 'admin@portfoliopilot.local';
  const logs = [
    { n: 1, action: 'ADMIN_LOGIN', targetUserId: null, days: 3, metadata: { result: 'success' } },
    { n: 2, action: 'VIEW_USER_DETAIL', targetUserId: ID.user(5), days: 3, metadata: { reason: 'reported content' } },
    {
      n: 3,
      action: 'SUSPEND_USER',
      targetUserId: ID.user(5),
      days: 3,
      metadata: { reason: 'DEMO ONLY - seeded to populate the moderation screens', previousStatus: 'ACTIVE' },
    },
    { n: 4, action: 'DEACTIVATE_TEMPLATE', targetUserId: null, days: 20, metadata: { templateKey: 'CLASSIC_CARD' } },
  ];

  for (const log of logs) {
    await upsertImmutable(db, 'adminLogs', ID.adminLog(log.n), {
      adminId,
      adminEmail,
      action: log.action,
      targetUserId: log.targetUserId,
      targetCollection: log.action.includes('TEMPLATE') ? 'portfolioTemplates' : 'users',
      targetId: null,
      metadata: log.metadata,
      ipAddress: '127.0.0.1',
      userAgent: 'seed-script',
      createdAt: ago(now, log.days, 14),
    });
  }

  // ---- summary -------------------------------------------------------------
  console.log('\n  seeded:');
  console.log(`    users              ${counts.users}`);
  console.log(`    profiles           ${counts.profiles}`);
  console.log(`    projects           ${counts.projects}`);
  console.log(`    jobAnalyses        ${counts.analyses}`);
  console.log(`    resumes            ${counts.resumes}`);
  console.log(`    portfolios         ${counts.portfolios}`);
  console.log(`    portfolioTemplates ${tplCount}`);
  console.log(`    skillDictionary    ${dictCount}`);
  console.log(`    adminLogs          ${logs.length}`);

  console.log('\n  demo credentials (development only):');
  console.log(`    admin  admin@portfoliopilot.local / ${cfg.seedPassword}`);
  console.log(`    user   demo@portfoliopilot.local  / ${cfg.seedPassword}`);
  console.log('    public portfolio: /portfolio/demo-student');
}

if (require.main === module) {
  runScript('seed', seed);
}

module.exports = { seed, resolveSkill, displayFor, computeProfileHealth };
