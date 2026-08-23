/**
 * PortfolioPilot AI — Shared portfolio renderer
 * =============================================
 *
 * ONE data object, MULTIPLE visual templates, THREE consumers:
 *
 *     builder.html   -> PPRender.renderCompact()   (small live preview)
 *     preview.html   -> PPRender.renderFull()      (temporary full preview)
 *     portfolio.html -> PPRender.renderFull()      (published public site)
 *
 * All three read the same shape produced by PPRender.buildData(), so the live
 * preview, the temporary preview and the published site cannot disagree. That
 * was the whole point: before this file existed, each page built its own markup
 * from a different source, and the three never matched.
 *
 * DATA SOURCE
 * -----------
 * Everything comes from the existing REST API via js/api.js. No second data
 * system is introduced, and nothing here talks to MongoDB directly.
 *
 *     GET /api/profile                 profile, skills, education, experience
 *     GET /api/projects?paged=false    projects
 *     GET /api/portfolio/{id}          template + section toggles + ordering
 *     GET /api/templates               template catalogue
 *
 * SECURITY
 * --------
 * Every interpolated value goes through esc(); every URL goes through
 * safeUrl(), which rejects anything that is not http(s) — so a stored
 * `javascript:` link cannot execute. No raw user HTML is ever injected.
 */
