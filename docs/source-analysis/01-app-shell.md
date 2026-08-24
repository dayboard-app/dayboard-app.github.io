# App Shell — Focusly (rebuild as Dayboard) — Exhaustive Findings

## 1. Overall app description (from README.md)

- **Focusly** is a "distraction-free productivity app combining a Pomodoro timer with an integrated task management system." Tagline behaviors: customizable focus sessions, to-do list management, real-time sync across devices.
- Feature list (README, verbatim intent):
  - Pomodoro Timer: customizable focus, short break, and long break durations with auto-start options
  - Task Management: create, edit, complete, and reorder tasks with drag-and-drop support
  - Task Tags: color-coded, emoji-enhanced custom tags
  - Push Notifications: desktop notifications when a timer session ends
  - Dark / Light Mode: persistent theme preference
  - Real-time Sync: live data synchronization across tabs and devices via Supabase Realtime
  - User Authentication: secure email/password sign-up and login
  - Settings: configure timer durations, auto-start behavior, and sound preferences
- Tech stack (original): React 18, TypeScript, Vite; Tailwind CSS + shadcn/ui + Radix UI; React Context + TanStack React Query; Supabase (PostgreSQL, Auth, Realtime, Edge Functions); @hello-pangea/dnd for drag & drop; Vitest + Playwright for tests.
- Database tables per README: `tasks` (user tasks with position, completion status, optional parent for subtasks), `tags` (name, color, optional emoji), `task_tags` (many-to-many), `timer_state` (persisted Pomodoro timer state per user), `user_settings` (timer durations, sound, auto-start). NOTE: the actual code (Index.tsx realtime subscriptions) additionally uses `notes` and `note_tags` tables — the README table list is stale; a Notes feature exists.
- Dev server runs at http://localhost:8080.
- License: MIT.

## 2. Entry point and HTML shell (index.html, src/main.tsx)

### index.html
- `<html lang="en">`.
- `<meta charset="UTF-8" />`.
- Viewport: `width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no` (zooming disabled — replicate for mobile-app feel).
- `<title>Focusly</title>` (Dayboard: "Dayboard").
- Meta description (also used for og:description and twitter:description): "A distraction-free Pomodoro timer with a built-in to-do list to help you stay focused and productive."
- `<meta name="author" content="Lovable" />` (artifact of the Lovable generator; can be replaced).
- Open Graph / Twitter tags: `og:type=website`, `og:title=Focusly`, `og:image` + `twitter:image` point to a signed Google Cloud Storage URL (Lovable-generated OG image; expires — replace with own asset), `twitter:card=summary_large_image`, `twitter:site=@Lovable`.
- Icons: `<link rel="icon" type="image/png" href="/favicon.png">` and `<link rel="apple-touch-icon" href="/apple-touch-icon.png">`. Both static files under `public/`.
- No PWA manifest, no service-worker registration in index.html (push notifications are handled in-app via a hook, not via a manifest).
- Body: single `<div id="root"></div>` plus `<script type="module" src="/src/main.tsx"></script>`. No other external scripts loaded in HTML.

### External fonts
- No fonts loaded from index.html. `src/index.css` starts with:
  `@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@500;700&display=swap');`
  So the app loads **Inter (400/500/600/700)** as the UI font and **JetBrains Mono (500/700)** as the mono font (used e.g. for clock/timer digits), from Google Fonts with `display=swap`.

### src/main.tsx
- Minimal: `createRoot(document.getElementById("root")!).render(<App />)` and imports `./index.css`. No React.StrictMode wrapper.

## 3. Provider nesting and root composition (src/App.tsx)

Exact nesting order (outermost to innermost):
1. `QueryClientProvider` with `const queryClient = new QueryClient();` — **default configuration, zero custom options** (default retries, staleTime etc.). React Query is set up but the app's own code primarily uses contexts + Supabase directly.
2. `ThemeProvider` (custom, from `@/contexts/ThemeContext`) — dark/light + color theme; exposes at least `reload()`.
3. `AuthProvider` (custom, from `@/contexts/AuthContext`) — exposes `{ user, loading, signOut }` at minimum.
4. `TooltipProvider` (shadcn/Radix tooltip context).
5. Siblings inside TooltipProvider, in order: `<Toaster />` (shadcn toast system), `<Sonner />` (sonner toast system), then `<BrowserRouter>` with `<Routes>`.

