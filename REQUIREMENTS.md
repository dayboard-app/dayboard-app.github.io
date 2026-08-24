# Dayboard — Requirements

Dayboard is a 1:1 functional and visual clone of **Focusly** (`github.com/bchmsl/focusly`), rebuilt with **Kotlin Multiplatform + Firebase** and deployed as a static web app to **GitHub Pages** at `https://dayboard-app.github.io`.

Focusly is a distraction-free productivity dashboard: a Pomodoro timer, a clock with weather, a task list with tags and subtasks, and notes with inline formatting. Everything syncs in realtime across devices behind email/password auth. The original is React + Vite + Tailwind + shadcn/ui + Supabase.

This document is the build spec. The exhaustive per-file analysis of the original source lives in [docs/source-analysis/](docs/source-analysis/); when a pixel-level or class-level detail is not repeated here, that appendix is the reference. The implementation plan is in [PLAN.md](PLAN.md).

---

## 1. Goals and parity rules

1. **Functional parity**: every user-visible behavior of Focusly must work the same in Dayboard, including defaults, timings, edge cases, and exact user-facing strings (listed per feature below). UI strings are verbatim, including their punctuation.
2. **Visual parity**: same layout, same design tokens (12 color palettes), same fonts, same shape language, same animations. Not necessarily the same DOM, but the same rendered result.
3. **Rebranding**: every occurrence of "Focusly" becomes "Dayboard" (title, header, auth page, notification sender, meta tags). New favicon / app icon set is needed.
4. **Deliberate deviations** are allowed only where the platform forces them; each one is listed in §11 and must be approved before implementation.

---

## 2. Platform, stack, and architecture

Dayboard mirrors the proven **Dakalebi** setup (`dakalebi/dakalebi.github.io`): a static Kotlin/JS bundle on GitHub Pages talking directly to Firebase from the browser. No server of our own.

### 2.1 Stack

| Layer | Choice | Reason |
|---|---|---|
| Language | Kotlin Multiplatform, Kotlin/JS (IR), `JsModuleKind.MODULE_COMMONJS` | Firebase npm SDK is module-only; same as Dakalebi |
| UI | **Compose HTML** (`org.jetbrains.compose.html:html-core`) | Renders real DOM. 1:1 CSS visuals, native text selection, native inputs, links, and scrollbars. A canvas-based Compose target could not match the original's DOM-level behavior |
| Backend | Firebase: Auth (email/password), Firestore, FCM (phase 2) | Replaces Supabase Auth / Postgres / Realtime / Edge Functions |
| Firebase binding | `npm("firebase", "12.x")` externals in `jsMain` (`@JsModule`) | Same approach as Dakalebi; no extra wrapper dependency |
| Serialization | kotlinx-serialization | Settings JSON, local cache |
| Build | Gradle, `jsBrowserDistribution` | Pages artifact from `build/dist/js/productionExecutable` |
| Tests | `:shared` commonTest; JVM target for coverage (see §9) | Fast, browser-free tests |

### 2.2 Module layout

```
dayboard-app.github.io/            (root project = the web app, like Dakalebi)
├── shared/                    :shared KMP module
│   └── src/commonMain/kotlin/io/github/dayboard/
│       ├── core/              logging, formatting, time (expect/actual kept minimal)
│       ├── domain/            models, repository interfaces, use cases, pure logic
│       ├── presentation/      screen state holders, routes, error->text mapping
│       └── i18n/              string catalog (English only for now)
├── src/jsMain/kotlin/io/github/dayboard/
│   ├── ui/                    Compose HTML screens and components
│   ├── data/                  Firestore/Auth/FCM implementations, localStorage
│   ├── di/                    composition root
│   └── Main.kt
├── src/jsMain/resources/      index.html, css, icons, sw
├── firebase/firestore.rules   versioned security rules
└── .github/workflows/deploy.yml
```

Dependency rule (enforced by the module boundary, same as Dakalebi): `domain` imports nothing but Kotlin; `data` implements `domain` interfaces; `ui` only reads `presentation` state. All timer, task, note, tag, and layout logic lives in `:shared` so it is unit-testable without a browser.

Package: `io.github.dayboard` (rooted at the GitHub Pages namespace, so no owned domain is needed).

### 2.3 Routing

The original uses history routing (`/`, `/auth`, `*` fallback). GitHub Pages has no server rewrites, so Dayboard uses **hash routing** like Dakalebi:

- `#/` → dashboard (auth-guarded)
- `#/auth` → auth page
- anything else → NotFound view (title "404", text "Oops! Page not found", link "Return to Home" to `#/`)

Guard behavior (exact): while auth state is resolving, render a full-height blank `background`-colored screen (no spinner). Signed out → redirect to `#/auth` (replace). Signed in on `#/auth` → redirect to `#/` (replace).

### 2.4 Hosting and CI

