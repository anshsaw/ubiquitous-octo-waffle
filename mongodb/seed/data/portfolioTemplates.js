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
