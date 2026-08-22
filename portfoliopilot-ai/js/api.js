/**
 * PortfolioPilot AI — HTTP client for the Spring Boot backend.
 * ============================================================
 *
 * Loaded BEFORE js/app.js. Exposes `window.PPApi`.
 *
 * Responsibilities:
 *   - one place that knows the API base URL
 *   - bearer-token storage and automatic refresh on 401
 *   - unwrapping the `{ success, message, data }` envelope
 *   - turning API errors into a normal JS Error with a displayable message
 *
 * Nothing here knows about the DOM. Nothing in the pages talks to fetch
 * directly — every call goes through this file, so auth, error handling and the
 * base URL are configured exactly once.
 */
(function () {
  'use strict';

  // ---------------------------------------------------------------- config

  /** Port the Spring Boot backend listens on during local development. */
  const LOCAL_API_PORT = '8080';

  /**
   * Backend origin.
   *
   * Resolution order:
   *   1. window.PP_API_BASE          — set it in a <script> before this file
   *   2. localStorage 'pp_api_base'  — repoint a built page without a rebuild
   *   3. page served BY the backend  — same origin
   *   4. local static dev server     — same hostname, port 8080
   *   5. deployed behind one domain  — same origin
   *
   * Step 4 preserves the HOSTNAME rather than forcing "localhost". A page on
   * 127.0.0.1:5500 must call 127.0.0.1:8080, not localhost:8080 — those are
   * different origins to the browser, and mixing them fails CORS.
   *
   * The previous version returned `origin + '/api'` for any port that was not
   * 5500 or 3000, so serving on 8000 or 5173 made the client POST to the static
   * file server instead of the API. That was the main reason login "did not
   * work" on anything but Live Server.
   */
  function resolveBase() {
    if (window.PP_API_BASE) return window.PP_API_BASE;

    const stored = localStorage.getItem('pp_api_base');
    if (stored) return stored;

    const loc = window.location;

    // Opened straight from disk: there is no usable origin, so an absolute URL
    // is the only option. CORS will still reject it (Origin: null) - serve the
    // pages over http:// instead.
    if (loc.protocol === 'file:') {
      console.warn('[PP] Page opened via file:// - CORS will block every API call. Serve it over http://');
      return 'http://localhost:' + LOCAL_API_PORT + '/api';
    }

    if (loc.port === LOCAL_API_PORT) {
      return loc.origin + '/api';
    }

    if (loc.hostname === 'localhost' || loc.hostname === '127.0.0.1' || loc.hostname === '[::1]') {
      return loc.protocol + '//' + loc.hostname + ':' + LOCAL_API_PORT + '/api';
    }

    return loc.origin + '/api';
  }

  const BASE = resolveBase();

  const KEYS = {
    access: 'pp_access_token',
    refresh: 'pp_refresh_token',
  };

  // ----------------------------------------------------------- token store

  const tokens = {
    access: () => localStorage.getItem(KEYS.access),
    refresh: () => localStorage.getItem(KEYS.refresh),

    set(accessToken, refreshToken) {
      if (accessToken) localStorage.setItem(KEYS.access, accessToken);
      if (refreshToken) localStorage.setItem(KEYS.refresh, refreshToken);
    },

    clear() {
      localStorage.removeItem(KEYS.access);
      localStorage.removeItem(KEYS.refresh);
    },

    exists() {
      return !!localStorage.getItem(KEYS.access);
    },
  };

  // ------------------------------------------------------------- transport

  /** Error carrying the HTTP status and the server's field-level messages. */
  class ApiError extends Error {
    constructor(message, status, errors) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
      this.errors = errors || null;
    }
  }

  let refreshInFlight = null;

  /**
   * Exchanges the refresh token for a new pair.
   *
   * Concurrent 401s share ONE refresh call. Without this guard, five parallel
   * requests failing at once would fire five refreshes; because the backend
   * rotates refresh tokens and treats reuse as theft, that would revoke every
   * session and log the user out — a self-inflicted denial of service.
   */
  function refreshTokens() {
    if (refreshInFlight) return refreshInFlight;

    const refreshToken = tokens.refresh();
    if (!refreshToken) return Promise.resolve(false);

    refreshInFlight = fetch(BASE + '/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
      .then((response) => (response.ok ? response.json() : null))
      .then((body) => {
        if (body && body.success && body.data) {
          tokens.set(body.data.accessToken, body.data.refreshToken);
          return true;
        }
        tokens.clear();
        return false;
      })
      .catch(() => {
        tokens.clear();
        return false;
      })
      .finally(() => {
        refreshInFlight = null;
      });

    return refreshInFlight;
  }

  /**
   * Core request. Retries ONCE after a successful token refresh.
   *
   * @param {string}  path        e.g. '/profile'
   * @param {object}  [options]
   * @param {boolean} [isRetry]   internal guard against an infinite loop
   */
  async function request(path, options = {}, isRetry = false) {
    const headers = Object.assign({}, options.headers);

    if (options.body !== undefined && !(options.body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
    }
    if (!options.anonymous && tokens.access()) {
      headers.Authorization = 'Bearer ' + tokens.access();
    }

    let response;
    try {
      response = await fetch(BASE + path, {
        method: options.method || 'GET',
        headers,
        body: options.body === undefined ? undefined
          : (typeof options.body === 'string' ? options.body : JSON.stringify(options.body)),
      });
    } catch (networkError) {
      throw new ApiError(
        'Cannot reach the server. Is the backend running on ' + BASE + '?', 0, null);
    }

    if (response.status === 401 && !isRetry && !options.anonymous && tokens.refresh()) {
      const refreshed = await refreshTokens();
      if (refreshed) {
        return request(path, options, true);
      }
    }

    if (response.status === 204) {
      return null;
    }

    let body = null;
    try {
      body = await response.json();
    } catch (parseError) {
      body = null;
    }

    if (!response.ok) {
      const message = (body && body.message) || 'Request failed (' + response.status + ')';
      throw new ApiError(message, response.status, body && body.errors);
    }

    // Unwrap the { success, message, data } envelope.
    return body && Object.prototype.hasOwnProperty.call(body, 'data') ? body.data : body;
  }

  const http = {
    get: (path, options) => request(path, Object.assign({ method: 'GET' }, options)),
    post: (path, body, options) => request(path, Object.assign({ method: 'POST', body }, options)),
    put: (path, body, options) => request(path, Object.assign({ method: 'PUT', body }, options)),
    patch: (path, body, options) => request(path, Object.assign({ method: 'PATCH', body }, options)),
    del: (path, options) => request(path, Object.assign({ method: 'DELETE' }, options)),
  };

  // ------------------------------------------------------------ endpoints

  const auth = {
    register: (name, email, password) =>
      http.post('/auth/register', { name, email, password }, { anonymous: true })
        .then((data) => { tokens.set(data.accessToken, data.refreshToken); return data; }),

    login: (email, password) =>
      http.post('/auth/login', { email, password }, { anonymous: true })
        .then((data) => { tokens.set(data.accessToken, data.refreshToken); return data; }),

    me: () => http.get('/auth/me'),

    logout: () => {
      const refreshToken = tokens.refresh();
      const done = refreshToken
        ? http.post('/auth/logout', { refreshToken }).catch(() => null)
        : Promise.resolve();
      return done.finally(() => tokens.clear());
    },
  };

  const profile = {
    get: () => http.get('/profile'),
    patch: (payload) => http.patch('/profile', payload),
    replaceSkills: (skills) => http.put('/profile/skills', skills),
    addSkill: (skill) => http.post('/profile/skills', skill),
    removeSkill: (name) => http.del('/profile/skills/' + encodeURIComponent(name)),
    addEducation: (entry) => http.post('/profile/education', entry),
    updateEducation: (id, entry) => http.put('/profile/education/' + id, entry),
    deleteEducation: (id) => http.del('/profile/education/' + id),
    addExperience: (entry) => http.post('/profile/experience', entry),
    deleteExperience: (id) => http.del('/profile/experience/' + id),
    addCertificate: (entry) => http.post('/profile/certificates', entry),
    deleteCertificate: (id) => http.del('/profile/certificates/' + id),
  };

  const projects = {
    list: () => http.get('/projects?paged=false'),
    get: (id) => http.get('/projects/' + id),
    create: (payload) => http.post('/projects', payload),
    update: (id, payload) => http.put('/projects/' + id, payload),
    setInclusion: (id, include) => http.patch('/projects/' + id + '/portfolio?include=' + include),
    remove: (id) => http.del('/projects/' + id),
  };

  const opportunities = {
    analyze: (jobTitle, company, jobDescription) =>
      http.post('/opportunities/analyze', { jobTitle, company, jobDescription }),
    recent: () => http.get('/opportunities/recent'),
    get: (id) => http.get('/opportunities/' + id),
    list: (page, size) => http.get('/opportunities?page=' + (page || 0) + '&size=' + (size || 20)),
    remove: (id) => http.del('/opportunities/' + id),
  };

  const resumes = {
    generate: (jobAnalysisId, template) =>
      http.post('/resumes/generate', { jobAnalysisId, template: template || 'ATS_CLASSIC' }),
    list: () => http.get('/resumes'),
    get: (id) => http.get('/resumes/' + id),
    forAnalysis: (analysisId) => http.get('/resumes/by-analysis/' + analysisId),
    remove: (id) => http.del('/resumes/' + id),
  };

  const portfolio = {
    list: () => http.get('/portfolio'),
    get: (id) => http.get('/portfolio/' + id),
    published: () => http.get('/portfolio/published'),
    generate: (payload) => http.post('/portfolio/generate', payload || {}),
    update: (id, payload) => http.put('/portfolio/' + id, payload),
    publish: (id) => http.post('/portfolio/' + id + '/publish', {}),
    unpublish: (id) => http.post('/portfolio/' + id + '/unpublish', {}),
    remove: (id) => http.del('/portfolio/' + id),
    templates: () => http.get('/templates', { anonymous: true }),
    public: (username) => http.get('/public/portfolio/' + encodeURIComponent(username), { anonymous: true }),
  };

  const dashboard = {
    get: () => http.get('/dashboard'),
  };

  const admin = {
    stats: () => http.get('/admin/dashboard/stats'),
    signupTrends: (days) => http.get('/admin/dashboard/signup-trends?days=' + (days || 30)),
    skillGaps: (limit) => http.get('/admin/dashboard/skill-gaps?limit=' + (limit || 10)),
    users: (params) => {
      const query = new URLSearchParams(params || {}).toString();
      return http.get('/admin/users' + (query ? '?' + query : ''));
    },
    userDetail: (id) => http.get('/admin/users/' + id),
    suspendUser: (id, reason) =>
      http.patch('/admin/users/' + id + '/suspend' + (reason ? '?reason=' + encodeURIComponent(reason) : ''), {}),
    activateUser: (id) => http.patch('/admin/users/' + id + '/activate', {}),
    deleteUser: (id) => http.del('/admin/users/' + id),
    jobAnalyses: (params) => {
      const query = new URLSearchParams(params || {}).toString();
      return http.get('/admin/job-analyses' + (query ? '?' + query : ''));
    },
    templates: () => http.get('/admin/templates'),
    createTemplate: (payload) => http.post('/admin/templates', payload),
    updateTemplate: (id, payload) => http.put('/admin/templates/' + id, payload),
    setTemplateStatus: (id, active) => http.patch('/admin/templates/' + id + '/status?active=' + active, {}),
    deleteTemplate: (id) => http.del('/admin/templates/' + id),
  };

  window.PPApi = {
    BASE,
    ApiError,
    tokens,
    http,
    auth,
    profile,
    projects,
    opportunities,
    resumes,
    portfolio,
    dashboard,
    admin,
  };
})();
