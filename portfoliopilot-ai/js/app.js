/**
 * PortfolioPilot AI — Core Application Logic
 * ==========================================
 *
 * Backed by the Spring Boot API (see js/api.js). No mock data remains for any
 * core feature: profile, projects, analysis, resumes and portfolios are all
 * real, persisted in MongoDB.
 *
 * WHY THE ACCESSORS ARE STILL SYNCHRONOUS
 * ---------------------------------------
 * Every page does this, inline, immediately:
 *
 *     if (!PP.requireAuth()) throw new Error('redirect');
 *     PP.initAppLayout('dashboard');
 *     const profile = PP.getProfile();      // <- synchronous
 *
 * Turning `getProfile()` into a promise would require editing every one of
 * those call sites across seven HTML files. Instead this module keeps a
 * localStorage-backed CACHE that is:
 *
 *   - written at login/register, before the redirect fires, so it is always
 *     warm by the time a page renders;
 *   - refreshed from the API in the background on every page load;
 *   - written through to the API on every save.
 *
 * When a background refresh brings in changed data, a `pp:data` event is
 * dispatched so a page can re-render if it wants to. Pages that ignore it still
 * work — they simply render the (correct, just-persisted) cached copy.
 *
 * This is a deliberate offline-first trade-off, not an oversight.
 */