`SettingsProvider` is NOT global: it wraps ONLY the Index page, inside the protected route: `<ProtectedRoute><SettingsProvider><Index /></SettingsProvider></ProtectedRoute>`. Settings are therefore only loaded/available for authenticated users on the main page.

## 4. Routing

- `BrowserRouter` (history-based URLs, no hash).
- Routes:
  - `path="/auth"` → `Auth` page (public).
  - `path="/"` → `ProtectedRoute` wrapping `SettingsProvider` wrapping `Index`.
  - `path="*"` → `NotFound`.
- `ProtectedRoute` logic (exact):
  - While `loading` (auth state resolving): renders `<div className="min-h-screen bg-background" />` — a full-height blank screen in the background color (this IS the app-level loading state; no spinner).
  - If not `user`: `<Navigate to="/auth" replace />`.
  - Else render children.
- Sign-out flow from Index: `await signOut(); navigate("/auth")`.

## 5. NotFound page (src/pages/NotFound.tsx)

- On mount (and whenever pathname changes), logs to console: `console.error("404 Error: User attempted to access non-existent route:", location.pathname)`.
- UI: full-screen (`min-h-screen`) flex-centered container with background `bg-muted`; centered column with:
  - `<h1>` "404" — `text-4xl font-bold`, `mb-4`.
  - `<p>` "Oops! Page not found" — `text-xl text-muted-foreground`, `mb-4`.
  - `<a href="/">` "Return to Home" — `text-primary underline hover:text-primary/90` (plain anchor, full page reload).

## 6. Main page (src/pages/Index.tsx) — layout & composition

### 6.1 Card model
- Four card types, type `CardId = "clock" | "timer" | "tasks" | "notes"`.
- Display labels (`CARD_LABELS`): clock → "Clock", timer → "Pomodoro", tasks → "Tasks", notes → "Notes".
- Card contents: clock → `ClockDisplay`, timer → `PomodoroTimer` (prop `onTimerEnd`), tasks → `TodoList`, notes → `NotesList`. TodoList/NotesList receive a `reloadRef` (a ref the child fills with its reload function) and all receive `expanded: boolean`.

### 6.2 Page structure
- Root: `<div className="min-h-screen bg-background">`.
- **Header**: `<header className="border-b px-6 py-4">` containing `<div className="mx-auto flex max-w-5xl items-center gap-2">`:
  - Refresh button: icon-only button with lucide `Timer` icon (`h-5 w-5`), classes `p-1 text-primary hover:text-primary/80 transition-colors`, `title="Refresh"`. On click: sets a `spinning` state true → icon gets `animate-spin` → awaits `reloadAll()` → spinning false. `reloadAll` = `Promise.all([reloadSettings(), reloadTheme()])` then calls the todo reload ref and notes reload ref.
  - App title: `<h1 className="text-lg font-semibold">Focusly</h1>` (Dayboard: "Dayboard").
  - Right side (`ml-auto flex items-center gap-3`): user email `<span className="text-sm text-muted-foreground hidden sm:inline">{user?.email}</span>` (hidden below the `sm` breakpoint, 640px), then `SettingsPanel` component with props: `onTagsChanged` (reloads todos + notes), `onSignOut` (signs out and navigates to /auth), and `pushNotifications={{ isSupported, permission, subscribed, subscribe }}` from `usePushNotifications()`.
- **Main**: `<main className="mx-auto max-w-5xl px-6 py-10 space-y-6">` (max content width 64rem/1024px, centered).

### 6.3 Clock card (special)
- The Clock card is rendered ABOVE the two columns, always on top, fixed position in flow, NOT draggable, always visible (`isVisible("clock")` returns true unconditionally).
- It is hidden only while some other card is expanded fullscreen (condition renders clock card only when no card is expanded).
- It is collapsible (participates in `collapsed` list) and expandable (maximize button).

