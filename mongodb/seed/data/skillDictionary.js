'use strict';

/**
 * Seed content for the `skillDictionary` collection.
 *
 * `aliases` are stored ALREADY NORMALISED (as produced by
 * lib/normalize.js#normalizeSkill), because that is the form the backend will
 * look them up with:
 *
 *   user types "React.js"
 *     -> normalizeSkill()      -> "react js"
 *     -> db.skillDictionary.findOne({ aliases: "react js" })
 *     -> canonical "React" / normalized "react"
 *
 * `weight` (0..1) is the scorer's importance multiplier: a specific framework
 * should count for more than a generic term.
 */

/** @type {Array<{canonicalName:string, normalizedName:string, aliases:string[], category:string, relatedSkills?:string[], weight?:number}>} */
const SKILL_DICTIONARY = [
  // --- Languages -------------------------------------------------------------
  { canonicalName: 'Java', normalizedName: 'java', aliases: ['core java', 'java se', 'java 17', 'j2se'], category: 'LANGUAGE', relatedSkills: ['spring boot', 'hibernate', 'maven'], weight: 0.9 },
  { canonicalName: 'JavaScript', normalizedName: 'javascript', aliases: ['js', 'ecmascript', 'es6', 'es2015'], category: 'LANGUAGE', relatedSkills: ['typescript', 'react', 'nodejs'], weight: 0.8 },
  { canonicalName: 'TypeScript', normalizedName: 'typescript', aliases: ['ts', 'type script'], category: 'LANGUAGE', relatedSkills: ['javascript', 'react'], weight: 0.85 },
  { canonicalName: 'Python', normalizedName: 'python', aliases: ['python3', 'py'], category: 'LANGUAGE', relatedSkills: ['sql'], weight: 0.85 },
  { canonicalName: 'SQL', normalizedName: 'sql', aliases: ['structured query language'], category: 'DATABASE', relatedSkills: ['mysql', 'postgresql'], weight: 0.7 },

  // --- Backend ---------------------------------------------------------------
  { canonicalName: 'Spring Boot', normalizedName: 'spring boot', aliases: ['springboot', 'spring boot 3'], category: 'BACKEND', relatedSkills: ['java', 'spring', 'spring security'], weight: 0.95 },
  { canonicalName: 'Spring Framework', normalizedName: 'spring', aliases: ['spring framework', 'spring core'], category: 'BACKEND', relatedSkills: ['spring boot', 'java'], weight: 0.8 },
  { canonicalName: 'Spring Security', normalizedName: 'spring security', aliases: ['springsecurity'], category: 'BACKEND', relatedSkills: ['spring boot', 'jwt'], weight: 0.85 },
  { canonicalName: 'Hibernate', normalizedName: 'hibernate', aliases: ['jpa', 'spring data jpa', 'hibernate orm'], category: 'BACKEND', relatedSkills: ['java', 'sql'], weight: 0.8 },
  { canonicalName: 'REST API', normalizedName: 'rest api', aliases: ['rest', 'restful api', 'restful apis', 'rest apis', 'restful web services'], category: 'BACKEND', relatedSkills: ['spring boot', 'nodejs'], weight: 0.75 },
  { canonicalName: 'Microservices', normalizedName: 'microservices', aliases: ['microservice', 'micro services', 'microservice architecture'], category: 'BACKEND', relatedSkills: ['spring boot', 'docker', 'kubernetes'], weight: 0.9 },
  { canonicalName: 'Node.js', normalizedName: 'nodejs', aliases: ['node js', 'node'], category: 'BACKEND', relatedSkills: ['javascript', 'expressjs'], weight: 0.85 },
  { canonicalName: 'Express.js', normalizedName: 'expressjs', aliases: ['express js', 'express'], category: 'BACKEND', relatedSkills: ['nodejs'], weight: 0.7 },
  { canonicalName: 'GraphQL', normalizedName: 'graphql', aliases: ['graph ql'], category: 'BACKEND', relatedSkills: ['rest api'], weight: 0.8 },
  { canonicalName: 'Apache Kafka', normalizedName: 'kafka', aliases: ['apache kafka', 'kafka streams'], category: 'BACKEND', relatedSkills: ['microservices'], weight: 0.9 },
  { canonicalName: 'JWT', normalizedName: 'jwt', aliases: ['json web token', 'json web tokens'], category: 'BACKEND', relatedSkills: ['spring security'], weight: 0.6 },

  // --- Frontend --------------------------------------------------------------
  { canonicalName: 'React', normalizedName: 'react', aliases: ['react js', 'reactjs', 'react 18', 'react hooks'], category: 'FRONTEND', relatedSkills: ['javascript', 'typescript', 'nextjs'], weight: 0.95 },
  { canonicalName: 'Next.js', normalizedName: 'nextjs', aliases: ['next js'], category: 'FRONTEND', relatedSkills: ['react'], weight: 0.85 },
  { canonicalName: 'Tailwind CSS', normalizedName: 'tailwind css', aliases: ['tailwind', 'tailwindcss'], category: 'FRONTEND', relatedSkills: ['css'], weight: 0.7 },
  { canonicalName: 'HTML5', normalizedName: 'html', aliases: ['html5', 'hyper text markup language'], category: 'FRONTEND', relatedSkills: ['css'], weight: 0.4 },
  { canonicalName: 'CSS3', normalizedName: 'css', aliases: ['css3', 'cascading style sheets'], category: 'FRONTEND', relatedSkills: ['html', 'tailwind css'], weight: 0.4 },
  { canonicalName: 'Redux', normalizedName: 'redux', aliases: ['redux toolkit', 'rtk'], category: 'FRONTEND', relatedSkills: ['react'], weight: 0.7 },

  // --- Databases -------------------------------------------------------------
  { canonicalName: 'MongoDB', normalizedName: 'mongodb', aliases: ['mongo db', 'mongo', 'mongo database', 'mongodb atlas'], category: 'DATABASE', relatedSkills: ['nodejs', 'spring boot'], weight: 0.85 },
  { canonicalName: 'MySQL', normalizedName: 'mysql', aliases: ['my sql'], category: 'DATABASE', relatedSkills: ['sql'], weight: 0.7 },
  { canonicalName: 'PostgreSQL', normalizedName: 'postgresql', aliases: ['postgres', 'psql', 'postgre sql'], category: 'DATABASE', relatedSkills: ['sql'], weight: 0.8 },
  { canonicalName: 'Redis', normalizedName: 'redis', aliases: ['redis cache'], category: 'DATABASE', relatedSkills: ['microservices'], weight: 0.8 },

  // --- DevOps / Cloud --------------------------------------------------------
  { canonicalName: 'Docker', normalizedName: 'docker', aliases: ['dockerize', 'containerization', 'containers'], category: 'DEVOPS', relatedSkills: ['kubernetes', 'ci cd'], weight: 0.9 },
  { canonicalName: 'Kubernetes', normalizedName: 'kubernetes', aliases: ['k8s', 'kube'], category: 'DEVOPS', relatedSkills: ['docker'], weight: 0.95 },
  { canonicalName: 'CI/CD', normalizedName: 'ci cd', aliases: ['cicd', 'continuous integration', 'continuous delivery', 'github actions', 'jenkins'], category: 'DEVOPS', relatedSkills: ['docker', 'git'], weight: 0.8 },
  { canonicalName: 'AWS', normalizedName: 'aws', aliases: ['amazon web services', 'aws cloud', 'ec2', 's3'], category: 'CLOUD', relatedSkills: ['docker', 'kubernetes'], weight: 0.95 },
  { canonicalName: 'Linux', normalizedName: 'linux', aliases: ['unix', 'gnu linux', 'shell scripting'], category: 'TOOLS', relatedSkills: ['docker'], weight: 0.6 },

  // --- Testing ---------------------------------------------------------------
  { canonicalName: 'JUnit', normalizedName: 'junit', aliases: ['junit 5', 'junit5', 'j unit'], category: 'TESTING', relatedSkills: ['java', 'mockito'], weight: 0.7 },
  { canonicalName: 'Mockito', normalizedName: 'mockito', aliases: [], category: 'TESTING', relatedSkills: ['junit'], weight: 0.7 },
  { canonicalName: 'Jest', normalizedName: 'jest', aliases: ['jestjs'], category: 'TESTING', relatedSkills: ['javascript', 'react'], weight: 0.7 },

  // --- Tools -----------------------------------------------------------------
  { canonicalName: 'Git', normalizedName: 'git', aliases: ['git scm', 'version control', 'github'], category: 'TOOLS', relatedSkills: ['ci cd'], weight: 0.5 },
  { canonicalName: 'Maven', normalizedName: 'maven', aliases: ['apache maven'], category: 'TOOLS', relatedSkills: ['java'], weight: 0.5 },
  { canonicalName: 'Figma', normalizedName: 'figma', aliases: [], category: 'TOOLS', relatedSkills: [], weight: 0.5 },

  // --- Soft skills -----------------------------------------------------------
  { canonicalName: 'Problem Solving', normalizedName: 'problem solving', aliases: ['analytical thinking', 'problem solving skills'], category: 'SOFT_SKILL', relatedSkills: [], weight: 0.4 },
  { canonicalName: 'Communication', normalizedName: 'communication', aliases: ['communication skills', 'verbal communication'], category: 'SOFT_SKILL', relatedSkills: [], weight: 0.4 },
  { canonicalName: 'Teamwork', normalizedName: 'teamwork', aliases: ['collaboration', 'team player'], category: 'SOFT_SKILL', relatedSkills: [], weight: 0.4 },
];

/** Fast lookup: normalizedName OR alias -> dictionary entry. */
function buildLookup(entries = SKILL_DICTIONARY) {
  const map = new Map();
  for (const entry of entries) {
    map.set(entry.normalizedName, entry);
    for (const alias of entry.aliases) map.set(alias, entry);
  }
  return map;
}

module.exports = { SKILL_DICTIONARY, buildLookup };
