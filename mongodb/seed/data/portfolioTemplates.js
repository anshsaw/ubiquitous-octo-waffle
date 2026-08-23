'use strict';

/**
 * Seed content for `portfolioTemplates`.
 *
 * `templateKey` is the contract between the database and the React renderer:
 *   MODERN_DEV -> <ModernDevTemplate />
 * Never rename a key that is already referenced by a portfolio.
 *
 * Thumbnails point at relative asset paths; swap them for CDN URLs in
 * production. No binary is ever stored in MongoDB.
 */

const { ID } = require('../ids');

const PORTFOLIO_TEMPLATES = [
  {
    _id: ID.template(1),
    name: 'Modern Developer',
    description: 'Clean, indigo-accented developer portfolio. Hero header, skill chips and a three-column project grid. The safest default for engineering roles.',
    templateKey: 'MODERN_DEV',
    thumbnailUrl: '/assets/templates/modern-dev.png',
    previewUrl: '/assets/templates/modern-dev-full.png',
    availableSections: ['about', 'skills', 'projects', 'education', 'experience', 'certificates', 'contact'],
    defaultSections: ['about', 'skills', 'projects', 'education', 'contact'],
    theme: { primaryColor: '#4F46E5', accentColor: '#10B981', darkMode: false },
    sortOrder: 10,
    active: true,
  },
  {
    _id: ID.template(2),
    name: 'Minimal Mono',
    description: 'Typography-first, single-column layout with generous whitespace. Prints well and reads fast - good for research and internship applications.',
    templateKey: 'MINIMAL_MONO',
    thumbnailUrl: '/assets/templates/minimal-mono.png',
    previewUrl: '/assets/templates/minimal-mono-full.png',
    availableSections: ['about', 'skills', 'projects', 'education', 'experience', 'contact'],
    defaultSections: ['about', 'skills', 'projects', 'education', 'contact'],
    theme: { primaryColor: '#111827', accentColor: '#4F46E5', darkMode: false },
    sortOrder: 20,
    active: true,
  },
  {
    _id: ID.template(3),
    name: 'Creative Grid',
    description: 'Image-led masonry grid that puts project screenshots first. Best for front-end, UI and design-adjacent roles.',
    templateKey: 'CREATIVE_GRID',
    thumbnailUrl: '/assets/templates/creative-grid.png',
    previewUrl: '/assets/templates/creative-grid-full.png',
    availableSections: ['about', 'projects', 'skills', 'certificates', 'contact'],
    defaultSections: ['about', 'projects', 'skills', 'contact'],
    theme: { primaryColor: '#7C3AED', accentColor: '#F59E0B', darkMode: true },
    sortOrder: 30,
    active: true,
  },
  {
    _id: ID.template(4),
    name: 'Timeline Pro',
    description: 'Chronological career timeline with education and experience as the spine. Suited to candidates who already have internships to show.',
    templateKey: 'TIMELINE_PRO',
    thumbnailUrl: '/assets/templates/timeline-pro.png',
    previewUrl: '/assets/templates/timeline-pro-full.png',
    availableSections: ['about', 'experience', 'education', 'projects', 'skills', 'certificates', 'contact'],
    defaultSections: ['about', 'experience', 'education', 'projects', 'skills', 'contact'],
    theme: { primaryColor: '#0F766E', accentColor: '#F97316', darkMode: false },
    sortOrder: 40,
    active: true,
  },
  {
    _id: ID.template(6),
    name: 'Gradient Wave',
    description: 'Vivid full-bleed gradient background with glassy cards. High impact for product and design-adjacent roles.',
    templateKey: 'GRADIENT_WAVE',
    thumbnailUrl: '/assets/templates/gradient-wave.png',
    previewUrl: null,
    availableSections: ['about', 'skills', 'projects', 'education', 'experience', 'certificates', 'contact'],
    defaultSections: ['about', 'skills', 'projects', 'contact'],
    theme: { primaryColor: '#DB2777', accentColor: '#6366F1', darkMode: false },
    sortOrder: 50,
    active: true,
  },
  {
    _id: ID.template(7),
    name: 'Terminal Dev',
    description: 'Monospaced terminal aesthetic on near-black. Unapologetically for backend and systems engineers.',
    templateKey: 'TERMINAL_DEV',
    thumbnailUrl: '/assets/templates/terminal-dev.png',
    previewUrl: null,
    availableSections: ['about', 'skills', 'projects', 'education', 'experience', 'contact'],
    defaultSections: ['about', 'skills', 'projects', 'experience', 'contact'],
    theme: { primaryColor: '#22C55E', accentColor: '#38BDF8', darkMode: true },
    sortOrder: 60,
    active: true,
  },
  {
    _id: ID.template(8),
    name: 'Editorial',
    description: 'Magazine layout on warm paper. Large serif headings and generous measure - reads like a feature article.',
    templateKey: 'EDITORIAL',
    thumbnailUrl: '/assets/templates/editorial.png',
    previewUrl: null,
    availableSections: ['about', 'skills', 'projects', 'education', 'experience', 'certificates', 'contact'],
    defaultSections: ['about', 'projects', 'experience', 'education', 'contact'],
    theme: { primaryColor: '#B45309', accentColor: '#0F766E', darkMode: false },
    sortOrder: 70,
    active: true,
  },
  {
    _id: ID.template(9),
    name: 'Soft Pastel',
    description: 'Gentle tinted background with rounded cards and low contrast. Calm and approachable.',
    templateKey: 'SOFT_PASTEL',
    thumbnailUrl: '/assets/templates/soft-pastel.png',
    previewUrl: null,
    availableSections: ['about', 'skills', 'projects', 'education', 'certificates', 'contact'],
    defaultSections: ['about', 'skills', 'projects', 'education', 'contact'],
    theme: { primaryColor: '#7C3AED', accentColor: '#EC4899', darkMode: false },
    sortOrder: 80,
    active: true,
  },
  {
    /*
     * The escape hatch behind the builder's "Custom" panel.
     *
     * It carries no opinion of its own: the layout is neutral and every colour
     * comes from the portfolio's own theme fields, which the user sets with the
     * pickers. That is why a user can "make their own template" without the
     * platform ever having to accept and execute uploaded CSS.
     */
    _id: ID.template(10),
    name: 'Custom',
    description: 'Neutral base you colour yourself. Set your own background, text and accent colours in the builder.',
    templateKey: 'CUSTOM',
    thumbnailUrl: '/assets/templates/custom.png',
    previewUrl: null,
    availableSections: ['about', 'skills', 'projects', 'education', 'experience', 'certificates', 'contact'],
    defaultSections: ['about', 'skills', 'projects', 'education', 'contact'],
    theme: { primaryColor: '#4F46E5', accentColor: '#10B981', darkMode: false },
    sortOrder: 95,
    active: true,
  },
  {
    _id: ID.template(5),
    name: 'Classic Card (retired)',
    description: 'First-generation layout. Kept INACTIVE on purpose so the admin Templates screen has a realistic active/inactive toggle to demonstrate, and so you can verify that retiring a template never breaks portfolios already using it.',
    templateKey: 'CLASSIC_CARD',
    thumbnailUrl: '/assets/templates/classic-card.png',
    previewUrl: null,
    availableSections: ['about', 'skills', 'projects', 'contact'],
    defaultSections: ['about', 'skills', 'projects'],
    theme: { primaryColor: '#2563EB', accentColor: '#64748B', darkMode: false },
    sortOrder: 90,
    active: false,
  },
];

module.exports = { PORTFOLIO_TEMPLATES };