### 6.4 Two-column drag-and-drop layout
- Container: `<div className="flex flex-col md:flex-row gap-6 items-start">` — **responsive: single column stacked below `md` (768px), two side-by-side flex-1 columns at md and above**. Wrapped in `DragDropContext` (from @hello-pangea/dnd) with `onDragEnd={onCardDragEnd}`. Rendered only when no card is expanded and at least one visible card exists in either column.
- Each column is a `Droppable` with ids `"col-left"` and `"col-right"`. Column div classes: `flex-1 w-full flex flex-col gap-6 min-h-[80px] rounded-xl transition-colors`, plus when dragging over: `bg-primary/5 ring-2 ring-primary/10 ring-inset`.
- Empty-column placeholder (shown when the column has 0 visible cards and is not being dragged over): `flex items-center justify-center py-12 rounded-xl border-2 border-dashed border-border/40 text-muted-foreground/40 text-xs` with text **"Drop cards here"**.
- Each card in a column is a `Draggable` (draggableId = card id). While dragging: wrapper gets `opacity-90 z-50`; the card itself gets `shadow-lg ring-2 ring-primary/20`.

### 6.5 Layout state (CardLayout) & persistence
- `CardLayout` (type from SettingsContext) = `{ left: string[], right: string[], widths: Record<string, "half"|...>, collapsed: string[] }`.
- Defaults when settings have no layout: `left: ["timer"]`, `right: ["tasks", "notes"]`, `widths: { timer: "half", tasks: "half", notes: "half" }`, `collapsed: []`. (`widths` is carried in state but not used for rendering in Index — columns are always flex-1.)
- Local state `localLayout` gives instant UI updates; whenever `settings.cardLayout` arrives with both `left` and `right` set, local state syncs from it.
- **Saving is debounced 500 ms**: `saveLayout` clears the previous timer and after 500 ms calls `updateSettings({ cardLayout: layout })` (persisted via SettingsContext → `user_settings` table).
- Collapse toggle: `toggleCollapse(card)` adds/removes card id in `collapsed` array, updates local state immediately, schedules debounced save.

### 6.6 Card visibility
- `isVisible(card)`: clock → always true; timer → `settings.showPomodoro`; tasks → `settings.showTasks`; notes → `settings.showNotes`; anything else → false. Columns render `localLayout.left/right.filter(isVisible)`.

### 6.7 Drag-end algorithm (exact semantics)
- If no destination: ignore.
- Same column reorder: work on the FULL column array but compute dragged/target from the VISIBLE (filtered) list by index; if no dragged card or dragged === target, ignore. Remove dragged from full array, find target's index in the new array, insert after it if moving down (`destination.index > source.index`), before it if moving up. Save via `updateLocalLayout` (immediate local + debounced persist).
- Cross-column move: dragged card = visible source list at `source.index` (ignore if missing). Remove from full source array. In destination, find the visible card currently at `destination.index`; if found, insert dragged before that card's index in the FULL destination array; if the destination visible list is shorter (dropped at end), append to end of full destination array. Persist both columns.

### 6.8 Expand (fullscreen) behavior
- `expandedCard: CardId | null`; `toggleExpand(card)` toggles (same card → null).
- When a card is expanded:
  - Fullscreen overlay: `<div className="fixed inset-0 z-30 bg-background/60 backdrop-blur-sm" onClick={() => setExpandedCard(null)} />` — clicking the dimmed/blurred backdrop closes.
  - Expanded card container: `<div className="fixed inset-4 z-40 transition-all duration-300">` holding a `CollapsibleCard` with `expanded`, `collapsed={false}`, no drag handle.
  - Expanded card extra classes: for clock/timer → card `h-full flex flex-col items-center justify-center`, content `flex-1 flex items-center justify-center` (content vertically/horizontally centered); for tasks/notes → card `h-full flex flex-col overflow-hidden`, content `flex-1 overflow-y-auto` (scrollable list).
  - The normal clock card and the two-column grid are hidden while any card is expanded.
  - Card content receives `isExpanded = true` (components render larger variants).

