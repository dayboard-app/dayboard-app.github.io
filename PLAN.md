# Dayboard — Implementation Plan

Companion to [REQUIREMENTS.md](REQUIREMENTS.md). Phases are ordered so every phase ends with something deployable and testable. Sizes: S (hours), M (1-2 days), L (several days).

---

## Phase 0 — Infrastructure and skeleton (M)

**Goal**: an empty Compose HTML app builds locally and deploys to `https://dayboard-app.github.io`.

**Already done (2026-08-24):**

- ~~Firebase project~~ — `Dayboard` / `dayboard-app` on the Spark plan: Email/Password auth on (passwordless off), Firestore `(default)` in `europe-west3`, owner-only rules published, web app registered, `dayboard-app.github.io` authorized. State recorded in [firebase/README.md](firebase/README.md).
- ~~`firebase/firestore.rules`~~ — written and published.
- ~~Icon set~~ — generated into `src/jsMain/resources/` by [tools/generate_icons.py](tools/generate_icons.py).
- ~~GitHub org and repo~~ — org [`dayboard-app`](https://github.com/dayboard-app) (personal-account org, free plan, sole owner `bchmsl`) and empty public repo [`dayboard-app/dayboard-app.github.io`](https://github.com/dayboard-app/dayboard-app.github.io), default branch `main`.

**Remaining:**

1. Commit this working tree and push it to the new repo, then enable Pages (Settings → Pages → Source: GitHub Actions).
2. Gradle skeleton copied from the Dakalebi shape:
   - Root = web app: Kotlin/JS IR, CommonJS module kind, Compose HTML, `binaries.executable()`, webpack output `app.js`.
   - `:shared`: commonMain (pure Kotlin) + `jvm()` target for tests/Kover.
   - Version catalog; Kotlin 2.3.x, Compose 1.11.x, `npm("firebase", "12.x")`.
3. `index.html` shell: Google Fonts (Inter, JetBrains Mono), viewport meta (no pinch zoom), meta description, the generated icons and `og-image.png`, `<title>Dayboard</title>`.
4. Firebase composition root reading the config from [firebase/README.md](firebase/README.md) into a Kotlin object.
5. Deploy workflow (`deploy.yml`): push to `main` → `jsBrowserDistribution` → `.nojekyll` → Pages. Verify the live URL serves the skeleton.

**Acceptance**: green Actions run; dayboard-app.github.io shows a "Dayboard" placeholder with the real favicon; Firestore reachable and locked to owner-only.

## Phase 1 — Design system foundation (M)

**Goal**: the 12-palette token system and base components, verified in both modes.

1. `tokens.css`: all CSS variables from REQUIREMENTS §12.2, selector scheme `:root`/`[data-theme=X]`/`.dark` identical to the original; radius token; themed scrollbars; keyframes (`timer-pulse`, `task-complete`, dialog/panel animations).
2. Theme engine in `:shared` presentation + js `data`: themeId/colorMode state, `prefers-color-scheme` listener, localStorage persistence, DOM application (`data-theme` + `dark` class).
3. Base Compose HTML components: Card shell (header with collapse/expand/drag affordances), Button variants, Dialog (scrim, fade+zoom, close on scrim/X), Switch, Slider, text Input/Textarea, toast-free.
4. Lucide icon set: SVG path composable + the ~40 icons used.

**Acceptance**: a component gallery page renders all base pieces in all 6 themes × light/dark; unit tests for theme fallback rules.

## Phase 2 — Auth (M)

1. Firebase Auth externals (jsMain): `onAuthStateChanged`, sign in/up/out, `sendEmailVerification`.
2. Auth screen per REQUIREMENTS §3 (exact strings, states, validation), hash router with the guard behavior (blank screen while loading).
3. Error-code → message mapping in `:shared` with tests (pin the JS SDK code spellings, as Dakalebi's ErrorMessagesTest does).
4. Email-verification gate (enforced, REQUIREMENTS §3.2).

**Acceptance**: sign up, verify, sign in, sign out round-trip works on the deployed site.

## Phase 3 — Dashboard shell (L)

1. Header (refresh spin, title, email, settings trigger stub).
2. Card board: clock slot + two columns, visibility gating, collapse, expand overlay, empty-column placeholders.
3. **Card drag and drop** (hand-rolled pointer-based DnD; the original's library is React-only): drag ghost/opacity, column highlight, the exact drag-end insertion semantics.
4. `cardLayout` model + `parseCardLayout` (with legacy migration) in `:shared` with full tests; 500 ms debounced persistence into the settings doc.
5. Settings document repository: defaults, optimistic writes, snapshot listener with 300 ms debounce and own-echo filtering.

**Acceptance**: layout arranges, collapses, expands, persists, and syncs across two browsers.

## Phase 4 — Clock and weather (M)

1. Clock per §5.1 (24h, seconds toggle, en-US date, 1 s tick).
2. Weather chain per §5.2: manual city geocode → ipapi.co → browser geolocation; open-meteo fetch; WMO icon map; 10 min refresh; silent failures.
3. WMO mapping + city resolution logic in `:shared` with tests (HTTP behind an interface).

**Acceptance**: weather shows for auto and manual city; hides silently when everything fails.

## Phase 5 — Pomodoro timer (L)

1. Timer state machine in `:shared` (pure, clock injected): tick, completion/skip, auto-start rules, session counting, duration-change reset. Exhaustive tests, including restore math and the expired-while-away case.
2. Firestore persistence: explicit-action saves, `lastTickAt`, restore with elapsed subtraction, snapshot sync with the 3 s anti-jump rule.
3. UI: tabs, SVG progress ring (1 s linear transition), dots, controls, loading skeleton, expanded variant.
4. Web Audio beep (440/660 Hz) behind a `:shared` interface.

**Acceptance**: full parity checklist from source-analysis 02 §7 passes manually; state survives refresh and syncs across devices.

## Phase 6 — Tasks (L)

1. Task + tag domain in `:shared`: position-pool reorder, compact subtask renumbering, completion cascade, filter semantics, duplicate-tag-name rule. Tests first-class here.
2. Firestore repositories (tasks with `tagIds` arrays; client-side cascade deletes).
3. List UI per §7.2 (add form, filter chips, rows, inline expansion, completed group).
4. Row/subtask/dialog DnD reuse of the Phase 3 DnD engine.
5. Edit and View dialogs per §7.3/§7.4.

**Acceptance**: every behavior in source-analysis 03 §8 checklist reproduced.

## Phase 7 — Notes and formatting (M/L)

1. Formatting engine in `:shared`: marker wrap/unwrap toggle + render tokenizer (URL/bold/italic/underline/code with recursion rules). Heavy test coverage; this is pure logic.
2. FormattingToolbar component; LinkifiedText renderer as a Compose HTML composable.
3. Notes list, edit dialog, view dialog per §7.5; retrofit the renderer and toolbar into the task dialogs.

**Acceptance**: markers round-trip; links clickable without opening dialogs; visible-subset reorder quirk preserved.

## Phase 8 — Settings panel and theming UI (M)

1. Panel per §8: all 10 sections, exact order, conditional rendering rules, custom switch/slider, tag management (edit/delete with confirmations).
2. Appearance section wired to the Phase 1 theme engine; DB+localStorage dual persistence.

**Acceptance**: every setting round-trips and cross-syncs; tags edited here update task/note pills via `onTagsChanged`-equivalent refresh.

## Phase 9 — Notifications (S for phase 1 scope)

1. Permission flow + settings section states; local notification (SW `showNotification`) with exact titles/bodies, tag `timer-notification`, renotify, vibrate, click-to-focus.
2. (Later, behind decision §15.2) FCM: messaging SW, token registry, callable `sendPush` Cloud Function, token pruning.

**Acceptance**: timer end raises the notification with the correct copy; clicking focuses the app.

## Phase 10 — Hardening and polish (M)

1. Coverage pass on `:shared` (Kover via the JVM target; 100/100/95 targets per file), ktlint clean.
2. Cross-browser + mobile sweep; dark/light sweep of every screen against the original side by side.
3. NotFound page; meta/OG tags; real favicon + apple-touch-icon.
4. README, ARCHITECTURE.md (Dakalebi-style), and rules docs in the repo.

---

## Risks

| Risk | Mitigation |
|---|---|
| Hand-rolled drag & drop is the biggest single effort (cards, tasks, subtasks, notes) | Build one pointer-based DnD engine in Phase 3 and reuse it everywhere; accept simplified ghost visuals first, polish later |
| Firebase npm externals typing friction (dynamic interop) | Copy Dakalebi's externals patterns; keep externals thin, map to typed Kotlin models immediately |
| Kover cannot measure Kotlin/JS | `jvm()` target on `:shared`; keep all logic in commonMain so JVM tests cover it |
| GitHub Pages + hash routing edge cases (auth email links land on `/`) | Verification links use the origin; the guard redirects correctly from any entry URL |
| FCM/Functions need the Blaze plan | Phase-gated; local notifications first |
| Compose HTML has no ready component kit | Phase 1 builds the small base kit before any feature work |

## Suggested first session

Phase 0 end to end: org, repo, Firebase project, Gradle skeleton, first deploy. Everything after that is incremental.
