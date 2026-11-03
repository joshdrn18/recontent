# ReContent — Creative Content Studio

An editorial-style content repurposing studio prototype.

## Preview

All prototype work is on the `design-preview` branch. The `main` branch is intentionally untouched apart from the repository starter README.

## Run locally

Open `index.html` in a browser. This is a static site with no build step or package installation.

## Cloudflare Pages settings (when ready)

- Production branch: `design-preview` for a temporary review deployment, or merge the approved work into `main` later.
- Framework preset: None
- Build command: leave blank
- Build output directory: `/` (repository root)

## Prototype scope

- Editorial dashboard, content studio, and project library
- Selectable output formats, tone, and language
- Sample transformations for X threads, Instagram carousels, LinkedIn posts, Reels/TikTok scripts, newsletters, and captions
- Copy and TXT export
- Browser-local library using `localStorage`
- Responsive desktop and mobile layout

**Not connected yet:** real AI generation, URL fetching/import, user accounts, cloud database sync, and billing. The current transformation is a front-end demo so the product direction and workflow can be reviewed before backend work begins.

## Expanded workspace review tools

The preview now includes a structured content brief (audience, goal, CTA, key points, and extra instructions), browser-local brief autosave, editable generated output cards, per-output review markers, reset-to-original editing, and a source-length quality hint. These are workflow/review improvements only; they do not claim to provide AI generation. The app remains a static browser prototype and does not yet provide authentication, server-side storage, URL extraction, or an AI provider connection.
