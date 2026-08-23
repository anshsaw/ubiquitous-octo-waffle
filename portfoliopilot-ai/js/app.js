/**
 * PortfolioPilot AI — Core Application Logic
 * Mock data + generateMatch + localStorage state
 * Backend-ready: replace mock functions with real API calls later
 */

// ========== MOCK USER PROFILE ==========
const defaultProfile = {
  name: 'Alex Rivera',
  email: 'alex@example.com',
  title: 'Full-Stack Developer',
  bio: 'Building delightful web experiences with React, TypeScript and Node.js. Passionate about clean design and adaptive tools.',
  location: 'San Francisco, CA',
  avatar: null,
  skills: [
    { name: 'React', level: 90 },
    { name: 'TypeScript', level: 85 },
    { name: 'Node.js', level: 80 },
    { name: 'Tailwind CSS', level: 88 },
    { name: 'PostgreSQL', level: 70 },
    { name: 'Git', level: 85 },
  ],
  projects: [
    {
      id: 'p1',
      title: 'PortfolioPilot AI',
      description: 'Adaptive portfolio builder that generates tailored portfolios and resumes for every job application using AI matching.',
      tech: ['React', 'TypeScript', 'Tailwind', 'Zustand'],
      repo: 'https://github.com/alex/portfoliopilot',
      live: 'https://portfoliopilot.ai',
      includeInPortfolio: true,
    },
    {
      id: 'p2',
      title: 'TaskFlow',
      description: 'Real-time collaborative task manager with drag-and-drop boards and team presence indicators.',
      tech: ['React', 'Node.js', 'Socket.io', 'MongoDB'],
      repo: 'https://github.com/alex/taskflow',
      live: '',
      includeInPortfolio: true,
    },
    {
      id: 'p3',
      title: 'WeatherDash',
      description: 'Beautiful weather dashboard with forecasts, maps and location-based alerts.',
      tech: ['JavaScript', 'Chart.js', 'OpenWeather API'],
      repo: '',
      live: 'https://weatherdash.demo',
      includeInPortfolio: false,
    },
  ],
  education: [
    { degree: 'B.S. Computer Science', school: 'Stanford University', year: '2024' },
  ],
  experience: [
    { role: 'Frontend Intern', company: 'TechCorp', period: '2023 – 2024', description: 'Built responsive UI components and improved page load times by 40%.' },
  ],
};

// ========== ADMIN MOCK DATA ==========
const adminStats = {
  totalUsers: 1284,
  portfoliosPublished: 892,
  jobsAnalyzedToday: 47,
  avgMatchScore: 78,
};

const dailySignups = [
  { day: 'Mon', count: 42 },
  { day: 'Tue', count: 58 },
  { day: 'Wed', count: 71 },
  { day: 'Thu', count: 65 },
  { day: 'Fri', count: 89 },
  { day: 'Sat', count: 34 },
  { day: 'Sun', count: 28 },
];

const skillGapsData = [
  { skill: 'System Design', count: 312 },
  { skill: 'AWS', count: 287 },
  { skill: 'Kubernetes', count: 198 },
  { skill: 'GraphQL', count: 156 },
  { skill: 'CI/CD', count: 143 },
];

const mockUsers = [
  { id: '1', name: 'Alex Rivera', email: 'alex@example.com', portfolios: 3, status: 'active', joinDate: '2025-11-12' },
  { id: '2', name: 'Sam Chen', email: 'sam@example.com', portfolios: 1, status: 'active', joinDate: '2026-01-03' },
  { id: '3', name: 'Jordan Lee', email: 'jordan@example.com', portfolios: 5, status: 'suspended', joinDate: '2025-09-20' },
  { id: '4', name: 'Taylor Kim', email: 'taylor@example.com', portfolios: 2, status: 'active', joinDate: '2026-03-15' },
  { id: '5', name: 'Morgan Patel', email: 'morgan@example.com', portfolios: 0, status: 'active', joinDate: '2026-07-01' },
];

const mockTemplates = [
  { id: 't1', name: 'Minimal Pro', active: true },
  { id: 't2', name: 'Modern Gradient', active: true },
  { id: 't3', name: 'Classic ATS', active: false },
  { id: 't4', name: 'Creative Dark', active: true },
];