### 6.9 CollapsibleCard (reusable shell for every card)
- Wrapper: `w-full rounded-2xl border bg-card shadow-sm relative`, plus `shadow-lg ring-2 ring-primary/20` while dragging, plus optional className.
- Header row: `flex items-center justify-between px-6 py-3`.
  - Left group (`flex items-center gap-1`):
    - Drag handle (only when not expanded and handle provided): lucide `GripVertical` `h-4 w-4`, wrapper `cursor-grab active:cursor-grabbing p-1 text-muted-foreground/30 hover:text-muted-foreground transition-colors`.
    - Collapse toggle button (only when not expanded): `flex items-center gap-2 text-sm font-semibold text-foreground hover:text-foreground/80 transition-colors`; icon lucide `ChevronDown` (`h-4 w-4`) when collapsed, `ChevronUp` when open, followed by the title text.
    - When expanded: plain `<span className="text-sm font-semibold text-foreground">{title}</span>` (no collapse control).
  - Right group (`flex items-center gap-0.5`): maximize/minimize button — `flex h-7 w-7 items-center justify-center rounded-lg text-muted-foreground hover:bg-muted hover:text-foreground transition-colors`, `aria-label` "Maximize" / "Minimize", icon lucide `Maximize2` / `Minimize2` at `h-3.5 w-3.5`.
- Content (hidden entirely when collapsed): `px-6 pb-6`, plus `flex flex-col items-center` when `centerContent` (used for the timer card so the dial centers), plus optional contentClassName.

### 6.10 Realtime sync (Supabase channel, in Index)
- On mount with a user, subscribes to one channel named `'realtime-sync'` listening to `postgres_changes` with `{ event: '*', schema: 'public' }` on tables:
  - `tasks` → call todo reload ref
  - `task_tags` → call todo reload ref
  - `tags` → call BOTH todo and notes reload refs
  - `notes` → call notes reload ref
  - `note_tags` → call notes reload ref
  - `user_settings` → **debounced 300 ms**: `reloadSettings()` and `reloadTheme()` (so remote settings/theme changes propagate across tabs/devices; debounce prevents thrash from the 500 ms layout saves).
- Cleanup on unmount: clear both debounce timers (300 ms settings debounce, 500 ms layout save) and `supabase.removeChannel(channel)`.

### 6.11 Timer-end notification wiring (app-shell responsibility)
- `handleTimerEnd(modeName: string)` passed to PomodoroTimer as `onTimerEnd`; calls `sendNotification(title, body)` from `usePushNotifications` with:
  - title: `` `${modeName} complete!` ``
  - body: if modeName includes "Focus" → "Great work! Time for a break." else → "Break's over — time to focus!"

## 7. Responsive behavior

- Header/main content constrained to `max-w-5xl` (1024px) with `px-6` gutters.
- Columns: stacked vertically below 768px (`flex-col`), side-by-side at ≥768px (`md:flex-row`), gap 24px (`gap-6`), `items-start`.
- User email in header hidden below 640px (`hidden sm:inline`).
- `useIsMobile()` hook (src/hooks/use-mobile.tsx): `MOBILE_BREAKPOINT = 768`; returns `window.innerWidth < 768`, initialized `undefined` (coerced to false) then set on mount; listens to `matchMedia("(max-width: 767px)")` change events. (Standard shadcn hook; used by ui components such as sidebar/drawer, not directly by Index.)
- Viewport meta disables pinch zoom (`maximum-scale=1.0, user-scalable=no`).

## 8. Toast systems (two coexist)

