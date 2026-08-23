'use strict';

/**
 * DEMO / DEVELOPMENT SEED CONTENT
 * ===============================
 *
 * All credentials here are OBVIOUSLY FAKE and use the non-routable
 * `.local` TLD so they can never collide with a real inbox:
 *
 *   admin@portfoliopilot.local   role ADMIN
 *   demo@portfoliopilot.local    role USER   <- the fully-populated showcase account
 *   plus three extra users so the admin charts have real distribution
 *
 * The shared password comes from SEED_PASSWORD (default "DemoPass123!") and is
 * bcrypt-hashed at seed time. NEVER run this seed against production.
 *
 * This module exports DECLARATIONS only. `seed/seed.js` derives every computed
 * field (normalizedName, skillIndex, techStackNormalized, match scores, ...) so
 * the derivation logic lives in exactly one place.
 */

const DAY = 24 * 60 * 60 * 1000;

/**
 * A Date `n` days before the reference point, pinned to a fixed hour so seeds
 * are reproducible. Clamped so `n = 0` can never produce a future timestamp
 * (which would break "jobs analyzed today" and any `$lte: now` filter).
 */
const ago = (now, n, hour = 10) => {
  const d = new Date(now.getTime() - n * DAY);
  d.setUTCHours(hour, 15, 0, 0);
  return d > now ? new Date(now.getTime() - 60 * 60 * 1000) : d;
};

/**
 * @param {Date} now reference "today", injected so seeds are reproducible
 */