const mockJobLogs = [
  { id: 'j1', title: 'Senior Frontend Engineer', company: 'Stripe', match: 86, date: '2026-08-19', user: 'Alex Rivera' },
  { id: 'j2', title: 'Full-Stack Intern', company: 'Notion', match: 74, date: '2026-08-19', user: 'Sam Chen' },
  { id: 'j3', title: 'React Developer', company: 'Vercel', match: 91, date: '2026-08-18', user: 'Alex Rivera' },
  { id: 'j4', title: 'Software Engineer', company: 'Google', match: 68, date: '2026-08-18', user: 'Jordan Lee' },
  { id: 'j5', title: 'UI Engineer', company: 'Figma', match: 82, date: '2026-08-17', user: 'Taylor Kim' },
];

// ========== STATE (localStorage) ==========
function getProfile() {
  const raw = localStorage.getItem('pp_profile');
  return raw ? JSON.parse(raw) : { ...defaultProfile };
}

function saveProfile(profile) {
  localStorage.setItem('pp_profile', JSON.stringify(profile));
}

function isLoggedIn() {
  return localStorage.getItem('pp_auth') === 'true';
}

function setLoggedIn(value) {
  localStorage.setItem('pp_auth', value ? 'true' : 'false');
}

function getLastMatch() {
  const raw = localStorage.getItem('pp_last_match');
  return raw ? JSON.parse(raw) : null;
}

function saveLastMatch(match) {
  localStorage.setItem('pp_last_match', JSON.stringify(match));
}

function getSelectedTemplate() {
  return localStorage.getItem('pp_template') || 't1';
}

function setSelectedTemplate(id) {
  localStorage.setItem('pp_template', id);
}

// ========== CORE: generateMatch ==========
/**
 * generateMatch(jobDescription, userProfile)
 * Keyword + simple semantic heuristic.
 * Returns everything needed for Match Analysis + tailored resume.
 * Replace body with real AI/backend call later.
 */
function generateMatch(jobDescription, userProfile) {
  const jobLower = (jobDescription || '').toLowerCase();
  const userSkills = (userProfile.skills || []).map(s => s.name.toLowerCase());

  // Strong skills = user skills that appear in the job description
  const strongSkills = userSkills
    .filter(s => jobLower.includes(s))
    .map(s => s.charAt(0).toUpperCase() + s.slice(1));

  // Common skill gaps that jobs often ask for
  const commonGaps = [
    'kubernetes', 'aws', 'graphql', 'system design', 'ci/cd', 'docker',
    'python', 'java', 'go', 'redis', 'kafka', 'microservices', 'figma',
  ];
  const skillGaps = commonGaps
    .filter(g => jobLower.includes(g) && !userSkills.some(s => s.includes(g) || g.includes(s)))
    .map(g => g.split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' '));

  // Scores (0-100)
  const skillsMatch = Math.min(95, Math.round((strongSkills.length / Math.max(userSkills.length, 1)) * 70 + 25 + Math.random() * 10));
  const matchingProjects = (userProfile.projects || []).filter(p =>
    (p.tech || []).some(t => jobLower.includes(t.toLowerCase()))
  );
  const projectsMatch = Math.min(92, 50 + matchingProjects.length * 15 + Math.round(Math.random() * 8));
  const requirementsMatch = Math.min(90, Math.round((skillsMatch + projectsMatch) / 2 + (Math.random() * 10 - 5)));
  const matchScore = Math.round(skillsMatch * 0.4 + projectsMatch * 0.35 + requirementsMatch * 0.25);

  const recommendedProjects = matchingProjects.length
    ? matchingProjects.slice(0, 3).map(p => p.id)
    : (userProfile.projects || []).filter(p => p.includeInPortfolio).slice(0, 2).map(p => p.id);

  // Auto-written summary tailored to the role
  const jobTitleGuess = jobDescription.split('\n')[0]?.slice(0, 80) || 'the target role';
  const topSkills = strongSkills.slice(0, 3).join(', ') || 'modern web technologies';
  const tailoredSummary = `Results-driven ${userProfile.title || 'developer'} with proven expertise in ${topSkills}. Passionate about delivering high-impact solutions aligned with the requirements of ${jobTitleGuess}. Eager to contribute strong problem-solving skills and a track record of shipping polished products.`;

  return {
    matchScore: Math.max(45, Math.min(98, matchScore)),
    skillsMatch: Math.max(40, Math.min(98, skillsMatch)),
    projectsMatch: Math.max(40, Math.min(98, projectsMatch)),
    requirementsMatch: Math.max(40, Math.min(98, requirementsMatch)),
    strongSkills: strongSkills.length ? strongSkills : ['React', 'TypeScript', 'JavaScript'],
    skillGaps: skillGaps.length ? skillGaps.slice(0, 5) : ['System Design', 'AWS'],
    recommendedProjects,
    tailoredSummary,
    jobTitle: jobDescription.split('\n')[0]?.trim().slice(0, 60) || 'Job Opportunity',
    analyzedAt: new Date().toISOString(),
  };
}

