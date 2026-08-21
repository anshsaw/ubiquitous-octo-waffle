# PortfolioPilot AI — Complete Website

Pure HTML + CSS + JavaScript. No build step. Open any HTML file in a browser.

## How to run

1. Open the folder `portfoliopilot-ai` on your computer.
2. Double-click **`index.html`** (or open it in a browser).
3. Or serve with a simple server:
   ```bash
   npx serve .
   # or: python -m http.server 8080
   ```

## Pages

### Public
| File | Description |
|------|-------------|
| `index.html` | Landing page |
| `register.html` | Sign up |
| `login.html` | Login (any email/password works) |
| `portfolio.html` | **Published public portfolio** (shareable, no login required) |

### User app (login required)
| File | Description |
|------|-------------|
| `dashboard.html` | Stats + recent analyses + quick actions |
| `profile.html` | Personal / Education / Skills tabs (editable, saved to localStorage) |
| `projects.html` | Project grid + add/edit/delete modal |
| `analyzer.html` | Paste job description → AI analysis |
| `match-analysis.html` | Match % ring, sub-scores, strong skills vs gaps, tailored resume |
| `builder.html` | Template picker + section toggles + live preview |
| `preview.html` | Final portfolio preview before publish |

### Admin (`/admin`)
| File | Description |
|------|-------------|
| `admin/login.html` | Admin login |
| `admin/index.html` | Dashboard with charts (Chart.js) |
| `admin/users.html` | Users table (search, suspend) |
| `admin/job-logs.html` | Job analyses log |
| `admin/templates.html` | Template CRUD |
| `admin/reports.html` | Analytics summary |
| `admin/settings.html` | Platform settings |

## Core features

- **generateMatch()** in `js/app.js` — keyword/heuristic match scoring, skill gaps, recommended projects, tailored summary. Ready to replace with a real AI API.
- **Tailored resume** — generated on Match Analysis page; prioritizes matched skills/projects; printable/PDF via browser print.
- **State** — profile, auth, last match, selected template stored in `localStorage`.
- **Responsive** — sidebar collapses on mobile with hamburger menu.
- **Design** — indigo primary, rounded cards, soft shadows, clean SaaS look.

## Demo flow

1. Open `index.html` → Get Started → Register (or Login).
2. Dashboard → Analyze New Opportunity.
3. Load sample job or paste your own → Analyze.
4. See Match Analysis → Generate Tailored Resume or Adapt Portfolio.
5. Builder → Preview → Publish (opens public portfolio).
6. Admin: open `admin/login.html` (any credentials).

## Tech

- HTML5 + CSS3 (custom design system, no framework)
- Vanilla JS
- Chart.js (CDN) for admin charts
- Google Fonts (Inter)

No npm, no React, no build tools required.