1. **shadcn/Radix toast** (`<Toaster />` from `@/components/ui/toaster` + `useToast` hook from `@/hooks/use-toast`): renders `ToastProvider` → mapped `Toast` items with optional `ToastTitle`, `ToastDescription`, action, `ToastClose` → `ToastViewport`. Driven by the `useToast()` store; used for app notifications fired via `toast({...})`.
2. **Sonner** (`<Sonner />` from `@/components/ui/sonner`): wraps the sonner `Toaster`; reads theme via `next-themes` `useTheme()` (default "system") and passes it through; `className="toaster group"`; `toastOptions.classNames`:
   - toast: `group toast group-[.toaster]:bg-background group-[.toaster]:text-foreground group-[.toaster]:border-border group-[.toaster]:shadow-lg`
   - description: `group-[.toast]:text-muted-foreground`
   - actionButton: `group-[.toast]:bg-primary group-[.toast]:text-primary-foreground`
   - cancelButton: `group-[.toast]:bg-muted group-[.toast]:text-muted-foreground`
- Both are mounted globally in App.tsx. For a 1:1 rebuild, a single toast system replicating both styles' usage is acceptable if all call sites are mapped, but note both exist.

## 9. Query client config

- `new QueryClient()` with **no options** — all TanStack Query defaults. No devtools mounted. For Dayboard: no special caching/retry semantics need reproducing beyond defaults.

## 10. Build/dev config (vite.config.ts)

- Dev server: `host: "::"` (all interfaces, IPv6), `port: 8080`, HMR error overlay disabled (`hmr: { overlay: false }`).
- Plugins: `@vitejs/plugin-react-swc`; `lovable-tagger`'s `componentTagger()` only in development mode (Lovable-editor instrumentation; irrelevant to rebuild).
- Path alias: `@` → `./src`.

## 11. public/robots.txt

Explicit Allow-all for: Googlebot, Bingbot, Twitterbot, facebookexternalhit, and `*` (each as `User-agent: X` / `Allow: /` blocks).

## 12. .env — variable names and services

All are Vite-exposed (VITE_ prefix) publishable client keys configuring **Supabase**:
- `VITE_SUPABASE_PROJECT_ID` — Supabase project id (value present: `devbpmxbyxsfrbrvbowo`).
- `VITE_SUPABASE_PUBLISHABLE_KEY` — Supabase anon (public) JWT key used by the JS client.
- `VITE_SUPABASE_URL` — Supabase project URL (`https://<project-id>.supabase.co`).
Dayboard equivalents: the Firebase web config object (apiKey, authDomain, projectId, etc.), also publishable client values.

## 13. src/App.css

Present but **unused leftover Vite template CSS** (`#root { max-width: 1280px; margin: 0 auto; padding: 2rem; text-align: center; }`, `.logo` hover filters, `logo-spin` keyframes, `.card`, `.read-the-docs`). It is not imported anywhere (main.tsx imports only index.css). Do NOT replicate; real styling lives in src/index.css (Tailwind + HSL design-token CSS variables, multiple named color themes via `[data-theme="..."]` incl. at least `coral` (default) and `ocean`, each with a `.dark` variant; `--radius: 0.75rem`).

## 14. Top-level dependencies (package.json) with purpose