(function () {
  'use strict';

  const api = window.PPApi;
  if (!api) {
    console.error('[PP] js/api.js must be loaded before js/app.js');
  }

  // ------------------------------------------------------------------ cache

  const CACHE = {
    profile: 'pp_profile',
    lastMatch: 'pp_last_match',
    lastMatchId: 'pp_last_match_id',
    template: 'pp_template',
    portfolioId: 'pp_portfolio_id',
  };

  function readCache(key, fallback) {
    try {
      const raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : fallback;
    } catch (e) {
      return fallback;
    }
  }

  function writeCache(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (e) {
      /* quota exceeded — the API remains the source of truth */
    }
  }

  /** Lets a page re-render when a background refresh changes something. */
  function emit(what) {
    document.dispatchEvent(new CustomEvent('pp:data', { detail: { what } }));
  }

  // ------------------------------------------------------- shape adapters

  /**
   * API profile + projects -> the legacy shape the HTML templates read.
   *
   * The backend already emits display aliases (`title`, `school`, `year`,
   * `period`, `level`, `tech`, `repo`, `live`) precisely so this adapter stays
   * thin and no template needs changing.
   */
  function toLegacyProfile(apiProfile, apiProjects) {
    return {
      name: apiProfile.name || '',
      email: apiProfile.email || '',
      title: apiProfile.professionalTitle || apiProfile.title || '',
      bio: apiProfile.bio || '',
      location: apiProfile.location || '',
      avatar: apiProfile.avatarUrl || null,
      profileHealth: apiProfile.profileHealth || 0,

      skills: (apiProfile.skills || []).map((s) => ({
        name: s.name,
        level: s.level || 50,
        proficiency: s.proficiency,
        normalizedName: s.normalizedName,
      })),

      education: (apiProfile.education || []).map((e) => ({
        id: e.id,
        degree: e.degree,
        school: e.institution || e.school,
        year: e.year || String(e.endYear || e.startYear || ''),
        startYear: e.startYear,
        endYear: e.endYear,
      })),

      experience: (apiProfile.experience || []).map((x) => ({
        id: x.id,
        role: x.role,
        company: x.company,
        period: x.period || '',
        description: x.description || '',
      })),

      certificates: apiProfile.certificates || [],

      projects: (apiProjects || []).map((p) => ({
        id: p.id,
        title: p.title,
        description: p.description,
        tech: p.techStack || p.tech || [],
        repo: p.repositoryUrl || '',
        live: p.liveDemoUrl || '',
        includeInPortfolio: p.includeInPortfolio,
        featured: p.featured,
      })),
    };
  }

  /** API analysis -> the legacy match shape used by match-analysis.html. */
  function toLegacyMatch(analysis) {
    return {
      id: analysis.id,
      matchScore: analysis.matchScore,
      skillsMatch: analysis.skillsMatch,
      projectsMatch: analysis.projectsMatch,
      requirementsMatch: analysis.requirementsMatch,
      strongSkills: analysis.strongSkills || [],
      skillGaps: analysis.skillGaps || [],
      // The templates expect an array of project ID STRINGS.
      recommendedProjects: (analysis.recommendedProjects || []).map((p) => p.projectId),
      recommendedProjectDetails: analysis.recommendedProjects || [],
      tailoredSummary: analysis.tailoredSummary || '',
      jobTitle: analysis.jobTitle || 'Job Opportunity',
      company: analysis.company || '',
      analyzedAt: analysis.createdAt,
    };
  }

  // ------------------------------------------------------------- accessors

  /** Synchronous read of the cached profile. Warm from login onward. */
  function getProfile() {
    return readCache(CACHE.profile, {
      name: '', email: '', title: '', bio: '', location: '', avatar: null,
      skills: [], projects: [], education: [], experience: [], certificates: [],
    });
  }

  /**
   * Write-through save.
   *
   * The whole legacy blob is diffed into the API's sub-resource endpoints, so a
   * bio edit never rewrites the skill list and vice versa.
   */
  async function saveProfile(profile) {
    writeCache(CACHE.profile, profile);

    try {
      await api.profile.patch({
        name: profile.name,
        professionalTitle: profile.title,
        bio: profile.bio,
        location: profile.location,
        avatarUrl: profile.avatar,
      });

      await api.profile.replaceSkills(
        (profile.skills || []).map((s) => ({ name: s.name, level: s.level })));

      await syncEducation(profile.education || []);
      await refreshProfile();
      return true;
    } catch (err) {
      showToast(err.message || 'Could not save your profile', 'error');
      return false;
    }
  }

  /**
   * Reconciles the cached education list against the server.
   *
   * Entries the UI created via prompt() have no id, so they are POSTed; entries
   * the server knows about but the cache no longer contains were deleted.
   */
  async function syncEducation(entries) {
    const server = (await api.profile.get()).education || [];
    const keptIds = entries.filter((e) => e.id).map((e) => e.id);

    for (const existing of server) {
      if (!keptIds.includes(existing.id)) {
        await api.profile.deleteEducation(existing.id).catch(() => null);
      }
    }
    for (const entry of entries) {
      if (entry.id) continue;
      const years = parseYears(entry.year);
      await api.profile.addEducation({
        degree: entry.degree,
        institution: entry.school || entry.institution,
        startYear: years.start,
        endYear: years.end,
      }).catch(() => null);
    }
  }

  /** "2024" / "2023 - 2026" / "2026 - Present" -> { start, end }. */
  function parseYears(raw) {
    const found = String(raw || '').match(/\d{4}/g) || [];
    if (found.length === 0) return { start: null, end: null };
    if (found.length === 1) return { start: Number(found[0]), end: Number(found[0]) };
    return { start: Number(found[0]), end: Number(found[1]) };
  }

  /** Pulls profile + projects and refreshes the cache. */
  async function refreshProfile() {
    if (!api.tokens.exists()) return null;
    try {
      const [apiProfile, apiProjects] = await Promise.all([
        api.profile.get(),
        api.projects.list(),
      ]);
      const legacy = toLegacyProfile(apiProfile, apiProjects);
      writeCache(CACHE.profile, legacy);
      emit('profile');
      return legacy;
    } catch (err) {
      if (err.status === 401) handleSessionExpired();
      return null;
    }
  }

  // ------------------------------------------------------------------ auth

  function isLoggedIn() {
    return api.tokens.exists();
  }

  /** Kept for API compatibility with the old facade; the token is the real state. */
  function setLoggedIn(value) {
    if (!value) api.tokens.clear();
  }

  async function login(email, password) {
    await api.auth.login(email, password);
    // Warm the cache BEFORE the caller redirects, so the next page renders
    // real data on its very first synchronous read.
    await refreshProfile();
    return true;
  }

  async function register(name, email, password) {
    await api.auth.register(name, email, password);
    await refreshProfile();
    return true;
  }

  function logout() {
    api.auth.logout().finally(() => {
      localStorage.removeItem(CACHE.profile);
      localStorage.removeItem(CACHE.lastMatch);
      localStorage.removeItem(CACHE.lastMatchId);
      localStorage.removeItem(CACHE.portfolioId);
      window.location.href = 'login.html';
    });
  }

  function handleSessionExpired() {
    api.tokens.clear();
    showToast('Your session expired. Please sign in again.', 'error');
    setTimeout(() => { window.location.href = 'login.html'; }, 900);
  }

  function requireAuth() {
    if (!isLoggedIn()) {
      window.location.href = 'login.html';
      return false;
    }
    return true;
  }

  // ------------------------------------------------------------- analysis

  function getLastMatch() {
    return readCache(CACHE.lastMatch, null);
  }

  function saveLastMatch(match) {
    writeCache(CACHE.lastMatch, match);
    if (match && match.id) localStorage.setItem(CACHE.lastMatchId, match.id);
  }

  /**
   * Runs a real analysis on the server.
   *
   * The old client-side `generateMatch` used Math.random() three times, so the
   * same job produced a different score on every run. The server version is
   * deterministic and the result is persisted, which is what makes the
   * dashboard history and the admin analytics meaningful.
   */
  async function analyzeJob(jobDescription, jobTitle, company) {
    const title = jobTitle || (jobDescription.split('\n')[0] || 'Job Opportunity').trim().slice(0, 60);
    const analysis = await api.opportunities.analyze(title, company || null, jobDescription);
    const match = toLegacyMatch(analysis);
    saveLastMatch(match);
    return match;
  }

  /** Loads a stored analysis by id (deep link from the dashboard). */
  async function loadAnalysis(id) {
    const analysis = await api.opportunities.get(id);
    const match = toLegacyMatch(analysis);
    saveLastMatch(match);
    return match;
  }

  async function generateResume(analysisId) {
    return api.resumes.generate(analysisId || localStorage.getItem(CACHE.lastMatchId));
  }

  // ------------------------------------------------------------ portfolio

  function getSelectedTemplate() {
    return localStorage.getItem(CACHE.template) || 't1';
  }

  function setSelectedTemplate(id) {
    localStorage.setItem(CACHE.template, id);
  }

  /** "Adapt Portfolio for this Job" / "Generate Portfolio". */
  async function generatePortfolio(templateKey) {
    const analysisId = localStorage.getItem(CACHE.lastMatchId);
    const portfolio = await api.portfolio.generate({
      jobAnalysisId: analysisId || null,
      templateKey: templateKey || null,
    });
    localStorage.setItem(CACHE.portfolioId, portfolio.id);
    return portfolio;
  }

  async function publishPortfolio(portfolioId) {
    const id = portfolioId || localStorage.getItem(CACHE.portfolioId);
    if (!id) throw new Error('Generate a portfolio first');
    return api.portfolio.publish(id);
  }

  // ---------------------------------------------------------------- admin

  /**
   * Real admin data.
   *
   * The old hardcoded `PP.adminStats` / `mockUsers` / `mockJobLogs` arrays are
   * gone. These loaders return promises; an admin page should await them, e.g.
   *
   *     const stats = await PP.admin.stats();
   */
  const admin = {
    stats: () => api.admin.stats(),
    signupTrends: (days) => api.admin.signupTrends(days),
    skillGaps: (limit) => api.admin.skillGaps(limit),
    users: (params) => api.admin.users(params),
    userDetail: (id) => api.admin.userDetail(id),
    suspendUser: (id, reason) => api.admin.suspendUser(id, reason),
    activateUser: (id) => api.admin.activateUser(id),
    deleteUser: (id) => api.admin.deleteUser(id),
    jobAnalyses: (params) => api.admin.jobAnalyses(params),
    templates: () => api.admin.templates(),
    createTemplate: (payload) => api.admin.createTemplate(payload),
    updateTemplate: (id, payload) => api.admin.updateTemplate(id, payload),
    setTemplateStatus: (id, active) => api.admin.setTemplateStatus(id, active),
    deleteTemplate: (id) => api.admin.deleteTemplate(id),

    isAdmin() {
      return localStorage.getItem('pp_admin') === 'true';
    },

    /** Admin sign-in is the same endpoint; the ADMIN role is verified server-side. */
    async login(email, password) {
      const data = await api.auth.login(email, password);
      if (!data.user || data.user.role !== 'ADMIN') {
        api.tokens.clear();
        throw new Error('This account does not have administrator access');
      }
      localStorage.setItem('pp_admin', 'true');
      return data;
    },

    logout() {
      localStorage.removeItem('pp_admin');
      api.auth.logout().finally(() => { window.location.href = 'login.html'; });
    },

    requireAuth() {
      if (!api.tokens.exists() || localStorage.getItem('pp_admin') !== 'true') {
        window.location.href = 'login.html';
        return false;
      }
      return true;
    },
  };

  // ------------------------------------------------------------- UI utils

  const $ = (selector, scope) => (scope || document).querySelector(selector);
  const $$ = (selector, scope) => Array.from((scope || document).querySelectorAll(selector));

  function showToast(message, type) {
    let toast = document.getElementById('toast');
    if (!toast) {
      toast = document.createElement('div');
      toast.id = 'toast';
      document.body.appendChild(toast);
    }
    toast.className = 'toast toast-' + (type || 'info') + ' show';
    toast.textContent = message;
    clearTimeout(toast._timer);
    toast._timer = setTimeout(() => { toast.className = 'toast'; }, 3000);
  }

  function initSidebar() {
    const sidebar = document.getElementById('sidebar');
    const toggle = document.getElementById('menuToggle');
    const overlay = document.getElementById('sidebarOverlay');
    if (!sidebar || !toggle) return;

    const close = () => {
      sidebar.classList.remove('open');
      if (overlay) overlay.classList.remove('show');
    };
    toggle.addEventListener('click', () => {
      sidebar.classList.toggle('open');
      if (overlay) overlay.classList.toggle('show');
    });
    if (overlay) overlay.addEventListener('click', close);
  }

  function setActiveNav(page) {
    $$('.nav-item').forEach((item) => {
      item.classList.toggle('active', item.dataset.page === page);
    });
  }

  /**
   * Page bootstrap.
   *
   * Renders instantly from cache, then refreshes from the API in the
   * background and emits `pp:data` if anything changed.
   */
  function initAppLayout(pageName) {
    initSidebar();
    setActiveNav(pageName);

    const profile = getProfile();
    const nameEl = document.getElementById('userName');
    if (nameEl) nameEl.textContent = (profile.name || 'there').split(' ')[0];

    refreshProfile().then((fresh) => {
      if (fresh && nameEl) nameEl.textContent = (fresh.name || 'there').split(' ')[0];
    });
  }

  function updatePasswordStrength(input, bar, label) {
    const value = input.value || '';
    let score = 0;
    if (value.length >= 8) score++;
    if (/[A-Z]/.test(value)) score++;
    if (/\d/.test(value)) score++;
    if (/[^A-Za-z0-9]/.test(value)) score++;

    const levels = ['', 'Weak', 'Fair', 'Good', 'Strong'];
    const colors = ['', '#EF4444', '#F59E0B', '#3B82F6', '#10B981'];
    if (bar) {
      bar.style.width = (score * 25) + '%';
      bar.style.background = colors[score];
    }
    if (label) label.textContent = levels[score];
    return score;
  }

  // ---------------------------------------------------------------- export

  window.PP = {
    // state
    getProfile,
    saveProfile,
    refreshProfile,
    isLoggedIn,
    setLoggedIn,
    getLastMatch,
    saveLastMatch,
    getSelectedTemplate,
    setSelectedTemplate,

    // auth
    login,
    register,
    requireAuth,
    logout,

    // core features (all server-backed)
    analyzeJob,
    loadAnalysis,
    generateResume,
    generatePortfolio,
    publishPortfolio,
    listTemplates: () => api.portfolio.templates(),
    recentAnalyses: () => api.opportunities.recent(),
    dashboard: () => api.dashboard.get(),

    // projects
    projects: {
      list: () => api.projects.list(),
      create: (payload) => api.projects.create(payload),
      update: (id, payload) => api.projects.update(id, payload),
      remove: (id) => api.projects.remove(id),
      setInclusion: (id, include) => api.projects.setInclusion(id, include),
    },

    // portfolios — the builder needs read/update/publish, not just generate
    portfolios: {
      list: () => api.portfolio.list(),
      get: (id) => api.portfolio.get(id),
      published: () => api.portfolio.published(),
      update: (id, payload) => api.portfolio.update(id, payload),
      publish: (id) => api.portfolio.publish(id),
      unpublish: (id) => api.portfolio.unpublish(id),
      public: (username) => api.portfolio.public(username),
    },

    // admin (promise-based; the old hardcoded arrays are gone)
    admin,

    // ui
    showToast,
    initAppLayout,
    initSidebar,
    setActiveNav,
    updatePasswordStrength,
    $,
    $$,

    /*
     * DEPRECATED compatibility stubs.
     *
     * The old hardcoded `adminStats` / `mockUsers` / `mockJobLogs` arrays are
     * gone - that data is real now and lives behind `PP.admin.*`.
     *
     * These empty placeholders exist ONLY so an admin page that has not been
     * rewired yet renders an empty table instead of throwing
     * "Cannot read properties of undefined". Remove them once every admin page
     * uses the promise-based loaders.
     */
    adminStats: { totalUsers: 0, portfoliosPublished: 0, jobsAnalyzedToday: 0, avgMatchScore: 0 },
    dailySignups: [],
    skillGapsData: [],
    mockUsers: [],
    mockTemplates: [],
    mockJobLogs: [],
  };
})();