- GitHub org **`dayboard-app`**, repo **`dayboard-app.github.io`**, GitHub Pages via Actions. The plain `dayboard` org name was already taken by an unrelated (and dormant) company, so `dayboard-app` was chosen to match the Firebase project ID; the product is still named "Dayboard" everywhere in the UI.
- Deploy workflow (copy of Dakalebi's): on push to `main` → JDK 21 → `./gradlew jsBrowserDistribution` → `touch .nojekyll` → `upload-pages-artifact` from `build/dist/js/productionExecutable` → `deploy-pages`. Concurrency group `pages`, newest push wins.
- Firebase web config (apiKey, authDomain, projectId, ...) is a publishable client value and lives in the source; security comes from Firestore rules. The live project details are in [firebase/README.md](firebase/README.md), and `dayboard-app.github.io` is already on the Firebase Auth authorized-domain list (without it, sign-in from the deployed site fails).

---

## 3. Functional requirements — Authentication

Provider: **email + password only**. No OAuth, no magic links, no anonymous mode.

### 3.1 Auth page (`#/auth`)

One page toggling between Login (default) and Sign-up modes.

- Layout: full-screen centered column, max width 24rem. Logo row: `Timer` icon (lucide, 24px, primary color) + brand text **"Dayboard"**. Card: `rounded-2xl`, border, card background, 24px padding, small shadow.
- Login mode: heading **"Welcome back"**, subtext **"Sign in to sync your tasks & timer"**.
- Sign-up mode: heading **"Create account"**, subtext **"Sign up to save your progress"**.
- Inputs: Email (`type=email`, required, `Mail` icon inside on the left) and Password (`type=password`, required, minLength 6, `Lock` icon). Placeholders: **"Email"**, **"Password"**.
- Submit button: primary, label **"Sign in"** / **"Sign up"** + `ArrowRight` icon; while submitting, disabled at 50% opacity showing a spinning `Loader2` icon instead of the label.
- Mode toggle line: **"Don't have an account?"** / **"Already have an account?"** + link-styled **"Sign up"** / **"Sign in"**. Toggling clears error and message.
- Error text: small, destructive color, under the form. Success text: small, secondary color.

### 3.2 Flows

- **Sign in**: `signInWithEmailAndPassword`. On failure show a short human message mapped from the Firebase error code (e.g. `auth/invalid-credential` → "Invalid login credentials") in the error slot. On success the auth listener fires and the page redirects to `#/`.
- **Sign up**: `createUserWithEmailAndPassword` + `sendEmailVerification` (continue URL = site origin). On success show **"Check your email for a confirmation link!"** and stay on the page.
- **Email verification gate** (parity with Supabase's confirm-before-login default, **decided: enforced**): after sign-in, if `user.emailVerified == false`, sign the user out and show an error telling them to confirm their email first.
- **Session**: Firebase web default `browserLocalPersistence` + auto token refresh. One `onAuthStateChanged` listener drives `{user, loading}` for the whole app.
- **Sign out**: from the settings panel; signs out and navigates to `#/auth`.

---

## 4. Functional requirements — App shell and dashboard

### 4.1 Header

Border-bottom bar, content constrained to max width 64rem (1024px) with 24px side padding:

- **Refresh button** (left): icon-only `Timer` icon in primary color, tooltip "Refresh". Clicking spins the icon while it reloads settings, theme, tasks, and notes in parallel.
- **App title**: "Dayboard", `text-lg font-semibold`.
- **Right side**: the signed-in user's email (muted, hidden below 640px), then the Settings trigger button (§8).

### 4.2 Cards

Four cards: `clock`, `timer`, `tasks`, `notes` with display titles **Clock / Pomodoro / Tasks / Notes**. Shared card shell:

- `rounded-2xl`, 1px border, card background, small shadow.
- Header row (24px h-padding, 12px v-padding): drag handle (`GripVertical`, only when draggable and not expanded), collapse toggle showing `ChevronDown` (collapsed) / `ChevronUp` (open) + title, and a Maximize/Minimize button (`Maximize2` / `Minimize2`, aria-labels "Maximize"/"Minimize").
- Content area (24px padding, none top when collapsed = hidden entirely). Timer card content is center-aligned.

### 4.3 Layout

- Main column: max width 64rem, centered, 24px side padding, 40px top/bottom, 24px gaps.
- **Clock card** always renders first, above the columns, full width. It is always visible (no visibility setting), never draggable, but collapsible and expandable.
- Below it, a **two-column drag-and-drop board**: two droppable columns (`col-left`, `col-right`), stacked vertically below 768px and side-by-side (flex-1 each) at 768px and up.
- Default placement: left = `[timer]`, right = `[tasks, notes]`.
- Card visibility: timer/tasks/notes gated by settings `showPomodoro` / `showTasks` / `showNotes`; clock always visible.
- Empty column placeholder: dashed 2px border box, centered muted text **"Drop cards here"**.
- Drag visuals: dragged card at 90% opacity with shadow and primary ring; the column being hovered gets a faint primary tint and inset ring.
- **Drag-end semantics** (exact, from the original):
  - Same-column reorder maps visible (filtered) indices back onto the full column array: remove the dragged id, then insert after the target when moving down and before it when moving up.
  - Cross-column move inserts before the visible card currently at the destination index, or appends when dropped past the end.
- **Expand (fullscreen)**: an expanded card renders in a fixed inset-16px overlay (z-40) above a backdrop (`background` at 60% + small blur, z-30, click closes). Clock/timer center their content; tasks/notes get a scrollable body. The rest of the dashboard is hidden while a card is expanded. Cards render larger variants when expanded.
- **Collapse**: collapsed ids stored in the layout; content hidden, header remains.

### 4.4 Layout persistence

`cardLayout` object: `{ left: [ids], right: [ids], widths: {id: "half"|"full"}, collapsed: [ids] }` (`widths` is carried but unused for rendering). Stored inside the settings document. Local state updates instantly; saving is **debounced 500 ms**. A `parseCardLayout` validator must replicate the original's rules: defaults per field when malformed, plus the legacy migration from `{order: [...]}` shape (filter out "clock", distribute alternately left/right).

### 4.5 Realtime refresh wiring

Firestore snapshot listeners replace Supabase postgres_changes with the same observable behavior:

- Tasks/tags changes → tasks list refresh. Tags changes also refresh notes. Notes changes → notes refresh.
- Settings/theme remote changes apply **debounced 300 ms**.
- **Own-echo suppression**: remote-change handling must ignore the client's own writes (use snapshot metadata `hasPendingWrites`, replacing the original's 2-second suppression windows).
- Refreshes are suppressed while an edit dialog is open, and during a reorder write plus **800 ms** after it settles.

---

## 5. Functional requirements — Clock and weather

### 5.1 Clock

- Ticks every 1 s from device time (no timezone setting).
- **Always 24-hour**, `HH:MM`, zero-padded; when `showSeconds` is on, append `:SS` in a smaller muted span. JetBrains Mono, bold.
- Date below: en-US long format "Weekday, Month D" (e.g. "Monday, August 24"), no year, muted.
- Sizes (normal → expanded): time `text-5xl sm:text-6xl` → up to `md:text-[8rem]`; seconds and date scale up accordingly (exact classes in the appendix).

### 5.2 Weather (on the clock card)

- Gated by `showWeather` (default true). Turning it off clears state.
- **Location resolution order**:
  1. Manual city (`weatherCity` setting): forward geocode via `https://geocoding-api.open-meteo.com/v1/search?name=<city>&count=1`; no result → hide weather silently.
  2. IP-based lookup: `https://ipapi.co/json/` called directly from the browser (CORS-enabled; replaces the original's `ip-location` edge function, see §11).
  3. Browser geolocation (5 s timeout); reverse-geocode the city name via the same open-meteo geocoding API; fallback label **"Your location"**.
- **Weather fetch**: `https://api.open-meteo.com/v1/forecast?latitude=..&longitude=..&current_weather=true`; show rounded `temperature` as `"{temp}°C"` and map `weathercode` to an icon.
- WMO code → icon map: 0,1 Sun; 2 CloudSun; 3,45,48 Cloud; 51,53,55,56,57 CloudDrizzle; 61,63,65,66,67,80,81,82 CloudRain; 71,73,75,77,85,86 CloudSnow; 95,96,99 CloudLightning; anything else CloudSun.
- Refresh every **10 minutes** plus immediately on mount/setting change. Every failure is silent.
- UI: rounded pill on muted background with weather icon (primary color), `Thermometer` icon + temperature, `MapPin` icon + city name. Loading state: `Loader2` spinner + **"Loading weather..."**.

---

## 6. Functional requirements — Pomodoro timer

### 6.1 Modes and defaults

Modes: `focus`, `shortBreak`, `longBreak` with labels **Focus / Short Break / Long Break**. Durations come from settings (minutes), converted to seconds:

| Setting | Default | Min | Max |
|---|---|---|---|
| focusDuration | 25 | 1 | 90 |
| shortBreakDuration | 5 | 1 | 30 |
| longBreakDuration | 15 | 1 | 60 |
| longBreakInterval | 4 | 2 | 8 |

### 6.2 State machine (exact)

- Tick: 1 s interval, decrement `timeLeft`; at `<= 1` stop, set 0, then complete (after a 50 ms defer).
- **Completion (natural or Skip — identical semantics)**:
  1. Play the sound (§6.5).
  2. Fire the timer-end notification callback with the ended mode's name: "Focus session" / "Short break" / "Long break".
  3. Focus ended: increment `completedSessions`; if it reaches `longBreakInterval`, reset counter to 0 and switch to long break; else switch to short break. Auto-start only if `autoStartBreaks`.
  4. Break ended: switch to focus. Auto-start only if `autoStartFocus`.
- **Skip counts a focus session as completed** (increments the dots) and fires sound + notification.
- Mode tabs: clicking switches to that mode at full duration, stopped, session count kept. No confirmation.
- Controls: Play/Pause toggle; Reset (full duration of current mode, stopped, sessions kept); Skip.
- Changing any duration setting resets and pauses the current timer to the new full duration. Changing `longBreakInterval` only changes the number of dots.
- Session dots: `longBreakInterval` dots; filled (primary) below `completedSessions`, otherwise accent.

### 6.3 Persistence and restore (Firestore doc `users/{uid}/state/timer`)

- Fields: `mode`, `timeLeft` (seconds), `isRunning`, `completedSessions`, `lastTickAt` (timestamp or null), `updatedAt`.
- **Writes only on explicit actions** (play/pause, reset, tab switch, completion/skip, duration-change reset). No per-tick writes. `lastTickAt` = now when running, null when paused. Coalesce rapid saves (original used a 50 ms debounce).
- **Restore on load** (after settings load): `tl = timeLeft`; if `isRunning && lastTickAt != null`, subtract wall-clock elapsed seconds, floor at 0. Run only if still `> 0`. If the timer expired while away: show 00:00, paused, same mode, **no completion side effects** (no sound, no notification, no advance).
- No row yet → focus mode at the settings' focus duration.

### 6.4 Cross-device sync

Snapshot listener on the timer doc. Ignore own echoes (`hasPendingWrites`). Apply remote state with the same elapsed-subtraction; if both devices run the same mode and the difference is **≤ 3 seconds**, ignore the update (anti-jump). Otherwise apply wholesale.

### 6.5 Sound

Web Audio, no files. If `soundEnabled`: gain = `soundVolume / 100`; two sine beeps, **440 Hz** then **660 Hz**, each 0.2 s, second starting 0.25 s after the first. Failures silent.

### 6.6 Visuals

- Vertical stack, centered: mode tabs → ring → dots → controls (gaps 32px, 48px expanded).
- Mode tabs: pill group on muted background; active tab is primary-colored during focus and secondary-colored during breaks; inactive tabs muted with hover.
- **Progress ring**: SVG 200×200 viewBox rotated -90°; track circle r=90 stroke 6 in accent; progress circle stroke 6, round caps, primary (focus) / secondary (breaks); dash offset animates with a **1 s linear transition** per tick; ring **fills clockwise as time elapses** (empty at start). Sizes: 224px normal; 320px expanded (448px on large screens).
- Time text centered over the ring: `MM:SS` zero-padded (minutes may exceed 59, e.g. `90:00`), JetBrains Mono bold, `text-5xl` normal / up to `text-8xl` expanded; while running it pulses (opacity 1 → 0.85 → 1 over 2 s, infinite). Mode label below, muted.
- Controls: Reset (`RotateCcw`) and Skip (`SkipForward`) as muted round icon buttons; center Play/Pause round button (56px normal / 80px expanded) in primary (focus) or secondary (break) with hover scale 105% / active 95%. Aria-labels: "Reset", "Start"/"Pause", "Skip".
- **Loading skeleton** until first load: static tabs, track-only ring, `--:--` in muted mono, label "Loading".
- Document title is static ("Dayboard"); no countdown in the tab title.

---

## 7. Functional requirements — Tasks, Notes, Tags, Formatting

### 7.1 Task model

`{ id, text, body|null, done, position, parentId|null, tagIds: [tagId] }`. One level of nesting only (subtasks cannot have subtasks). **No due dates, no priorities.** IDs generated client-side (UUID) for optimistic UI.

### 7.2 Tasks card behaviors

- **Add form** on top: input placeholder **"Add a new task..."** + 40×40 primary `Plus` button (aria "Add task"), disabled when blank. On add: position = max(top-level positions)+1 (or 0); optimistic append; input clears and refocuses; if a tag filter is active the new task gets that tag automatically; the **edit dialog auto-opens** for the new task.
- **Tag filter row** (only when tags exist): `Filter` icon, an **"All"** chip, then one chip per tag (created-at order, color-tinted, emoji shown). Single-select; clicking the active chip clears it. Filter applies to pending and completed groups.
- **List**: pending top-level tasks (position asc), then a **Completed** group: header **"Completed · {count}"** (uppercase, tiny, muted), rows at 75% opacity, not draggable.
- **Row**: drag handle (pending only, fades in on hover) · custom checkbox (20px, rounded, hover previews the checkmark in primary) · expand chevron only when the task has body, subtasks, or tags (rotates 90°) · title (click opens View dialog) with inline tiny tag pills and a `{done}/{total}` subtask badge when collapsed · `Pencil` edit button that fades in on hover.
- **Inline expansion** (chevron): shows tag pills, the body (muted box, preserved line breaks), and read-only subtask rows where only the checkbox is interactive. State is in-memory only.
- **Completion**: toggling a top-level task cascades the same value to all its subtasks (both directions). Toggling a subtask affects only itself; no auto-complete of the parent. Completed tasks keep their position.
- **Reorder (drag)**: pending top-level only. Uses the **position-pool algorithm**: collect the positions of the currently visible items, sort them, reassign in the new visual order. Only changed rows are written. Reorder guard blocks refreshes until writes settle + 800 ms.
- **Empty states**: **"No tasks yet — type above to get started."** / with filter: **"No tasks with this tag."** Loading: spinner + **"Loading tasks..."**.
- All writes optimistic, no error toasts, no undo (parity).

### 7.3 Task Edit dialog

Modal (max-w 48rem, max-h 85vh, scrollable). SR title "Edit Task".

- **Title** input: saves on blur and Enter; empty → **"Untitled"**. FormattingToolbar attached.
- **Notes** textarea (6 rows, resizable, placeholder **"Add details or notes..."**): saves on blur; empty → null. FormattingToolbar attached.
- Closing the dialog flushes unsaved title/body changes.
- **Tags** section (top-level tasks only): attached pills (click removes, `X` icon), available pills (click attaches), plus a **"New tag"** creator: emoji input (maxLength 2, placeholder 😊), name input (Enter creates), 10-swatch palette `#6366f1 #ec4899 #f59e0b #10b981 #3b82f6 #8b5cf6 #ef4444 #14b8a6 #f97316 #64748b` (default first), Create/Cancel. Duplicate name (case-insensitive) → toggles the existing tag instead of creating.
- **Subtasks** section (top-level only): draggable rows with read-only done indicator; click text to edit in place (blur/Enter saves, Escape cancels); `X` deletes immediately without confirmation; **"+ Add subtask"** form (placeholder **"Subtask title..."**, stays open for adding more); empty state **"No subtasks yet."** Subtask reorder renumbers positions compactly 0..n-1 and writes only changed indices.
- **Delete**: two-step inline: "Delete task" → **"Delete this task and all its subtasks?"** + Delete/Cancel. Deletes the parent and (client-side, since Firestore has no cascade) all its subtasks; closing after delete skips the usual list refresh.

### 7.4 Task View dialog

Read-only: formatted title, tag pills, formatted body (or italic **"No notes."**), subtasks header **"Subtasks · {done}/{total}"** with toggleable checkboxes and drag reorder (same compact renumbering), and an **"Edit task"** button that switches to the Edit dialog.

### 7.5 Notes

Same interaction patterns as tasks where applicable. Model: `{ id, title, body|null, position, tagIds }`. No done state, no subtasks, no search, no pinning.

- Add form placeholder **"Add a new note..."**; new note position = current note count; edit dialog auto-opens; active filter auto-tags.
- Rows: drag handle, expand chevron (only when body or tags exist), title via formatted rendering with tiny tag pills, hover `Pencil`. Expanded: tag pills + body preview clamped to 2 lines (tooltip **"Click to view full note"**, click opens View).
- Reorder: renumbers the **visible (filtered) subset** compactly 0..n-1 (accepting possible collisions with hidden notes; parity).
- Edit dialog: Title (blur/Enter, empty → "Untitled") + **Content** textarea (12 rows, placeholder **"Write your note..."**, blur save, empty → null), both with FormattingToolbar; same tags section and creator; delete confirm **"Delete this note?"**.
- View dialog: formatted title/body, tag pills, empty body **"No content yet."**, button **"Edit note"**.
- Empty/loading strings: **"No notes yet — type above to get started."**, **"No notes with this tag."**, **"Loading notes..."**.

### 7.6 Tags (shared between tasks and notes)

Model: `{ id, name, color (hex), emoji|null, createdAt }`. Name unique per user (case-insensitive duplicate check in app logic). Created from the task/note edit dialogs; edited and deleted from the settings panel (§8, section 7). Deleting a tag must remove it from every task and note (client-side `arrayRemove` batch; Firestore has no cascade).

Color tinting convention (background = tag hex + alpha suffix, text = raw hex): `15` filter chip unselected / available pill, `18` inline collapsed pill, `20` standard pill in dialogs/expanded, `30` filter chip selected.

### 7.7 Formatting toolbar and rendering

- **Storage format**: plain text with inline markers: `**bold**`, `*italic*`, `__underline__`, `` `code` ``. Edit fields show raw markers; list rows and view dialogs render them.
- **Toolbar** (under title/notes/subtask-edit fields): 4 buttons (Bold, Italic, Underline, Code icons), always visible; mousedown is prevented so clicking never blurs the field. Wrap/unwrap toggle logic: if the selection is immediately surrounded by the markers, remove them; otherwise wrap; restore focus and selection afterwards; collapsed cursor inserts an empty pair.
- **Renderer** (LinkifiedText equivalent): single-pass parse with precedence URL → bold → italic → underline → code; bold/italic/underline parse recursively, code is literal; plain URLs (`http(s)://...` up to whitespace or `< > " ' ) ] } ,`) become links opening in a new tab (`noopener noreferrer`), primary-tinted underline, and clicks on links must not open the row's dialog. Applies everywhere formatted text is shown; never in edit inputs.

---

## 8. Functional requirements — Settings panel

Trigger: gear icon button in the header. Opens a right-side panel (full height, max width 24rem, card background, left border, slides in from the right over 200 ms) over a blurred backdrop (click closes). Header row: **"Settings"** + `X` close button. Body scrolls; sections stacked with 32px gaps; each section = icon (primary) + label.

Sections in exact order (all changes apply instantly, optimistic, no Save button):

1. **Widgets** (`Monitor` icon): toggles **"Pomodoro Timer"** (Show the Pomodoro timer card), **"Tasks"** (Show the tasks card), **"Notes"** (Show the notes card).
2. **Clock Settings** (`Clock`): toggle **"Show Seconds"** (Display seconds in the clock (HH:mm:ss)).
3. **Weather** (`CloudSun`): toggle **"Show Weather"** (Display weather info on the clock card); when on, a **"City"** input (placeholder "e.g. Tokyo, London...", hint "Leave empty for auto-detect", commits on blur/Enter, trimmed, empty → null) with an **"Auto"** button (`MapPin`) shown when a city is set.
4. **Timer Durations** (`Clock`; only when the Pomodoro card is enabled): sliders **Focus** (1–90 min), **Short Break** (1–30 min), **Long Break** (1–60 min), **Long Break Every** (2–8 sessions); value shown right-aligned as "{value} {unit}" in tabular figures.
5. **Automation** (`Zap`; only with Pomodoro enabled): toggles **"Auto-start Breaks"** / **"Auto-start Focus"** with their descriptions.
6. **Sound** (`Volume2` when on / `VolumeX` when off): toggle **"Notification Sound"** (Play a sound when a timer session ends); **"Volume"** slider (0–100 %) only when enabled.
7. **Tags** (`Tag`; only when the user has tags): per-tag row with tinted pill; hover reveals `Pencil` edit and `Trash2` delete. Delete is two-step inline (**"Delete"** / **"No"**). Edit swaps in a small card: emoji input (maxLength 2), name input (Enter saves, Escape cancels), the 10-color palette, Save/Cancel. Empty name blocks save. No tag creation here.
8. **Appearance** (`Palette`): **"Theme"** grid (3 columns) of the 6 themes with color dots; **"Mode"** segmented row Light (`Sun`) / Dark (`Moon`) / System (`Monitor`). Both apply instantly.
9. **Notifications** (`Bell`; only when the platform supports notifications): outlined button **"Enable push notifications"** (`BellOff`) → permission/subscribe flow; once granted and subscribed, a static primary-tinted pill **"Push notifications enabled"** (`Bell`).
10. **Sign Out**: separated by a top border; button **"Sign out"** (`LogOut`), destructive-tinted on hover.

Custom controls (exact): toggle switch = 20×36px track (primary when on, accent when off) with a 16px white knob sliding 16px; slider = native range input with 6px accent track and 16px primary round thumb (hover scale 110%).

A separate **ThemeToggle popover** component exists in the original (palette button opening a 224px dropdown with the same theme grid + mode row, closing on outside click) but is never mounted in the header. **Decided: not implemented** — the Appearance section covers it.

---

## 9. Functional requirements — Theming

- **6 accent themes** × light/dark: `coral` (default), `ocean`, `forest`, `lavender`, `ember`, `slate`. Color mode: `light` / `dark` / `system` (default `system`), where system follows `prefers-color-scheme` live.
- Applied to the DOM as `data-theme="<id>"` attribute + `dark` class on the root element; all colors are HSL CSS variables (full 12-palette table in §10.2).
- Theme picker swatch hexes: coral `#f43f5e`, ocean `#0ea5e9`, forest `#22c55e`, lavender `#a78bfa`, ember `#f97316`, slate `#64748b`.
- **Persistence**: localStorage keys `themeId` and `colorMode` written on every change (instant paint on next load, before auth resolves), plus the same values in the user's settings document (DB wins over localStorage once auth resolves). Validation nuance (parity): values loaded from the DB are validated against the allowed lists and fall back to `coral` / `system` when invalid; values read from localStorage at boot fall back only when missing or empty (an invalid non-empty value is used as-is until the DB load corrects it).
- Signed out: theme still fully works via localStorage.

## 9b. Functional requirements — Settings persistence

One settings document per user: `users/{uid}/state/settings` holding all fields (§10.1). Full-document upsert (merge) on every change; local state updates first (optimistic). After a local write, suppress refetch-driven overwrites for 2 s (or rely on `hasPendingWrites`). Signed out: settings are in-memory defaults only (no localStorage fallback except theme). Remote changes stream in via the snapshot listener (300 ms debounce).

---

## 10. Data model (Firestore) and security rules

### 10.1 Collections

All under `users/{uid}`; timestamps via `serverTimestamp()`; `updatedAt` set on every write.

| Path | Fields |
|---|---|
| `users/{uid}/tasks/{taskId}` | `text: string`, `body: string?`, `done: bool = false`, `position: int = 0`, `parentId: string?`, `tagIds: string[]`, `createdAt`, `updatedAt` |
| `users/{uid}/notes/{noteId}` | `title: string`, `body: string?`, `position: int = 0`, `tagIds: string[]`, `createdAt`, `updatedAt` |
| `users/{uid}/tags/{tagId}` | `name: string`, `color: string = "#6366f1"`, `emoji: string?`, `createdAt` |
| `users/{uid}/state/timer` | `mode: string = "focus"`, `timeLeft: int = 1500`, `isRunning: bool = false`, `completedSessions: int = 0`, `lastTickAt: timestamp?`, `updatedAt` |
| `users/{uid}/state/settings` | `focusDuration=25, shortBreakDuration=5, longBreakDuration=15, longBreakInterval=4, autoStartBreaks=false, autoStartFocus=false, soundEnabled=true, soundVolume=70, themeId="coral", colorMode="system", displayMode="pomodoro", showSeconds=false, weatherCity=null, showWeather=true, showPomodoro=true, showTasks=true, showNotes=true, cardLayout: map, createdAt, updatedAt` |
| `users/{uid}/fcmTokens/{token}` | `createdAt` (phase 2, doc id = token) |

Notes:
- The junction tables (`task_tags`, `note_tags`) become `tagIds` arrays on the parent docs (insertion order preserved).
- The original's unused `tasks.subtitle` column and `displayMode` setting are carried only if trivial; see §12.5.
- Ordering fields replicate the original quirks: top-level task positions are sparse (never compacted); subtask and note reorders renumber compactly.

### 10.2 Security rules (`firebase/firestore.rules`, versioned in the repo)

```
match /users/{uid}/{document=**} {
  allow read, write: if request.auth != null && request.auth.uid == uid;
}
```

Optionally `allow delete: if false` on `users/{uid}/state/{doc}` for exact parity with the original's missing DELETE policies. Add field validation (types, string sizes) as hardening once the model settles.

### 10.3 Cascades (client-side, since Firestore has none)

- Deleting a task → delete its subtasks (`parentId == taskId`) in the same batch.
- Deleting a tag → `arrayRemove(tagId)` from all tasks and notes that hold it (batched).
- Account deletion (if ever added) → the "Delete User Data" extension or an `auth.onDelete` function.

---

## 11. Notifications and server-side

The original ships full Web Push: a Supabase edge function that generates/stores VAPID keys, manages subscriptions, and fans out RFC 8291 encrypted pushes to all of the user's devices; plus an `ip-location` function for weather.

For Dayboard, in phases:

- **Phase 1 (no server, free tier)**:
  - Timer-end notification shown **locally** via the Notifications API / service worker `showNotification`, gated behind the same permission flow and the same settings UI. Content identical: title `"{mode} complete!"`; body **"Great work! Time for a break."** after focus, **"Break's over — time to focus!"** after breaks. SW notification options parity: icon/badge = favicon, tag `timer-notification`, `renotify: true`, vibrate `[200, 100, 200]`; clicking focuses an existing app window or opens one.
  - `ip-location` replaced by calling `https://ipapi.co/json/` directly from the browser (§5.2). The `ip-api.com` fallback is dropped (HTTP-only on the free tier; blocked as mixed content from an HTTPS page); browser geolocation remains the fallback.
- **Phase 2 (optional, requires the Blaze plan)**: FCM Web Push for true cross-device delivery: `firebase-messaging-sw.js`, `getToken(vapidKey)` stored at `users/{uid}/fcmTokens/{token}`, and a callable Cloud Function `sendPush({title, body})` that multicasts to all of the caller's tokens and prunes `registration-token-not-registered` ones (the 410-cleanup analog). Auto-resubscribe on login when permission is already granted (parity).

---

## 12. Visual specification

### 12.1 Typography

- Google Fonts: **Inter** 400/500/600/700 (UI) and **JetBrains Mono** 500/700 (clock and timer digits), `display=swap`.
- Body: Inter, antialiased, `background`/`foreground` tokens.

### 12.2 Color tokens

All tokens are HSL triplets consumed as `hsl(var(--token))` (alpha applied via `hsl(var(--token) / a)`). `--input` always equals `--border`; `--ring` always equals `--primary`. `--destructive: 0 84% 60%` with white foreground everywhere. `--radius: 0.75rem`.

**Coral light** (default): background `350 30% 97%`, foreground `350 25% 15%`, card/popover `350 20% 99%`, primary `350 91% 60%` (fg white), secondary `160 59% 45%` (fg white), muted `350 16% 94%`, muted-fg `350 12% 42%`, accent `350 16% 90%`, border/input `350 14% 89%`.

**Coral dark**: background `222 20% 10%`, foreground `210 20% 92%`, card/popover `222 18% 14%`, primary `350 91% 60%`, secondary `160 59% 45%`, muted `222 14% 18%`, muted-fg `215 15% 55%`, accent `222 14% 22%`, border/input `222 14% 20%`.

**Ocean**: light bg `200 35% 96%` fg `210 30% 14%` card `200 30% 98%` primary `199 89% 48%` secondary `172 66% 50%` muted `200 22% 92%`/`210 16% 44%` accent `200 22% 88%` border `200 20% 87%`; dark bg `215 28% 9%` fg `210 20% 92%` card `215 25% 13%` muted `215 20% 18%`/`215 15% 55%` accent `215 18% 22%` border `215 18% 20%` (primary/secondary unchanged).

**Forest**: light bg `140 30% 96%` fg `150 30% 12%` card `140 25% 98%` primary `142 71% 45%` secondary `47 96% 53%` (secondary-fg dark: `150 25% 14%`) muted `140 20% 92%`/`150 14% 40%` accent `140 20% 87%` border `140 18% 86%`; dark bg `150 20% 8%` fg `138 16% 90%` card `150 18% 12%` muted `150 14% 17%`/`140 10% 52%` accent `150 14% 21%` border `150 14% 19%`.

**Lavender**: light bg `268 32% 96%` fg `270 28% 16%` card `268 28% 98%` primary `263 70% 71%` secondary `330 80% 65%` muted `268 20% 92%`/`270 14% 43%` accent `268 20% 88%` border `268 18% 87%`; dark bg `270 22% 9%` fg `270 15% 90%` card `270 20% 13%` muted `270 16% 18%`/`270 12% 53%` accent `270 16% 22%` border `270 16% 20%`.

**Ember**: light bg `28 35% 96%` fg `20 28% 13%` card `28 30% 98%` primary `25 95% 53%` secondary `43 96% 56%` (secondary-fg dark: `20 25% 15%`) muted `28 22% 91%`/`20 14% 41%` accent `28 22% 87%` border `28 20% 86%`; dark bg `20 22% 9%` fg `30 18% 90%` card `20 20% 13%` muted `20 16% 17%`/`20 10% 52%` accent `20 16% 21%` border `20 16% 19%`.

**Slate**: light bg `220 20% 95%` fg `224 22% 16%` card `220 16% 98%` primary `215 16% 47%` secondary `215 25% 60%` muted `220 18% 90%`/`220 12% 44%` accent `220 18% 86%` border `220 16% 85%`; dark bg `224 20% 9%` fg `220 14% 90%` card `224 18% 13%` **primary `215 20% 55%`** (the only theme whose primary differs per mode) muted `224 14% 17%`/`220 10% 52%` accent `224 14% 21%` border `224 14% 19%`.

Card/popover foregrounds always equal the theme's foreground. The original also defines sidebar tokens (coral-only); Dayboard has no sidebar, so they are dropped.

### 12.3 Shape, borders, shadows

- Radii from the 12px base token: 12px (cards use 16px `rounded-2xl` for dashboard cards and the auth card), 10px, 8px.
- 1px hairline borders in `--border` as the default border color everywhere.
- Shadows: Tailwind `shadow-sm` on cards, `shadow-lg` on dialogs/dragged items, `shadow-xl` on the settings panel.
- No gradients. Backdrop blur only on the expanded-card overlay and the settings-panel backdrop (`background` at 60% + small blur). Dialog overlays are flat black at 80%.

### 12.4 Animations

- `timer-pulse`: opacity 1 → 0.85 → 1, 2 s ease-in-out infinite (running timer digits).
- `task-complete`: opacity 1 → 0.4, 0.3 s ease-out forwards.
- Dialog enter/exit: fade + zoom-95, 200 ms. Settings panel: slide-in-from-right 200 ms. Theme popover: fade + zoom-95, 150 ms. Inline expansions: fade + slide-from-top, 150 ms. Progress ring: 1 s linear per tick. Hover transitions on colors/transforms throughout.

### 12.5 Scrollbars and misc

- Themed thin scrollbars everywhere: 6px, transparent track, pill thumb in muted-foreground at 25% alpha (40% hover).
- Viewport meta disables pinch zoom (`maximum-scale=1.0, user-scalable=no`).
- Breakpoints: Tailwind defaults (sm 640, md 768, lg 1024, xl 1280); dashboard content capped at 64rem; dialogs at 48rem; settings panel at 24rem.
- Icons: lucide set throughout (used names are listed per feature in the appendix). For Compose HTML, embed lucide SVG paths as composables.

---

## 13. Non-functional requirements

- **Testing**: all timer/task/note/tag/layout/parsing logic lives in `:shared` and is covered by unit tests (state machine transitions, restore math, position-pool reorder, compact renumbering, `parseCardLayout` including legacy migration, marker wrap/unwrap, render tokenizer, WMO icon mapping, settings fallbacks, Firebase error-code mapping). Add a `jvm()` target to `:shared` so tests also run on the JVM and **Kover** can report coverage (Kover does not support Kotlin/JS). Coverage targets per my standard rules: 100% line/method, 95%+ branch on testable shared code.
- **Code quality**: ktlint, zero warnings in touched files, KDoc on public shared APIs, no magic values (constants objects), no secrets in code (the Firebase web config is public by design).
- **Performance**: initial JS bundle reasonable for Pages (webpack production build); Firestore listeners scoped to the signed-in user only; no polling except the 1 s clocks and the 10 min weather refresh.
- **Browsers**: evergreen Chrome/Firefox/Safari/Edge, desktop and mobile. Notifications degrade gracefully where unsupported (section simply hidden, as in the original).
- **Accessibility**: keep the original's aria-labels and SR-only dialog titles/descriptions; keyboard: Enter/Escape semantics in all edit fields as specified.
- **No i18n requirement** (English only), but strings go through the `:shared` string catalog to keep them testable and centralized.

---

## 14. Known deviations from Focusly (need approval)

| # | Deviation | Reason | Impact |
|---|---|---|---|
| 1 | Hash routing instead of history routing | GitHub Pages has no rewrites | URLs look like `dayboard-app.github.io/#/auth` |
| 2 | Auth error strings mapped from Firebase codes, not Supabase messages | Different backend | Same UI slot, slightly different wording |
| 3 | Email-verification gate enforced client-side | Firebase signs in unverified users by default | Same UX, different mechanism |
| 4 | Push: phase 1 is local notifications only; cross-device FCM push in phase 2 | Cloud Functions/FCM send needs the paid Blaze plan | Timer-end notification still appears on the active device; other devices only in phase 2 |
| 5 | `ip-location` server function replaced by direct `ipapi.co` call + geolocation fallback; `ip-api.com` fallback dropped | No server; mixed-content block | Same weather UX; arguably more accurate (real client IP) |
| 6 | Junction tables become `tagIds` arrays | Firestore modeling | None visible |
| 7 | Unused leftovers dropped: `tasks.subtitle`, sidebar tokens, second toast system (original mounts two toast systems but fires no feature toasts), React Query | Dead weight in the original | None visible |
| 8 | `displayMode` setting field kept in the model but still unused (as in the original) | Parity of stored data | None |
| 9 | Firestore offline cache gives latency compensation the original lacks | Free improvement | Strictly better UX on flaky networks |

## 15. Decisions (all resolved 2026-08-24)

| # | Decision | Outcome |
|---|---|---|
| 1 | Email verification | **Enforced.** Unverified users are signed back out with an error, matching the original's confirm-before-use behavior (§3.2) |
| 2 | Phase 2 push (FCM + Blaze) | **Planned**, not built yet. Phase 1 ships local notifications; cross-device FCM push stays a gated later phase (§11) |
| 3 | Package name | `io.github.dayboard` |
| 4 | ThemeToggle header popover | **Skipped.** The original never mounts it and the Appearance section covers the same controls (§8) |
| 5 | App icon | **Done.** Generated from one geometric definition by [tools/generate_icons.py](tools/generate_icons.py) into `src/jsMain/resources/`: `favicon.ico` (16/32/48), `favicon.png`, `icon-16/32/180/192/512.png`, and `og-image.png` (1200×630, replacing the original's expiring Lovable OG URL). The mark is the app's own layout — the always-on-top clock card above the two card columns — in the coral primary `hsl(350 91% 60%)`, with colors derived from the design tokens rather than hardcoded |
| 6 | Firebase project | **Created.** `Dayboard` / project ID `dayboard-app`, Spark (free) plan, Firestore in `europe-west3` (Frankfurt), Email/Password auth on, owner-only rules published, `dayboard-app.github.io` authorized. Full state and the web config in [firebase/README.md](firebase/README.md) |

---

## Appendix: source analysis

Exhaustive per-file extraction from the original repo (exact CSS classes, all strings, DB schema and RLS, edge-function protocol details):

- [docs/source-analysis/01-app-shell.md](docs/source-analysis/01-app-shell.md)
- [docs/source-analysis/02-timer-clock.md](docs/source-analysis/02-timer-clock.md)
- [docs/source-analysis/03-tasks.md](docs/source-analysis/03-tasks.md)
- [docs/source-analysis/04-notes.md](docs/source-analysis/04-notes.md)
- [docs/source-analysis/05-settings-theme.md](docs/source-analysis/05-settings-theme.md)
- [docs/source-analysis/06-visuals.md](docs/source-analysis/06-visuals.md)
- [docs/source-analysis/07-backend-auth.md](docs/source-analysis/07-backend-auth.md)