(function () {
  'use strict';

  // ------------------------------------------------------------ sanitising

  /** HTML-escape. Applied to EVERY user-supplied value without exception. */
  function esc(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  /**
   * Returns the URL only when it is http(s). Anything else — most importantly
   * `javascript:` — returns null and the link is simply not rendered.
   */
  function safeUrl(raw) {
    if (!raw) return null;
    try {
      const parsed = new URL(raw, window.location.href);
      return (parsed.protocol === 'http:' || parsed.protocol === 'https:') ? parsed.href : null;
    } catch (err) {
      return null;
    }
  }

  // -------------------------------------------------------------- helpers

  const SECTION_ORDER = ['about', 'skills', 'projects', 'education', 'experience', 'certificates', 'contact'];

  const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  function monthYear(value) {
    if (!value) return null;
    const d = new Date(value);
    return isNaN(d) ? null : MONTHS[d.getUTCMonth()] + ' ' + d.getUTCFullYear();
  }

  function dateRange(start, end) {
    const from = monthYear(start);
    if (!from) return null;
    return from + ' – ' + (monthYear(end) || 'Present');
  }

  function yearRange(start, end) {
    if (!start && !end) return null;
    if (!start) return String(end);
    if (!end) return start + ' – Present';
    return start === end ? String(end) : start + ' – ' + end;
  }

  function initialOf(name) {
    const trimmed = String(name || '').trim();
    return trimmed ? trimmed.charAt(0).toUpperCase() : '?';
  }

  // ------------------------------------------------------------ build data

  /**
   * Normalises the API payloads into the single portfolio object every
   * renderer consumes.
   *
   * @param {Object}   input
   * @param {Object}   input.profile    GET /api/profile
   * @param {Array}    input.projects   GET /api/projects
   * @param {Object}   [input.portfolio] GET /api/portfolio/{id} — template, sections, ordering
   * @param {Object}   [input.template]  the matching entry from GET /api/templates
   * @param {Object}   [input.sectionsOverride] live builder checkbox state
   * @param {string}   [input.templateOverride] live builder template choice
   */
  function buildData(input) {
    const profile = input.profile || {};
    const portfolio = input.portfolio || {};
    const template = input.template || {};

    const templateKey = input.templateOverride || portfolio.templateKey || template.templateKey || 'MODERN_DEV';

    // Builder checkboxes win while the user is editing; otherwise use the
    // saved configuration; otherwise show everything that has content.
    const sections = input.sectionsOverride || portfolio.sections || defaultSections(profile, input.projects);

    const contact = profile.contact || {};

    // Ordering comes from the saved portfolio when present. It is how a
    // job-adapted portfolio puts the relevant work first.
    const orderedSkills = portfolio.orderedSkills || [];
    const orderedProjects = portfolio.orderedProjects || [];

    return {
      templateKey: templateKey,
      templateName: template.name || portfolio.templateKey || templateKey,

      theme: {
        primary: portfolio.primaryColor || template.primaryColor || '#4F46E5',
        accent: portfolio.accentColor || template.accentColor || '#10B981',
        dark: (portfolio.darkMode != null ? portfolio.darkMode : template.darkMode) || false,
        // Custom surface colours, persisted on portfolios.theme. Present only
        // for the CUSTOM template; built-in templates get their surfaces from
        // CSS so their designed palette is never overwritten.
        background: (input.customTheme && input.customTheme.background) || portfolio.backgroundColor || null,
        surface: (input.customTheme && input.customTheme.surface) || portfolio.surfaceColor || null,
        ink: (input.customTheme && input.customTheme.ink) || portfolio.inkColor || null
      },

      sections: sections,
      sectionOrder: (portfolio.sectionOrder && portfolio.sectionOrder.length)
        ? portfolio.sectionOrder
        : SECTION_ORDER,

      owner: {
        fullName: profile.name || profile.fullName || '',
        initial: initialOf(profile.name || profile.fullName),
        headline: portfolio.headlineOverride || profile.professionalTitle || profile.title || '',
        bio: portfolio.summaryOverride || profile.bio || '',
        location: profile.location || '',
        avatarUrl: safeUrl(profile.avatarUrl),
        // Only the address the user chose to publish — never the login email.
        email: contact.publicEmail || '',
        phone: contact.phone || '',
        website: safeUrl(contact.website),
        github: safeUrl(contact.github),
        linkedin: safeUrl(contact.linkedin),
        twitter: safeUrl(contact.twitter)
      },

      skills: orderSkills(profile.skills || [], orderedSkills),
      projects: orderProjects(input.projects || [], orderedProjects),
      education: profile.education || [],
      experience: profile.experience || [],
      certificates: profile.certificates || []
    };
  }

  /** Enable a section only when there is something to put in it. */
  function defaultSections(profile, projects) {
    return {
      about: !!(profile.bio),
      skills: !!(profile.skills && profile.skills.length),
      projects: !!(projects && projects.length),
      education: !!(profile.education && profile.education.length),
      experience: !!(profile.experience && profile.experience.length),
      certificates: !!(profile.certificates && profile.certificates.length),
      contact: true
    };
  }

  /** Applies the portfolio's skill ordering; unlisted skills follow. */
  function orderSkills(skills, order) {
    if (!order || !order.length) return skills;
    const rank = new Map(order.map((k, i) => [k, i]));
    return skills.slice().sort((a, b) => {
      const ra = rank.has(a.normalizedName) ? rank.get(a.normalizedName) : 9999;
      const rb = rank.has(b.normalizedName) ? rank.get(b.normalizedName) : 9999;
      return ra - rb;
    });
  }

  /** Recommended-first ordering, then anything else the user opted in to. */
  function orderProjects(projects, order) {
    const visible = projects.filter(p => p.includeInPortfolio !== false);
    if (!order || !order.length) {
      return visible.slice().sort((a, b) => (b.featured === true) - (a.featured === true));
    }
    const rank = new Map(order.map((id, i) => [id, i]));
    return visible.slice().sort((a, b) => {
      const ra = rank.has(a.id) ? rank.get(a.id) : 9999;
      const rb = rank.has(b.id) ? rank.get(b.id) : 9999;
      return ra - rb;
    });
  }

  function isOn(data, section) {
    return data.sections[section] !== false && data.sections[section] !== undefined
      ? !!data.sections[section]
      : false;
  }

  // ------------------------------------------------------------- sections

  /*
   * Each section is built ONCE here and reused by every template. Templates
   * differ in their hero, their CSS class and their project layout — not by
   * duplicating the whole page four times.
   */

  function socialLinks(owner) {
    const links = [];
    if (owner.github) links.push(['GitHub', owner.github]);
    if (owner.linkedin) links.push(['LinkedIn', owner.linkedin]);
    if (owner.website) links.push(['Website', owner.website]);
    if (owner.twitter) links.push(['Twitter', owner.twitter]);
    if (!links.length) return '';
    return '<div class="pp-social">' + links.map(([label, href]) =>
      `<a href="${esc(href)}" target="_blank" rel="noopener noreferrer" class="btn btn-sm btn-secondary">${esc(label)}</a>`
    ).join('') + '</div>';
  }

  function heroFor(data) {
    const o = data.owner;
    const avatar = o.avatarUrl
      ? `<img class="pp-avatar" src="${esc(o.avatarUrl)}" alt="${esc(o.fullName)}" />`
      : `<div class="pp-avatar pp-avatar-initial">${esc(o.initial)}</div>`;

    // MINIMAL_MONO drops the avatar entirely: it is a typography-first layout.
    const showAvatar = data.templateKey !== 'MINIMAL_MONO' && data.templateKey !== 'CLASSIC_CARD';

    return `
      <header class="pp-hero" id="top">
        ${showAvatar ? avatar : ''}
        <h1 class="pp-name">${esc(o.fullName || 'Your Name')}</h1>
        ${o.headline ? `<p class="pp-headline">${esc(o.headline)}</p>` : ''}
        ${o.location ? `<p class="pp-location">${esc(o.location)}</p>` : ''}
        ${socialLinks(o)}
      </header>`;
  }

  function sectionAbout(data) {
    if (!data.owner.bio) return '';
    return `
      <section class="pp-section" id="about">
        <h2 class="pp-section-title">About</h2>
        <p class="pp-bio">${esc(data.owner.bio)}</p>
      </section>`;
  }

  function sectionSkills(data) {
    if (!data.skills.length) {
      return emptySection('skills', 'Skills', 'No skills added yet.');
    }
    return `
      <section class="pp-section" id="skills">
        <h2 class="pp-section-title">Skills</h2>
        <div class="pp-skills">
          ${data.skills.map(s => `<span class="pp-skill">${esc(s.name)}</span>`).join('')}
        </div>
      </section>`;
  }

  function sectionProjects(data) {
    if (!data.projects.length) {
      return emptySection('projects', 'Projects', 'No projects added yet.');
    }
    return `
      <section class="pp-section" id="projects">
        <h2 class="pp-section-title">Projects</h2>
        <div class="pp-projects">
          ${data.projects.map(projectCard).join('')}
        </div>
      </section>`;
  }

  function projectCard(p) {
    const repo = safeUrl(p.repositoryUrl || p.repo);
    const live = safeUrl(p.liveDemoUrl || p.live);
    const image = safeUrl(p.imageUrl);
    const tech = p.techStack || p.tech || [];
    const period = dateRange(p.startDate, p.endDate);

    return `
      <article class="pp-project">
        ${image ? `<img class="pp-project-image" src="${esc(image)}" alt="${esc(p.title)}" loading="lazy" />` : ''}
        <div class="pp-project-body">
          <h3 class="pp-project-title">${esc(p.title)}</h3>
          ${period ? `<p class="pp-project-period">${esc(period)}</p>` : ''}
          ${p.description ? `<p class="pp-project-desc">${esc(p.description)}</p>` : ''}
          ${tech.length ? `<div class="pp-tech">${tech.map(t => `<span class="pp-tech-tag">${esc(t)}</span>`).join('')}</div>` : ''}
          ${(repo || live) ? `
            <div class="pp-project-links">
              ${live ? `<a href="${esc(live)}" target="_blank" rel="noopener noreferrer" class="btn btn-sm btn-primary">Live Demo</a>` : ''}
              ${repo ? `<a href="${esc(repo)}" target="_blank" rel="noopener noreferrer" class="btn btn-sm btn-secondary">GitHub</a>` : ''}
            </div>` : ''}
        </div>
      </article>`;
  }

  function sectionEducation(data) {
    if (!data.education.length) {
      return emptySection('education', 'Education', 'No education added yet.');
    }
    return `
      <section class="pp-section" id="education">
        <h2 class="pp-section-title">Education</h2>
        <div class="pp-timeline">
          ${data.education.map(e => {
            const years = yearRange(e.startYear, e.endYear) || e.year;
            return `
            <div class="pp-entry">
              <h3 class="pp-entry-title">${esc(e.degree)}</h3>
              <p class="pp-entry-meta">${esc(e.institution || e.school || '')}${years ? ' · ' + esc(years) : ''}${e.grade ? ' · ' + esc(e.grade) : ''}</p>
              ${e.fieldOfStudy ? `<p class="pp-entry-desc">${esc(e.fieldOfStudy)}</p>` : ''}
            </div>`;
          }).join('')}
        </div>
      </section>`;
  }

  function sectionExperience(data) {
    if (!data.experience.length) {
      return emptySection('experience', 'Experience', 'No experience added yet.');
    }
    return `
      <section class="pp-section" id="experience">
        <h2 class="pp-section-title">Experience</h2>
        <div class="pp-timeline">
          ${data.experience.map(x => {
            const period = x.period || dateRange(x.startDate, x.endDate);
            const tech = x.technologies || [];
            return `
            <div class="pp-entry">
              <h3 class="pp-entry-title">${esc(x.role)}</h3>
              <p class="pp-entry-meta">${esc(x.company)}${period ? ' · ' + esc(period) : ''}${x.location ? ' · ' + esc(x.location) : ''}</p>
              ${x.description ? `<p class="pp-entry-desc">${esc(x.description)}</p>` : ''}
              ${(x.responsibilities && x.responsibilities.length) ? `<ul class="pp-bullets">${x.responsibilities.map(r => `<li>${esc(r)}</li>`).join('')}</ul>` : ''}
              ${tech.length ? `<div class="pp-tech">${tech.map(t => `<span class="pp-tech-tag">${esc(t)}</span>`).join('')}</div>` : ''}
            </div>`;
          }).join('')}
        </div>
      </section>`;
  }

  function sectionCertificates(data) {
    if (!data.certificates.length) {
      return emptySection('certificates', 'Certificates', 'No certificates added yet.');
    }
    return `
      <section class="pp-section" id="certificates">
        <h2 class="pp-section-title">Certificates</h2>
        <div class="pp-timeline">
          ${data.certificates.map(c => {
            const url = safeUrl(c.credentialUrl);
            const issued = monthYear(c.issueDate);
            return `
            <div class="pp-entry">
              <h3 class="pp-entry-title">${esc(c.name)}</h3>
              <p class="pp-entry-meta">${esc(c.issuingOrganization || '')}${issued ? ' · ' + esc(issued) : ''}</p>
              ${url ? `<a href="${esc(url)}" target="_blank" rel="noopener noreferrer" class="pp-link">View credential</a>` : ''}
            </div>`;
          }).join('')}
        </div>
      </section>`;
  }

  function sectionContact(data) {
    const o = data.owner;
    const rows = [];
    if (o.email) rows.push(`<a class="pp-contact-item" href="mailto:${esc(o.email)}">${esc(o.email)}</a>`);
    if (o.phone) rows.push(`<a class="pp-contact-item" href="tel:${esc(o.phone.replace(/\s+/g, ''))}">${esc(o.phone)}</a>`);
    if (o.github) rows.push(`<a class="pp-contact-item" href="${esc(o.github)}" target="_blank" rel="noopener noreferrer">GitHub</a>`);
    if (o.linkedin) rows.push(`<a class="pp-contact-item" href="${esc(o.linkedin)}" target="_blank" rel="noopener noreferrer">LinkedIn</a>`);
    if (o.website) rows.push(`<a class="pp-contact-item" href="${esc(o.website)}" target="_blank" rel="noopener noreferrer">Website</a>`);

    return `
      <section class="pp-section" id="contact">
        <h2 class="pp-section-title">Contact</h2>
        ${rows.length
          ? `<div class="pp-contact">${rows.join('')}</div>`
          : `<p class="pp-empty">No contact details published. Add them under Profile → Contact.</p>`}
      </section>`;
  }

  /** A selected-but-empty section says so rather than rendering a bare heading. */
  function emptySection(id, title, message) {
    return `
      <section class="pp-section" id="${id}">
        <h2 class="pp-section-title">${esc(title)}</h2>
        <p class="pp-empty">${esc(message)}</p>
      </section>`;
  }

  const BUILDERS = {
    about: sectionAbout,
    skills: sectionSkills,
    projects: sectionProjects,
    education: sectionEducation,
    experience: sectionExperience,
    certificates: sectionCertificates,
    contact: sectionContact
  };

  // ------------------------------------------------------------- renderers

  /** In-page navigation, limited to sections that are actually rendered. */
  function navFor(data, enabled) {
    const labels = {
      about: 'About', skills: 'Skills', projects: 'Projects',
      education: 'Education', experience: 'Experience',
      certificates: 'Certificates', contact: 'Contact'
    };
    return `
      <nav class="pp-nav">
        <a class="pp-nav-brand" href="#top">${esc(data.owner.fullName || 'Portfolio')}</a>
        <div class="pp-nav-links">
          ${enabled.map(s => `<a href="#${s}">${esc(labels[s])}</a>`).join('')}
        </div>
      </nav>`;
  }

  /**
   * Full portfolio — used by BOTH the temporary preview and the published site,
   * which is what guarantees "what you preview is what you publish".
   */
  function renderFull(data) {
    const enabled = data.sectionOrder.filter(s => isOn(data, s) && BUILDERS[s]);

    return `
      <div class="pp-portfolio ${templateClass(data.templateKey)}${data.theme.dark ? ' pp-dark' : ''}">
        ${navFor(data, enabled)}
        ${heroFor(data)}
        <main class="pp-main">
          ${enabled.map(s => BUILDERS[s](data)).join('')}
        </main>
        <footer class="pp-footer">
          <p>${esc(data.owner.fullName || 'Portfolio')} · Built with PortfolioPilot AI</p>
        </footer>
      </div>`;
  }

  /**
   * Small representation for the builder's live preview. Same data, same
   * template, same ordering — just truncated.
   */
  function renderCompact(data) {
    const enabled = data.sectionOrder.filter(s => isOn(data, s));
    const o = data.owner;

    return `
      <div class="pp-compact ${templateClass(data.templateKey)}${data.theme.dark ? ' pp-dark' : ''}">
        <div class="pp-compact-hero">
          ${o.avatarUrl
            ? `<img class="pp-compact-avatar" src="${esc(o.avatarUrl)}" alt="" />`
            : `<div class="pp-compact-avatar pp-avatar-initial">${esc(o.initial)}</div>`}
          <div class="pp-compact-name">${esc(o.fullName || 'Your Name')}</div>
          <div class="pp-compact-headline">${esc(o.headline || 'Your headline')}</div>
        </div>

        ${enabled.includes('about') && o.bio
          ? `<p class="pp-compact-bio">${esc(o.bio.slice(0, 130))}${o.bio.length > 130 ? '…' : ''}</p>` : ''}

        ${enabled.includes('skills') ? `
          <div class="pp-compact-block">
            <div class="pp-compact-label">Skills</div>
            <div class="pp-compact-tags">
              ${data.skills.slice(0, 6).map(s => `<span class="pp-skill">${esc(s.name)}</span>`).join('')
                || '<span class="pp-empty-inline">None yet</span>'}
            </div>
          </div>` : ''}

        ${enabled.includes('projects') ? `
          <div class="pp-compact-block">
            <div class="pp-compact-label">Projects</div>
            ${data.projects.slice(0, 3).map(p => `<div class="pp-compact-item">${esc(p.title)}</div>`).join('')
              || '<span class="pp-empty-inline">None yet</span>'}
          </div>` : ''}

        ${enabled.includes('education') ? `
          <div class="pp-compact-block">
            <div class="pp-compact-label">Education</div>
            ${data.education.slice(0, 2).map(e => `<div class="pp-compact-item">${esc(e.degree)}</div>`).join('')
              || '<span class="pp-empty-inline">None yet</span>'}
          </div>` : ''}

        ${enabled.includes('experience') ? `
          <div class="pp-compact-block">
            <div class="pp-compact-label">Experience</div>
            ${data.experience.slice(0, 2).map(x => `<div class="pp-compact-item">${esc(x.role)} · ${esc(x.company)}</div>`).join('')
              || '<span class="pp-empty-inline">None yet</span>'}
          </div>` : ''}

        <div class="pp-compact-foot">${esc(data.templateName)} · ${enabled.length} section${enabled.length === 1 ? '' : 's'}</div>
      </div>`;
  }

  /** MODERN_DEV -> pp-tpl-modern-dev */
  function templateClass(key) {
    return 'pp-tpl-' + String(key || 'MODERN_DEV').toLowerCase().replace(/_/g, '-');
  }

  /** Accepts only #rgb / #rrggbb, so a stored value cannot inject CSS. */
  function safeColor(value, fallback) {
    return (typeof value === 'string' && /^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(value.trim()))
      ? value.trim()
      : fallback;
  }

  /**
   * Pushes the template's palette onto a scope element as CSS variables.
   *
   * IMPORTANT: this now sets the SURFACE variables too, not just the accents.
   * Previously it set only --pp-primary/--pp-accent while the stylesheet
   * hardcoded `background: #fff`, so every light template rendered on an
   * identical white page and switching template looked like it did nothing.
   *
   * A template's own colours come from CSS (`.pp-tpl-*` blocks). This function
   * overrides them ONLY when the portfolio carries explicit custom colours —
   * which is how the builder's Custom panel works without uploading any CSS.
   */
  function applyTheme(data, element) {
    const target = element || document.documentElement;
    const theme = data.theme || {};

    target.style.setProperty('--pp-primary', safeColor(theme.primary, '#4F46E5'));
    target.style.setProperty('--pp-accent', safeColor(theme.accent, '#10B981'));

    // Custom surfaces: only applied for the CUSTOM template, so a built-in
    // template's designed palette is never silently overwritten.
    const custom = data.templateKey === 'CUSTOM';
    const props = ['--pp-bg', '--pp-surface', '--pp-ink', '--pp-ink-muted', '--pp-border'];

    if (custom) {
      const dark = !!theme.dark;
      const bg = safeColor(theme.background, dark ? '#0B1120' : '#FFFFFF');
      const surface = safeColor(theme.surface, dark ? '#111C34' : '#F8FAFC');
      const ink = safeColor(theme.ink, dark ? '#E2E8F0' : '#334155');

      target.style.setProperty('--pp-bg', bg);
      target.style.setProperty('--pp-surface', surface);
      target.style.setProperty('--pp-ink', ink);
      target.style.setProperty('--pp-ink-muted', dark ? '#94A3B8' : '#64748B');
      target.style.setProperty('--pp-border', dark ? '#1E293B' : '#E2E8F0');
    } else {
      // Clear any leftovers from a previous Custom render, otherwise switching
      // back to a built-in template would keep the custom background.
      props.forEach(p => target.style.removeProperty(p));
    }
  }

  /** Smooth in-page scrolling for the rendered nav. */
  function enableSmoothScroll(scope) {
    (scope || document).querySelectorAll('.pp-nav-links a, .pp-nav-brand').forEach(link => {
      link.addEventListener('click', (e) => {
        const id = link.getAttribute('href');
        if (!id || id.charAt(0) !== '#') return;
        const target = id === '#top' ? document.body : document.querySelector(id);
        if (!target) return;
        e.preventDefault();
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    });
  }

  window.PPRender = {
    buildData,
    renderFull,
    renderCompact,
    applyTheme,
    enableSmoothScroll,
    templateClass,
    esc,
    safeUrl,
    SECTION_ORDER
  };
})();
