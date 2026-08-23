'use strict';

/**
 * DETERMINISTIC MATCH GENERATOR - SEED DATA ONLY
 * ==============================================
 *
 * !!! THIS IS NOT THE PRODUCT'S SCORING ENGINE. !!!
 *
 * It exists purely so the seeded `jobAnalyses` documents contain internally
 * CONSISTENT numbers - a match score that actually reflects the demo user's
 * skills, gaps that really are missing from their profile, and recommended
 * projects that genuinely overlap the job. Random numbers would make the admin
 * charts look plausible but behave nonsensically when you click through.
 *
 * The real scoring lives in the Spring Boot backend. What matters here is the
 * OUTPUT CONTRACT, which is exactly the shape the frontend's
 * `generateMatch(jobDescription, userProfile)` promises:
 *
 *   {
 *     matchScore, skillsMatch, projectsMatch, requirementsMatch,
 *     strongSkills, skillGaps, recommendedProjects, tailoredSummary
 *   }
 *
 * Keep that contract stable; replace the arithmetic freely.
 */

const WEIGHTS = { skills: 0.5, projects: 0.3, requirements: 0.2 };

const clamp = (n) => Math.max(0, Math.min(100, Math.round(n)));
const pct = (num, den) => (den === 0 ? 0 : (num / den) * 100);

/**
 * @param {Object}   input
 * @param {string[]} input.requiredSkills   normalised skills demanded by the job
 * @param {string[]} input.niceToHaveSkills normalised, weighted at 50%
 * @param {Map<string,{name:string}>} input.skillDisplay normalized -> display name
 * @param {string[]} input.profileSkills    the user's normalised skillIndex
 * @param {Array<{_id: any, title: string, techStackNormalized: string[]}>} input.projects
 * @param {string}   input.jobTitle
 * @param {string}   input.company
 * @param {string}   input.candidateTitle
 */
function generateMatch(input) {
  const {
    requiredSkills = [],
    niceToHaveSkills = [],
    skillDisplay = new Map(),
    profileSkills = [],
    projects = [],
    jobTitle,
    company,
    candidateTitle,
  } = input;

  const have = new Set(profileSkills);
  const display = (n) => (skillDisplay.get(n) || { name: n }).name;

  // --- 1. Skills -------------------------------------------------------------
  const allDemanded = [...new Set([...requiredSkills, ...niceToHaveSkills])];
  const strong = allDemanded.filter((s) => have.has(s));
  const gaps = allDemanded.filter((s) => !have.has(s));

  // Required skills count double; nice-to-haves are half-weighted.
  const weightOf = (s) => (requiredSkills.includes(s) ? 1 : 0.5);
  const earned = strong.reduce((acc, s) => acc + weightOf(s), 0);
  const possible = allDemanded.reduce((acc, s) => acc + weightOf(s), 0);
  const skillsMatch = clamp(pct(earned, possible));

  // --- 2. Projects -----------------------------------------------------------
  const required = new Set(requiredSkills);
  const scored = projects
    .map((p) => {
      const tech = p.techStackNormalized || [];
      const overlap = tech.filter((t) => required.has(t));

      // coverage  = how much of the JOB this project proves
      // focus     = how much of the PROJECT is relevant (penalises kitchen-sink stacks)
      const coverage = pct(overlap.length, required.size);
      const focus = pct(overlap.length, tech.length || 1);
      const relevanceScore = clamp(coverage * 0.7 + focus * 0.3);

      return {
        projectId: p._id,
        titleSnapshot: p.title,
        relevanceScore,
        reason: overlap.length
          ? `Direct overlap on ${overlap.map(display).slice(0, 3).join(', ')}.`
          : 'No direct technology overlap with this role.',
        matchedSkills: overlap.map(display),
      };
    })
    .filter((p) => p.relevanceScore > 0)
    .sort((a, b) => b.relevanceScore - a.relevanceScore);

  const top = scored.slice(0, 3);
  // Average of the top 2 - a single perfect project should not imply a perfect
  // portfolio, but three mediocre ones should not drown out one great fit.
  const projectsMatch = clamp(
    top.length === 0 ? 0 : top.slice(0, 2).reduce((a, p) => a + p.relevanceScore, 0) / Math.min(2, top.length)
  );

  // --- 3. Requirements -------------------------------------------------------
  const extractedRequirements = [
    ...requiredSkills.map((s) => ({
      text: `Hands-on experience with ${display(s)}`,
      met: have.has(s),
      weight: 1,
    })),
    ...niceToHaveSkills.map((s) => ({
      text: `Exposure to ${display(s)} is a plus`,
      met: have.has(s),
      weight: 0.5,
    })),
    { text: 'Bachelor\'s degree in Computer Science, IT or equivalent', met: true, weight: 0.8 },
    { text: 'Portfolio of shipped projects', met: projects.length > 0, weight: 0.8 },
  ];

  const reqEarned = extractedRequirements.reduce((a, r) => a + (r.met ? r.weight : 0), 0);
  const reqPossible = extractedRequirements.reduce((a, r) => a + r.weight, 0);
  const requirementsMatch = clamp(pct(reqEarned, reqPossible));

  // --- 4. Overall ------------------------------------------------------------
  const matchScore = clamp(
    skillsMatch * WEIGHTS.skills + projectsMatch * WEIGHTS.projects + requirementsMatch * WEIGHTS.requirements
  );

  // --- 5. Tailored summary ---------------------------------------------------
  const headlineSkills = strong
    .filter((s) => requiredSkills.includes(s))
    .slice(0, 4)
    .map(display);

  const tailoredSummary = buildSummary({
    jobTitle,
    company,
    candidateTitle,
    headlineSkills,
    projectCount: projects.length,
    topProject: top[0] ? top[0].titleSnapshot : null,
    matchScore,
  });

  return {
    matchScore,
    skillsMatch,
    projectsMatch,
    requirementsMatch,
    strongSkills: strong.map(display),
    strongSkillsNormalized: strong,
    skillGaps: gaps.map(display),
    skillGapsNormalized: gaps,
    extractedRequirements,
    recommendedProjects: top,
    tailoredSummary,
    engine: 'seed-mock-v1',
  };
}

/**
 * Assembles the role-specific summary line that heads the tailored resume.
 * Real implementations will hand this to an LLM; the structure below is the
 * fallback template and doubles as the prompt skeleton.
 */
function buildSummary({ jobTitle, company, candidateTitle, headlineSkills, projectCount, topProject, matchScore }) {
  const skills = headlineSkills.length ? headlineSkills.join(', ') : 'modern web technologies';
  const target = company ? `${jobTitle} role at ${company}` : `${jobTitle} role`;
  const evidence = topProject ? ` Most relevant work: ${topProject}.` : '';
  const strength = matchScore >= 80 ? 'Strong' : matchScore >= 60 ? 'Solid' : 'Developing';

  return (
    `${strength} ${candidateTitle} targeting the ${target}. ` +
    `Practical experience across ${skills}, demonstrated through ${projectCount} shipped project${projectCount === 1 ? '' : 's'}.` +
    evidence
  );
}

module.exports = { generateMatch, buildSummary, WEIGHTS };