function buildAccounts(now) {
  return [
    // =========================================================================
    // 1. PLATFORM ADMIN
    // =========================================================================
    {
      idx: 1,
      username: 'admin',
      email: 'admin@portfoliopilot.local',
      name: 'Platform Admin',
      role: 'ADMIN',
      status: 'ACTIVE',
      createdAt: ago(now, 120),
      lastLoginAt: ago(now, 0, 8),
      // Admins still get a profile row so the users<->profiles 1:1 invariant
      // holds for every account. It stays intentionally sparse.
      profile: {
        professionalTitle: 'Platform Administrator',
        bio: 'Operates the PortfolioPilot AI platform. Not a candidate account.',
        location: 'Remote',
        contact: {},
        skills: [],
        education: [],
        experience: [],
        certificates: [],
      },
      projects: [],
      jobs: [],
      portfolios: [],
    },

    // =========================================================================
    // 2. DEMO STUDENT - the account every walkthrough should use
    // =========================================================================
    {
      idx: 2,
      username: 'demo-student',
      email: 'demo@portfoliopilot.local',
      name: 'Demo Student',
      role: 'USER',
      status: 'ACTIVE',
      createdAt: ago(now, 45),
      lastLoginAt: ago(now, 0, 9),
      profile: {
        avatarUrl: '/assets/demo/avatar-demo-student.png',
        professionalTitle: 'Full-Stack Developer (Java + React)',
        bio:
          'MSc IT student who builds production-shaped web applications. Comfortable owning a feature end to end: Spring Boot REST services, MongoDB data modelling, and a typed React front end. Currently deepening cloud and container skills.',
        location: 'Pune, India',
        contact: {
          phone: '+91 90000 00000',
          publicEmail: 'demo.student@example.com',
          website: 'https://demo-student.example.com',
          github: 'https://github.com/demo-student',
          linkedin: 'https://linkedin.com/in/demo-student',
        },
        // Display names deliberately include messy variants ("React.js",
        // "Spring-Boot", "Mongo DB") to prove the normaliser + dictionary work.
        skills: [
          { name: 'Java', proficiency: 'ADVANCED', yearsOfExperience: 3 },
          { name: 'Spring-Boot', proficiency: 'ADVANCED', yearsOfExperience: 2 },
          { name: 'REST APIs', proficiency: 'ADVANCED', yearsOfExperience: 2 },
          { name: 'Hibernate', proficiency: 'INTERMEDIATE', yearsOfExperience: 1.5 },
          { name: 'Mongo DB', proficiency: 'INTERMEDIATE', yearsOfExperience: 1.5 },
          { name: 'MySQL', proficiency: 'INTERMEDIATE', yearsOfExperience: 2 },
          { name: 'React.js', proficiency: 'INTERMEDIATE', yearsOfExperience: 2 },
          { name: 'JavaScript', proficiency: 'ADVANCED', yearsOfExperience: 3 },
          { name: 'TypeScript', proficiency: 'INTERMEDIATE', yearsOfExperience: 1 },
          { name: 'Tailwind', proficiency: 'INTERMEDIATE', yearsOfExperience: 1 },
          { name: 'HTML5', proficiency: 'ADVANCED', yearsOfExperience: 3 },
          { name: 'CSS3', proficiency: 'ADVANCED', yearsOfExperience: 3 },
          { name: 'Node js', proficiency: 'BEGINNER', yearsOfExperience: 1 },
          { name: 'Git', proficiency: 'ADVANCED', yearsOfExperience: 3 },
          { name: 'JUnit 5', proficiency: 'INTERMEDIATE', yearsOfExperience: 1 },
          { name: 'Maven', proficiency: 'INTERMEDIATE', yearsOfExperience: 2 },
          { name: 'Problem Solving', proficiency: 'ADVANCED', yearsOfExperience: 3 },
          // Duplicate on purpose: "ReactJS" normalises to the same key as
          // "React.js". The seed's dedupe + the `skillIndex` uniqueItems rule
          // must both collapse it. If this ever reaches the database twice,
          // validation fails loudly - which is the point.
          { name: 'ReactJS', proficiency: 'BEGINNER', yearsOfExperience: 1 },
        ],
        education: [
          {
            subIdx: 1,
            degree: 'MSc Information Technology',
            institution: 'Fergusson College',
            fieldOfStudy: 'Information Technology',
            startYear: 2026,
            endYear: 2028,
            grade: null,
            description: 'Ongoing. Coursework in distributed systems, cloud computing and applied machine learning.',
          },
          {
            subIdx: 2,
            degree: 'BSc Computer Science',
            institution: 'Savitribai Phule Pune University',
            fieldOfStudy: 'Computer Science',
            startYear: 2023,
            endYear: 2026,
            grade: '8.6 CGPA',
            description: 'Data structures, DBMS, operating systems, software engineering.',
          },
        ],
        experience: [
          {
            subIdx: 11,
            company: 'Nexora Labs',
            role: 'Backend Developer Intern',
            location: 'Pune, India (Hybrid)',
            employmentType: 'INTERNSHIP',
            startDate: new Date(Date.UTC(2025, 4, 12)),
            endDate: new Date(Date.UTC(2025, 6, 31)),
            description: 'Worked on the billing service of a B2B SaaS platform.',
            responsibilities: [
              'Built 9 REST endpoints in Spring Boot backed by MySQL and Hibernate.',
              'Cut the invoice listing query from 1.8s to 220ms by adding composite indexes.',
              'Raised service test coverage from 34% to 71% with JUnit 5 and Mockito.',
            ],
            technologies: ['Java', 'Spring Boot', 'MySQL', 'Hibernate', 'JUnit 5', 'Git'],
          },
        ],
        certificates: [
          {
            subIdx: 21,
            name: 'Java Backend Development',
            issuingOrganization: 'Coursera',
            issueDate: new Date(Date.UTC(2025, 8, 3)),
            credentialId: 'DEMO-CERT-JAVA-0001',
            credentialUrl: 'https://coursera.org/verify/DEMO-CERT-JAVA-0001',
          },
          {
            subIdx: 22,
            name: 'MongoDB for Developers (M001)',
            issuingOrganization: 'MongoDB University',
            issueDate: new Date(Date.UTC(2026, 1, 18)),
            credentialId: 'DEMO-CERT-MDB-0002',
            credentialUrl: 'https://learn.mongodb.com/verify/DEMO-CERT-MDB-0002',
          },
        ],
      },

      projects: [
        {
          idx: 11,
          title: 'PortfolioPilot AI',
          description:
            'Adaptive portfolio builder. Users maintain one profile; the app analyses a pasted job description and generates a tailored portfolio and ATS-friendly resume that reorders skills and projects for that specific role.',
          techStack: ['React', 'TypeScript', 'Tailwind CSS', 'Spring Boot', 'MongoDB', 'REST API'],
          repositoryUrl: 'https://github.com/demo-student/portfoliopilot-ai',
          liveDemoUrl: 'https://portfoliopilot.example.com',
          imageUrl: '/assets/demo/project-portfoliopilot.png',
          role: 'Full-stack developer',
          features: [
            'Job-description parser that extracts required skills',
            'Match scoring with skills / projects / requirements sub-scores',
            'One-click tailored resume export to PDF',
          ],
          achievements: [
            'Modelled 10 MongoDB collections with JSON Schema validation and partial unique indexes.',
            'Reduced public portfolio load to a single indexed query.',
          ],
          startDate: new Date(Date.UTC(2026, 4, 1)),
          endDate: null,
          featured: true,
          includeInPortfolio: true,
          createdAt: ago(now, 40),
        },
        {
          idx: 12,
          title: 'Campus Placement Portal',
          description:
            'Placement management system for a 4,000-student campus. Companies post drives, students apply, and the placement cell tracks offers through a role-based dashboard.',
          techStack: ['Java', 'Spring Boot', 'Spring Security', 'MySQL', 'Hibernate', 'JUnit 5'],
          repositoryUrl: 'https://github.com/demo-student/campus-placement-portal',
          liveDemoUrl: null,
          imageUrl: '/assets/demo/project-placement.png',
          role: 'Backend developer',
          features: [
            'Role-based access for students, recruiters and the placement cell',
            'Eligibility rule engine per drive',
            'Offer-letter tracking with audit history',
          ],
          achievements: [
            'Handled 4,000+ student records with paginated, indexed queries.',
            'Automated eligibility filtering that previously took the cell 6 hours per drive.',
          ],
          startDate: new Date(Date.UTC(2025, 10, 5)),
          endDate: new Date(Date.UTC(2026, 2, 20)),
          featured: true,
          includeInPortfolio: true,
          createdAt: ago(now, 38),
        },
        {
          idx: 13,
          title: 'ExpenseSense',
          description:
            'Personal finance tracker with automatic category detection, monthly budget envelopes and a shared-expense splitter.',
          techStack: ['React', 'Node.js', 'Express.js', 'MongoDB', 'Tailwind CSS'],
          repositoryUrl: 'https://github.com/demo-student/expensesense',
          liveDemoUrl: 'https://expensesense.example.com',
          imageUrl: '/assets/demo/project-expensesense.png',
          role: 'Full-stack developer',
          features: ['Rule-based transaction categorisation', 'Budget envelopes with rollover', 'CSV import'],
          achievements: ['Aggregation pipeline computes 12-month trends in under 40ms on 50k documents.'],
          startDate: new Date(Date.UTC(2025, 6, 10)),
          endDate: new Date(Date.UTC(2025, 9, 2)),
          featured: false,
          includeInPortfolio: true,
          createdAt: ago(now, 35),
        },
        {
          idx: 14,
          title: 'AlgoViz',
          description:
            'Browser-based algorithm visualiser for sorting and graph traversal, built to teach first-year students. No framework - plain DOM and Canvas.',
          techStack: ['JavaScript', 'HTML5', 'CSS3'],
          repositoryUrl: 'https://github.com/demo-student/algoviz',
          liveDemoUrl: 'https://algoviz.example.com',
          imageUrl: '/assets/demo/project-algoviz.png',
          role: 'Solo developer',
          features: ['Step-through execution with adjustable speed', 'Side-by-side algorithm comparison'],
          achievements: ['Used by two university tutorial groups.'],
          startDate: new Date(Date.UTC(2025, 1, 4)),
          endDate: new Date(Date.UTC(2025, 3, 18)),
          featured: false,
          // Private on purpose: proves that includeInPortfolio: false really
          // does exclude a project from the public page.
          includeInPortfolio: false,
          createdAt: ago(now, 33),
        },
      ],

      jobs: [
        {
          idx: 101,
          title: 'Java Backend Developer',
          company: 'Nexora Labs',
          location: 'Pune, India (Hybrid)',
          employmentType: 'FULL_TIME',
          daysAgo: 12,
          required: ['java', 'spring boot', 'rest api', 'mysql', 'hibernate', 'microservices'],
          niceToHave: ['docker', 'aws', 'kafka', 'junit'],
          generateResume: true,
          resumeTemplate: 'ATS_CLASSIC',
        },
        {
          idx: 102,
          title: 'Frontend Developer Intern',
          company: 'Brightpix Studio',
          location: 'Remote',
          employmentType: 'INTERNSHIP',
          daysAgo: 8,
          required: ['react', 'javascript', 'css', 'html'],
          niceToHave: ['typescript', 'tailwind css', 'jest', 'figma'],
          generateResume: true,
          resumeTemplate: 'ATS_MODERN',
        },
        {
          idx: 103,
          title: 'Full Stack Engineer (MERN)',
          company: 'Cloudspire',
          location: 'Bengaluru, India',
          employmentType: 'FULL_TIME',
          daysAgo: 5,
          required: ['react', 'nodejs', 'expressjs', 'mongodb', 'rest api'],
          niceToHave: ['typescript', 'docker', 'aws', 'redis'],
          generateResume: false,
        },
        {
          idx: 104,
          title: 'Software Engineer Trainee',
          company: 'Vertex Systems',
          location: 'Hyderabad, India',
          employmentType: 'FULL_TIME',
          // Today, so the "jobs analyzed today" stat card is never zero.
          daysAgo: 0,
          required: ['java', 'sql', 'git', 'problem solving'],
          niceToHave: ['python', 'linux', 'docker'],
          generateResume: false,
        },
      ],

      portfolios: [
        {
          idx: 601,
          name: 'Java Backend Portfolio',
          templateIdx: 1,
          fromJobIdx: 101,
          isPublished: true,
          publishedDaysAgo: 11,
          sections: { about: true, skills: true, projects: true, education: true, experience: true, certificates: true, contact: true },
          headlineOverride: 'Java Backend Developer | Spring Boot + MongoDB',
        },
        {
          idx: 602,
          name: 'Frontend Internship Draft',
          templateIdx: 3,
          fromJobIdx: 102,
          isPublished: false,
          sections: { about: true, skills: true, projects: true, education: true, experience: false, certificates: false, contact: true },
          headlineOverride: 'Frontend Developer | React + TypeScript',
        },
      ],
    },

    // =========================================================================
    // 3. AARAV - frontend leaning, gives the charts a second data shape
    // =========================================================================
    {
      idx: 3,
      username: 'aarav-sharma',
      email: 'aarav@portfoliopilot.local',
      name: 'Aarav Sharma',
      role: 'USER',
      status: 'ACTIVE',
      createdAt: ago(now, 27),
      lastLoginAt: ago(now, 2),
      profile: {
        professionalTitle: 'Frontend Developer',
        bio: 'Final-year CS student focused on accessible, performant React interfaces.',
        location: 'Delhi, India',
        contact: { github: 'https://github.com/aarav-sharma', linkedin: 'https://linkedin.com/in/aarav-sharma' },
        skills: [
          { name: 'React', proficiency: 'ADVANCED', yearsOfExperience: 2 },
          { name: 'JavaScript', proficiency: 'ADVANCED', yearsOfExperience: 3 },
          { name: 'TypeScript', proficiency: 'INTERMEDIATE', yearsOfExperience: 1 },
          { name: 'Tailwind CSS', proficiency: 'ADVANCED', yearsOfExperience: 2 },
          { name: 'HTML5', proficiency: 'EXPERT', yearsOfExperience: 4 },
          { name: 'CSS3', proficiency: 'EXPERT', yearsOfExperience: 4 },
          { name: 'Redux', proficiency: 'INTERMEDIATE', yearsOfExperience: 1 },
          { name: 'Figma', proficiency: 'INTERMEDIATE', yearsOfExperience: 2 },
          { name: 'Git', proficiency: 'ADVANCED', yearsOfExperience: 3 },
        ],
        education: [
          {
            subIdx: 31,
            degree: 'BTech Computer Science',
            institution: 'Delhi Technological University',
            fieldOfStudy: 'Computer Science',
            startYear: 2022,
            endYear: 2026,
            grade: '8.1 CGPA',
            description: null,
          },
        ],
        experience: [],
        certificates: [],
      },
      projects: [
        {
          idx: 21,
          title: 'Accessible Component Library',
          description: 'WCAG 2.1 AA compliant React component library with 28 components, full keyboard navigation and screen-reader tests.',
          techStack: ['React', 'TypeScript', 'Tailwind CSS', 'Jest'],
          repositoryUrl: 'https://github.com/aarav-sharma/a11y-kit',
          liveDemoUrl: 'https://a11y-kit.example.com',
          imageUrl: null,
          role: 'Solo developer',
          features: ['Keyboard-first interaction model', 'Automated axe-core audits in CI'],
          achievements: ['Zero critical axe violations across all 28 components.'],
          startDate: new Date(Date.UTC(2025, 8, 1)),
          endDate: null,
          featured: true,
          includeInPortfolio: true,
          createdAt: ago(now, 24),
        },
        {
          idx: 22,
          title: 'Weather Dashboard',
          description: 'Real-time weather dashboard with location search, 7-day forecast charts and offline caching.',
          techStack: ['React', 'JavaScript', 'CSS3'],
          repositoryUrl: 'https://github.com/aarav-sharma/weather-dashboard',
          liveDemoUrl: null,
          imageUrl: null,
          role: 'Solo developer',
          features: ['Service-worker offline cache'],
          achievements: [],
          startDate: new Date(Date.UTC(2025, 5, 1)),
          endDate: new Date(Date.UTC(2025, 6, 15)),
          featured: false,
          includeInPortfolio: true,
          createdAt: ago(now, 22),
        },
      ],
      jobs: [
        {
          idx: 201,
          title: 'Frontend Developer',
          company: 'Lumina Tech',
          location: 'Remote',
          employmentType: 'FULL_TIME',
          daysAgo: 15,
          required: ['react', 'typescript', 'css', 'javascript'],
          niceToHave: ['nextjs', 'jest', 'graphql'],
          generateResume: true,
          resumeTemplate: 'ATS_COMPACT',
        },
        {
          idx: 202,
          title: 'Full Stack Engineer (MERN)',
          company: 'Cloudspire',
          location: 'Bengaluru, India',
          employmentType: 'FULL_TIME',
          daysAgo: 3,
          required: ['react', 'nodejs', 'expressjs', 'mongodb', 'rest api'],
          niceToHave: ['typescript', 'docker', 'aws'],
          generateResume: false,
        },
      ],
      portfolios: [
        {
          idx: 603,
          name: 'Frontend Portfolio',
          templateIdx: 3,
          fromJobIdx: 201,
          isPublished: true,
          publishedDaysAgo: 14,
          sections: { about: true, skills: true, projects: true, education: true, experience: false, certificates: false, contact: true },
          headlineOverride: null,
        },
      ],
    },

    // =========================================================================
    // 4. PRIYA - data/backend leaning, heaviest analyser
    // =========================================================================
    {
      idx: 4,
      username: 'priya-nair',
      email: 'priya@portfoliopilot.local',
      name: 'Priya Nair',
      role: 'USER',
      status: 'ACTIVE',
      createdAt: ago(now, 19),
      lastLoginAt: ago(now, 1),
      profile: {
        professionalTitle: 'Backend & Data Engineer',
        bio: 'Builds data-heavy backends. Comfortable with Python pipelines and Postgres query tuning.',
        location: 'Kochi, India',
        contact: { github: 'https://github.com/priya-nair' },
        skills: [
          { name: 'Python', proficiency: 'ADVANCED', yearsOfExperience: 3 },
          { name: 'SQL', proficiency: 'ADVANCED', yearsOfExperience: 3 },
          { name: 'PostgreSQL', proficiency: 'ADVANCED', yearsOfExperience: 2 },
          { name: 'MongoDB', proficiency: 'INTERMEDIATE', yearsOfExperience: 1 },
          { name: 'REST API', proficiency: 'INTERMEDIATE', yearsOfExperience: 2 },
          { name: 'Git', proficiency: 'ADVANCED', yearsOfExperience: 3 },
          { name: 'Linux', proficiency: 'INTERMEDIATE', yearsOfExperience: 2 },
          { name: 'Problem Solving', proficiency: 'EXPERT', yearsOfExperience: 4 },
        ],
        education: [
          {
            subIdx: 41,
            degree: 'BTech Information Technology',
            institution: 'Cochin University of Science and Technology',
            fieldOfStudy: 'Information Technology',
            startYear: 2021,
            endYear: 2025,
            grade: '8.9 CGPA',
            description: null,
          },
        ],
        experience: [
          {
            subIdx: 42,
            company: 'DataForge Analytics',
            role: 'Data Engineering Intern',
            location: 'Remote',
            employmentType: 'INTERNSHIP',
            startDate: new Date(Date.UTC(2025, 0, 8)),
            endDate: new Date(Date.UTC(2025, 5, 30)),
            description: 'Maintained ingestion pipelines for a retail analytics product.',
            responsibilities: [
              'Rewrote a nightly batch job in Python, cutting runtime from 4h to 38min.',
              'Added partitioning to a 240M-row Postgres table.',
            ],
            technologies: ['Python', 'PostgreSQL', 'Linux'],
          },
        ],
        certificates: [],
      },
      projects: [
        {
          idx: 31,
          title: 'Retail Demand Forecaster',
          description: 'Time-series forecasting service exposing a REST API for weekly SKU-level demand predictions.',
          techStack: ['Python', 'PostgreSQL', 'REST API'],
          repositoryUrl: 'https://github.com/priya-nair/demand-forecaster',
          liveDemoUrl: null,
          imageUrl: null,
          role: 'Solo developer',
          features: ['Seasonal decomposition', 'Backtesting harness'],
          achievements: ['Beat the naive baseline by 23% MAPE on held-out weeks.'],
          startDate: new Date(Date.UTC(2025, 7, 1)),
          endDate: new Date(Date.UTC(2025, 11, 12)),
          featured: true,
          includeInPortfolio: true,
          createdAt: ago(now, 17),
        },
        {
          idx: 32,
          title: 'Log Aggregation Service',
          description: 'Ingests application logs, normalises them and exposes a query API with full-text search over MongoDB.',
          techStack: ['Python', 'MongoDB', 'Linux', 'REST API'],
          repositoryUrl: 'https://github.com/priya-nair/log-aggregator',
          liveDemoUrl: null,
          imageUrl: null,
          role: 'Solo developer',
          features: ['Structured log parsing', 'Retention via TTL indexes'],
          achievements: ['Sustained 3k log lines/sec on a single node.'],
          startDate: new Date(Date.UTC(2026, 0, 15)),
          endDate: null,
          featured: false,
          includeInPortfolio: true,
          createdAt: ago(now, 15),
        },
      ],
      jobs: [
        {
          idx: 301,
          title: 'Backend Engineer',
          company: 'Stratafy',
          location: 'Remote',
          employmentType: 'FULL_TIME',
          daysAgo: 16,
          required: ['python', 'postgresql', 'rest api', 'docker'],
          niceToHave: ['kubernetes', 'aws', 'redis'],
          generateResume: true,
          resumeTemplate: 'ATS_CLASSIC',
        },
        {
          idx: 302,
          title: 'Java Backend Developer',
          company: 'Nexora Labs',
          location: 'Pune, India (Hybrid)',
          employmentType: 'FULL_TIME',
          daysAgo: 9,
          required: ['java', 'spring boot', 'rest api', 'mysql', 'hibernate', 'microservices'],
          niceToHave: ['docker', 'aws', 'kafka'],
          generateResume: false,
        },
        {
          idx: 303,
          title: 'Cloud Platform Engineer',
          company: 'Skyforge Cloud',
          location: 'Remote',
          employmentType: 'FULL_TIME',
          daysAgo: 0,
          required: ['aws', 'docker', 'kubernetes', 'linux', 'ci cd'],
          niceToHave: ['python', 'redis'],
          generateResume: false,
        },
      ],
      portfolios: [
        {
          idx: 604,
          name: 'Backend Engineer Portfolio',
          templateIdx: 2,
          fromJobIdx: 301,
          isPublished: true,
          publishedDaysAgo: 15,
          sections: { about: true, skills: true, projects: true, education: true, experience: true, certificates: false, contact: true },
          headlineOverride: null,
        },
      ],
    },

    // =========================================================================
    // 5. ROHAN - SUSPENDED, so the admin user-moderation screens have a subject
    // =========================================================================
    {
      idx: 5,
      username: 'rohan-verma',
      email: 'rohan@portfoliopilot.local',
      name: 'Rohan Verma',
      role: 'USER',
      status: 'SUSPENDED',
      createdAt: ago(now, 9),
      lastLoginAt: ago(now, 6),
      profile: {
        professionalTitle: 'Junior Developer',
        bio: 'Learning web development.',
        location: 'Jaipur, India',
        contact: {},
        skills: [
          { name: 'HTML5', proficiency: 'INTERMEDIATE', yearsOfExperience: 1 },
          { name: 'CSS3', proficiency: 'INTERMEDIATE', yearsOfExperience: 1 },
          { name: 'JavaScript', proficiency: 'BEGINNER', yearsOfExperience: 1 },
        ],
        education: [
          {
            subIdx: 51,
            degree: 'BCA',
            institution: 'University of Rajasthan',
            fieldOfStudy: 'Computer Applications',
            startYear: 2023,
            endYear: 2026,
            grade: null,
            description: null,
          },
        ],
        experience: [],
        certificates: [],
      },
      projects: [
        {
          idx: 41,
          title: 'Personal Blog',
          description: 'Static blog built from scratch with semantic HTML and CSS Grid.',
          techStack: ['HTML5', 'CSS3', 'JavaScript'],
          repositoryUrl: null,
          liveDemoUrl: null,
          imageUrl: null,
          role: 'Solo developer',
          features: [],
          achievements: [],
          startDate: new Date(Date.UTC(2026, 3, 2)),
          endDate: null,
          featured: false,
          includeInPortfolio: true,
          createdAt: ago(now, 8),
        },
      ],
      jobs: [
        {
          idx: 401,
          title: 'Frontend Developer Intern',
          company: 'Brightpix Studio',
          location: 'Remote',
          employmentType: 'INTERNSHIP',
          daysAgo: 7,
          required: ['react', 'javascript', 'css', 'html'],
          niceToHave: ['typescript', 'tailwind css', 'git'],
          generateResume: false,
        },
      ],
      // Suspended accounts keep their data but nothing stays public.
      portfolios: [],
    },

    // =========================================================================
    // 6. NEHA - frontend / design leaning
    // =========================================================================
    {
      idx: 6,
      username: 'neha-iyer',
      email: 'neha@portfoliopilot.local',
      name: 'Neha Iyer',
      role: 'USER',
      status: 'ACTIVE',
      createdAt: ago(now, 14),
      lastLoginAt: ago(now, 1),
      profile: {
        professionalTitle: 'Frontend Engineer',
        bio: 'Design-minded frontend engineer. Builds fast, accessible interfaces and cares about the details users never consciously notice.',
        location: 'Bengaluru, India',
        contact: { github: 'https://github.com/neha-iyer', linkedin: 'https://linkedin.com/in/neha-iyer' },
        skills: [
          { name: 'React', proficiency: 'ADVANCED', yearsOfExperience: 3 },
          { name: 'TypeScript', proficiency: 'ADVANCED', yearsOfExperience: 2 },
          { name: 'Next.js', proficiency: 'INTERMEDIATE', yearsOfExperience: 1 },
          { name: 'Tailwind CSS', proficiency: 'ADVANCED', yearsOfExperience: 2 },
          { name: 'HTML5', proficiency: 'EXPERT', yearsOfExperience: 4 },
          { name: 'CSS3', proficiency: 'EXPERT', yearsOfExperience: 4 },
          { name: 'Figma', proficiency: 'ADVANCED', yearsOfExperience: 3 },
          { name: 'Jest', proficiency: 'INTERMEDIATE', yearsOfExperience: 1 },
          { name: 'Git', proficiency: 'ADVANCED', yearsOfExperience: 3 },
        ],
        education: [
          {
            subIdx: 61,
            degree: 'BE Information Science',
            institution: 'RV College of Engineering',
            fieldOfStudy: 'Information Science',
            startYear: 2021,
            endYear: 2025,
            grade: '8.4 CGPA',
            description: null,
          },
        ],
        experience: [
          {
            subIdx: 62,
            company: 'Pixelworks Studio',
            role: 'Frontend Intern',
            location: 'Remote',
            employmentType: 'INTERNSHIP',
            startDate: new Date(Date.UTC(2025, 5, 2)),
            endDate: new Date(Date.UTC(2025, 10, 28)),
            description: 'Rebuilt the marketing site component library.',
            responsibilities: [
              'Migrated 40 components from CSS modules to Tailwind with zero visual regressions.',
              'Improved Lighthouse performance from 61 to 94 on the landing page.',
            ],
            technologies: ['React', 'TypeScript', 'Tailwind CSS'],
          },
        ],
        certificates: [],
      },
      projects: [
        {
          idx: 51,
          title: 'Design System Kit',
          description: 'Themeable React design system with 34 components, dark mode, and a documented token layer. Published to a private npm registry.',
          techStack: ['React', 'TypeScript', 'Tailwind CSS', 'Jest'],
          repositoryUrl: 'https://github.com/neha-iyer/design-system-kit',
          liveDemoUrl: 'https://ds-kit.example.com',
          imageUrl: null,
          role: 'Solo developer',
          features: ['Token-driven theming', 'Dark mode without a flash of unstyled content'],
          achievements: ['Adopted by three internal teams; cut new-page build time roughly in half.'],
          startDate: new Date(Date.UTC(2025, 9, 1)),
          endDate: null,
          featured: true,
          includeInPortfolio: true,
          createdAt: ago(now, 12),
        },
        {
          idx: 52,
          title: 'Recipe Explorer',
          description: 'Next.js recipe browser with server-side search, ingredient filtering and offline support.',
          techStack: ['Next.js', 'React', 'TypeScript'],
          repositoryUrl: 'https://github.com/neha-iyer/recipe-explorer',
          liveDemoUrl: null,
          imageUrl: null,
          role: 'Solo developer',
          features: ['Server-side rendering', 'Offline cache'],
          achievements: [],
          startDate: new Date(Date.UTC(2025, 2, 10)),
          endDate: new Date(Date.UTC(2025, 4, 20)),
          featured: false,
          includeInPortfolio: true,
          createdAt: ago(now, 11),
        },
      ],
      jobs: [
        {
          idx: 501,
          title: 'Frontend Developer',
          company: 'Lumina Tech',
          location: 'Remote',
          employmentType: 'FULL_TIME',
          daysAgo: 6,
          required: ['react', 'typescript', 'css', 'html'],
          niceToHave: ['nextjs', 'jest', 'figma'],
          generateResume: true,
          resumeTemplate: 'ATS_MODERN',
        },
        {
          idx: 502,
          title: 'Full Stack Engineer (MERN)',
          company: 'Cloudspire',
          location: 'Bengaluru, India',
          employmentType: 'FULL_TIME',
          daysAgo: 2,
          required: ['react', 'nodejs', 'expressjs', 'mongodb', 'rest api'],
          niceToHave: ['typescript', 'docker'],
          generateResume: false,
        },
      ],
      portfolios: [
        {
          idx: 605,
          name: 'Frontend Portfolio',
          templateIdx: 3,
          fromJobIdx: 501,
          isPublished: true,
          publishedDaysAgo: 5,
          sections: { about: true, skills: true, projects: true, education: true, experience: true, certificates: false, contact: true },
          headlineOverride: null,
        },
      ],
    },

    // =========================================================================
    // 7. ARJUN - cloud / DevOps leaning, so the gap chart is not all frontend
    // =========================================================================
    {
      idx: 7,
      username: 'arjun-mehta',
      email: 'arjun@portfoliopilot.local',
      name: 'Arjun Mehta',
      role: 'USER',
      status: 'ACTIVE',
      createdAt: ago(now, 5),
      lastLoginAt: ago(now, 0),
      profile: {
        professionalTitle: 'Cloud & DevOps Engineer',
        bio: 'Automates the boring parts. Containers, pipelines and infrastructure that other engineers never have to think about.',
        location: 'Remote',
        contact: { github: 'https://github.com/arjun-mehta' },
        skills: [
          { name: 'Docker', proficiency: 'ADVANCED', yearsOfExperience: 3 },
          { name: 'Kubernetes', proficiency: 'INTERMEDIATE', yearsOfExperience: 2 },
          { name: 'AWS', proficiency: 'ADVANCED', yearsOfExperience: 3 },
          { name: 'Linux', proficiency: 'EXPERT', yearsOfExperience: 5 },
          { name: 'CI/CD', proficiency: 'ADVANCED', yearsOfExperience: 3 },
          { name: 'Python', proficiency: 'INTERMEDIATE', yearsOfExperience: 2 },
          { name: 'Git', proficiency: 'EXPERT', yearsOfExperience: 5 },
        ],
        education: [
          {
            subIdx: 71,
            degree: 'BTech Electronics and Communication',
            institution: 'VIT Vellore',
            fieldOfStudy: 'Electronics and Communication',
            startYear: 2019,
            endYear: 2023,
            grade: '7.9 CGPA',
            description: null,
          },
        ],
        experience: [],
        certificates: [],
      },
      projects: [
        {
          idx: 61,
          title: 'Zero-Downtime Deploy Pipeline',
          description: 'GitHub Actions pipeline performing blue-green deploys to ECS with automated rollback on failed health checks.',
          techStack: ['Docker', 'AWS', 'CI/CD', 'Linux'],
          repositoryUrl: 'https://github.com/arjun-mehta/deploy-pipeline',
          liveDemoUrl: null,
          imageUrl: null,
          role: 'Solo developer',
          features: ['Blue-green cutover', 'Automatic rollback on health-check failure'],
          achievements: ['Took deploys from a 12-minute maintenance window to zero downtime.'],
          startDate: new Date(Date.UTC(2025, 7, 5)),
          endDate: null,
          featured: true,
          includeInPortfolio: true,
          createdAt: ago(now, 4),
        },
        {
          idx: 62,
          title: 'Cluster Cost Reporter',
          description: 'Python service that tags Kubernetes workloads by team and produces a weekly cost attribution report.',
          techStack: ['Python', 'Kubernetes', 'Linux'],
          repositoryUrl: 'https://github.com/arjun-mehta/cluster-cost',
          liveDemoUrl: null,
          imageUrl: null,
          role: 'Solo developer',
          features: ['Per-namespace attribution'],
          achievements: ['Surfaced an idle node group costing about $400 a month.'],
          startDate: new Date(Date.UTC(2026, 1, 3)),
          endDate: null,
          featured: false,
          includeInPortfolio: true,
          createdAt: ago(now, 3),
        },
      ],
      jobs: [
        {
          idx: 511,
          title: 'Cloud Platform Engineer',
          company: 'Skyforge Cloud',
          location: 'Remote',
          employmentType: 'FULL_TIME',
          daysAgo: 1,
          required: ['aws', 'docker', 'kubernetes', 'linux', 'ci cd'],
          niceToHave: ['python', 'redis'],
          generateResume: true,
          resumeTemplate: 'ATS_COMPACT',
        },
      ],
      portfolios: [
        {
          idx: 606,
          name: 'Platform Engineering Portfolio',
          templateIdx: 2,
          fromJobIdx: 511,
          isPublished: true,
          publishedDaysAgo: 1,
          sections: { about: true, skills: true, projects: true, education: true, experience: false, certificates: false, contact: true },
          headlineOverride: null,
        },
      ],
    },
  ];
}

/**
 * Builds a realistic job-description body from the declared requirement lists.
 * Keeping the text generated (rather than hand-written) guarantees the JD text
 * and the scored `requiredSkills` can never drift apart.
 */
function buildJobDescription(job, displayFor) {
  const req = job.required.map(displayFor);
  const nice = (job.niceToHave || []).map(displayFor);

  return [
    `${job.company || 'A fast-growing product company'} is hiring a ${job.title}${job.location ? ` (${job.location})` : ''}.`,
    '',
    'About the role',
    `You will design, build and ship features end to end alongside a small product team. You will own your services in production, participate in code review, and work closely with design and QA.`,
    '',
    'Requirements',
    ...req.map((s) => `- Hands-on experience with ${s}`),
    '- Bachelor\'s degree in Computer Science, IT or equivalent practical experience',
    '- A portfolio of shipped projects you can talk through in depth',
    '',
    ...(nice.length ? ['Nice to have', ...nice.map((s) => `- Exposure to ${s}`), ''] : []),
    'What we offer',
    '- Mentorship from senior engineers, a real ownership scope, and a flexible hybrid schedule.',
  ].join('\n');
}

module.exports = { buildAccounts, buildJobDescription, ago, DAY };