Runtime dependencies:
- `@hello-pangea/dnd` 17.0.0 — drag & drop (card layout in Index, task reordering in TodoList).
- `@hookform/resolvers` ^3.10.0 — zod resolver for react-hook-form (form validation, e.g. Auth).
- `@radix-ui/react-*` (accordion 1.2.11, alert-dialog 1.1.14, aspect-ratio 1.1.7, avatar 1.1.10, checkbox 1.3.2, collapsible 1.1.11, context-menu 2.2.15, dialog 1.1.14, dropdown-menu 2.1.15, hover-card 1.1.14, label 2.1.7, menubar 1.1.15, navigation-menu 1.2.13, popover 1.1.14, progress 1.1.7, radio-group 1.3.7, scroll-area 1.2.9, select 2.2.5, separator 1.1.7, slider 1.3.5, slot 1.2.3, switch 1.2.5, tabs 1.1.12, toast 1.2.14, toggle 1.1.9, toggle-group 1.1.10, tooltip 1.2.7) — headless primitives underlying the shadcn/ui component set (full kit is installed; only a subset is actually used: dialog/sheet, checkbox, switch, slider, select, label, toast, tooltip, alert-dialog, popover among the likely used).
- `@supabase/supabase-js` ^2.99.1 — backend client: auth, Postgres CRUD, realtime channels (→ Firebase Auth + Firestore + listeners in Dayboard).
- `@tanstack/react-query` ^5.83.0 — query client provider (default config; minimal actual usage).
- `class-variance-authority` ^0.7.1 — variant-based class composition for shadcn components (button variants etc.).
- `clsx` ^2.1.1 + `tailwind-merge` ^2.6.0 — the `cn()` class utility.
- `cmdk` ^1.1.1 — command palette primitive (shadcn Command component; installed with kit).
- `date-fns` ^3.6.0 — date utilities (calendar component / date formatting).
- `embla-carousel-react` ^8.6.0 — carousel (shadcn kit; likely unused by features).
- `input-otp` ^1.4.2 — OTP input (shadcn kit; likely unused).
- `lucide-react` ^0.462.0 — icon set. Icons named in app shell: `Timer`, `Maximize2`, `Minimize2`, `ChevronDown`, `ChevronUp`, `GripVertical`.
- `next-themes` ^0.3.0 — theme detection used by the sonner wrapper (main theming is the custom ThemeContext).
- `react` ^18.3.1 / `react-dom` ^18.3.1 — framework.
- `react-day-picker` ^8.10.1 — calendar (shadcn kit).
- `react-hook-form` ^7.61.1 — forms.
- `react-resizable-panels` ^2.1.9 — resizable panel primitive (shadcn kit; likely unused).
- `react-router-dom` ^6.30.1 — routing (BrowserRouter, Routes, Navigate, useNavigate, useLocation).
- `recharts` ^2.15.4 — charts (shadcn chart component; likely unused by features).
- `sonner` ^1.7.4 — second toast system.
- `tailwindcss-animate` ^1.0.7 — Tailwind animation utilities used by shadcn components (accordion, fade/zoom of dialogs, etc.).
- `vaul` ^0.9.9 — drawer primitive (shadcn kit).
- `zod` ^3.25.76 — schema validation (auth forms).

Dev dependencies (tooling only): `@vitejs/plugin-react-swc`, `vite` ^5.4.19, `typescript` ^5.8.3, `tailwindcss` ^3.4.17 + `autoprefixer` + `postcss` + `@tailwindcss/typography`, `eslint` 9 + `typescript-eslint` + react-hooks/react-refresh plugins + `globals`, `vitest` ^3.2.4 + `@testing-library/react` ^16 + `@testing-library/jest-dom` ^6.6 + `jsdom` ^20, `@playwright/test` ^1.57 (E2E), `@types/*`, `lovable-tagger` ^1.1.13 (Lovable dev instrumentation, not needed in rebuild).

NPM scripts: `dev` (vite), `build` (vite build), `build:dev` (vite build --mode development), `lint` (eslint .), `preview` (vite preview), `test` (vitest run), `test:watch` (vitest).

## 15. Cross-component contracts the shell imposes (for other areas)

- `AuthContext` must expose: `user` (with `.email`), `loading`, `signOut()`.
- `ThemeContext` must expose: `reload()` (re-fetch persisted theme).
- `SettingsContext` must expose: `settings` (incl. `cardLayout?: CardLayout`, `showPomodoro`, `showTasks`, `showNotes`), `updateSettings(partial)`, `reload()`; exports type `CardLayout`.
- `usePushNotifications()` must expose: `permission`, `subscribed`, `subscribe`, `sendNotification(title, body)`, `isSupported`.
- `PomodoroTimer` props: `{ onTimerEnd: (modeName: string) => void, expanded: boolean }`.
- `ClockDisplay` props: `{ expanded: boolean }`.
- `TodoList` / `NotesList` props: `{ reloadRef: MutableRef<(() => void) | null>, expanded: boolean }` — component writes its reload fn into the ref.
- `SettingsPanel` props: `{ onTagsChanged: () => void, onSignOut: () => void, pushNotifications: { isSupported, permission, subscribed, subscribe } }`.