// Simulated async analysis (shows skeleton / loading)
async function analyzeJob(jobDescription, userProfile) {
  await new Promise(r => setTimeout(r, 1100)); // fake latency
  return generateMatch(jobDescription, userProfile);
}

// ========== UI HELPERS ==========
function $(sel, ctx = document) {
  return ctx.querySelector(sel);
}
function $$(sel, ctx = document) {
  return Array.from(ctx.querySelectorAll(sel));
}

function showToast(message, type = 'info') {
  let toast = $('#toast');
  if (!toast) {
    toast = document.createElement('div');
    toast.id = 'toast';
    toast.style.cssText = 'position:fixed;bottom:1.5rem;right:1.5rem;padding:0.875rem 1.25rem;border-radius:0.5rem;font-size:0.875rem;font-weight:500;z-index:200;box-shadow:0 10px 15px -3px rgba(0,0,0,0.1);transition:all 0.3s;opacity:0;transform:translateY(10px);';
    document.body.appendChild(toast);
  }
  const colors = {
    success: 'background:#D1FAE5;color:#047857;',
    error: 'background:#FEE2E2;color:#B91C1C;',
    info: 'background:#EEF2FF;color:#4F46E5;',
  };
  toast.style.cssText += colors[type] || colors.info;
  toast.textContent = message;
  toast.style.opacity = '1';
  toast.style.transform = 'translateY(0)';
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(10px)';
  }, 2800);
}

function requireAuth() {
  if (!isLoggedIn()) {
    window.location.href = 'login.html';
    return false;
  }
  return true;
}

function logout() {
  setLoggedIn(false);
  window.location.href = 'login.html';
}

// Sidebar mobile toggle
function initSidebar() {
  const toggle = $('#menuToggle');
  const sidebar = $('#sidebar');
  const overlay = $('#sidebarOverlay');
  if (!toggle || !sidebar) return;

  toggle.addEventListener('click', () => {
    sidebar.classList.toggle('open');
    overlay?.classList.toggle('open');
  });
  overlay?.addEventListener('click', () => {
    sidebar.classList.remove('open');
    overlay.classList.remove('open');
  });
}

// Highlight active nav item
function setActiveNav(page) {
  $$('.nav-item').forEach(el => {
    el.classList.toggle('active', el.dataset.page === page);
  });
}

// Init common layout pieces
function initAppLayout(pageName) {
  initSidebar();
  setActiveNav(pageName);
  const nameEl = $('#userName');
  if (nameEl) {
    const profile = getProfile();
    nameEl.textContent = profile.name?.split(' ')[0] || 'User';
  }
}

// Password strength indicator
function updatePasswordStrength(input, bar, label) {
  const val = input.value;
  let score = 0;
  if (val.length >= 8) score++;
  if (/[A-Z]/.test(val)) score++;
  if (/[0-9]/.test(val)) score++;
  if (/[^A-Za-z0-9]/.test(val)) score++;

  const pct = (score / 4) * 100;
  const colors = ['#EF4444', '#F59E0B', '#F59E0B', '#10B981'];
  const labels = ['Weak', 'Fair', 'Good', 'Strong'];
  bar.style.width = pct + '%';
  bar.style.background = colors[score - 1] || '#E2E8F0';
  if (label) label.textContent = score ? labels[score - 1] : '';
}

// Export for other scripts
window.PP = {
  getProfile,
  saveProfile,
  isLoggedIn,
  setLoggedIn,
  getLastMatch,
  saveLastMatch,
  getSelectedTemplate,
  setSelectedTemplate,
  generateMatch,
  analyzeJob,
  showToast,
  requireAuth,
  logout,
  initAppLayout,
  updatePasswordStrength,
  adminStats,
  dailySignups,
  skillGapsData,
  mockUsers,
  mockTemplates,
  mockJobLogs,
  $,
  $$,
};
