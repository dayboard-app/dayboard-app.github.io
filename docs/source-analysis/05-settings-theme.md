# Area: Settings & Theme (Focusly → Dayboard rebuild spec)

Source files read completely: `src/components/SettingsPanel.tsx`, `src/contexts/SettingsContext.tsx`, `src/contexts/ThemeContext.tsx`, `src/components/ThemeToggle.tsx`, `src/components/NavLink.tsx`, plus (fetched because essential to the theme system) `src/index.css` and `tailwind.config.ts`.

---

## 1. Settings data model (SettingsContext)

### 1.1 `Settings` shape and exact defaults

| Field (app) | Type | Default | DB column (`user_settings`) |
|---|---|---|---|
| `focusDuration` | number (minutes) | `25` | `focus_duration` |
| `shortBreakDuration` | number (minutes) | `5` | `short_break_duration` |
| `longBreakDuration` | number (minutes) | `15` | `long_break_duration` |
| `longBreakInterval` | number (sessions) | `4` | `long_break_interval` |
| `autoStartBreaks` | boolean | `false` | `auto_start_breaks` |
| `autoStartFocus` | boolean | `false` | `auto_start_focus` |
| `soundEnabled` | boolean | `true` | `sound_enabled` |
| `soundVolume` | number (0–100, %) | `70` | `sound_volume` |
| `displayMode` | `"pomodoro" \| "clock"` | `"pomodoro"` | `display_mode` (fallback to `"pomodoro"` if column null/missing) |
| `showSeconds` | boolean | `false` | `show_seconds` (fallback `false`) |
| `weatherCity` | `string \| null` | `null` | `weather_city` (fallback `null`) |
| `showWeather` | boolean | `true` | `show_weather` (fallback `true`) |
| `showPomodoro` | boolean | `true` | `show_pomodoro` (fallback `true`) |
| `showTasks` | boolean | `true` | `show_tasks` (fallback `true`) |
| `showNotes` | boolean | `true` | `show_notes` (fallback `true`) |
| `cardLayout` | `CardLayout` object | see 1.2 | `card_layout` (JSON, parsed via `parseCardLayout`) |

Note: `displayMode` and `cardLayout` are not edited in the settings panel UI (they are changed elsewhere in the app), but they live in this same settings object and are persisted in the same row/upsert.

### 1.2 `CardLayout` shape and default

```
CardLayout {
  left: string[]          // card ids in left column
  right: string[]         // card ids in right column
  widths: Record<string, "full" | "half">
  collapsed: string[]     // ids of collapsed cards
}
```

Default: `left: ["timer"]`, `right: ["tasks", "notes"]`, `widths: { timer: "half", tasks: "half", notes: "half" }`, `collapsed: []`.

### 1.3 `parseCardLayout(raw)` rules (migration + validation)

- If `raw` is falsy or not an object → return the default layout.
- Legacy migration: if `raw.order` is an array AND `raw.left`/`raw.right` are both absent:
  - Filter `"clock"` out of `order`.
  - `widths` = `raw.widths` if it is an object, else default widths.
  - `collapsed` = `raw.collapsed` if array, else `[]`.
  - Distribute the remaining ordered card ids alternately: even index → `left`, odd index → `right`.
- Otherwise, field-by-field validation: `left` must be an array else default `["timer"]`; `right` must be an array else default `["tasks","notes"]`; `widths` must be an object else default; `collapsed` must be an array else `[]`.

### 1.4 Context API

`SettingsContext` exposes: `settings: Settings`, `updateSettings(partial: Partial<Settings>): Promise<void>`, `loaded: boolean` (initially `false`), `reload(): Promise<void>`.

### 1.5 Load behavior

- On provider mount and whenever the auth user changes, `loadFromDb()` runs.
- Reload suppression: `loadFromDb(force?)` returns immediately (no fetch) if `!force` and `Date.now() < suppressReloadUntilRef` — the ref is set to `now + 2000` ms on every `updateSettings` call. This prevents a background refetch from overwriting an optimistic local update for 2 seconds. `reload` (exposed on the context) is `loadFromDb` itself, so an external reload without `force=true` is also suppressed inside that window.
- If there is no logged-in user: no fetch; `loaded` becomes `true`; defaults are used.
- Query: `SELECT * FROM user_settings WHERE user_id = <uid>` single-row (`maybeSingle`). If a row exists, every field is mapped per the table in 1.1 with the listed per-column fallbacks. If no row exists, defaults stay. `loaded` set `true` afterwards in all cases.

### 1.6 Save behavior (`updateSettings`)

- Merges the partial into current settings, sets local state immediately (optimistic; UI never waits for the DB).
- Sets the 2000 ms reload-suppression window.
- If no user: stops there (settings are then session-only, NOT persisted anywhere; there is no localStorage fallback for these settings).
- If user: upserts the FULL settings row (all columns from 1.1, plus `user_id`) into `user_settings` with conflict target `user_id` (one row per user). No debounce beyond the optimistic pattern; every change writes.
- Cross-device sync: settings live in the DB keyed by user, so any device that loads/reloads gets them. There is no realtime subscription in these files; sync happens on load/reload.

---

## 2. Theme system (ThemeContext)

### 2.1 Types and options

- `ThemeId = "coral" | "ocean" | "forest" | "lavender" | "ember" | "slate"`.
- `ColorMode = "light" | "dark" | "system"`.
- Exported `THEMES: ThemeOption[]` (`{ id, name, accent }`), in this exact order with these exact swatch hexes:

| id | name | accent (swatch hex) |
|---|---|---|
| `coral` | Coral | `#f43f5e` |
| `ocean` | Ocean | `#0ea5e9` |
| `forest` | Forest | `#22c55e` |
| `lavender` | Lavender | `#a78bfa` |
| `ember` | Ember | `#f97316` |
| `slate` | Slate | `#64748b` |

- Defaults: `themeId = "coral"`, `colorMode = "system"`.

### 2.2 Persistence and precedence

- localStorage keys: `"themeId"` and `"colorMode"` (raw string values, e.g. `"ocean"`, `"dark"`).
- Initial state = localStorage value if present, else default (`coral` / `system`). This makes the theme apply instantly on page load, before auth resolves (no flash of wrong theme).
- ThemeContext has its OWN Supabase auth listener (`onAuthStateChange` + initial `getSession`) to track `userId` — it does not use SettingsContext.
- When a user id appears, `loadThemeFromDb()` reads `theme_id, color_mode` from the same `user_settings` row (`maybeSingle`). Values are validated against the allowed lists; invalid/unknown DB values fall back to `"coral"` / `"system"`. Valid values overwrite both React state AND localStorage (DB wins over localStorage for logged-in users).
- Setting a theme or mode (`setThemeId` / `setColorMode`): updates state, writes localStorage immediately, then upserts `{ user_id, theme_id, color_mode }` into `user_settings` with `onConflict: "user_id"` (both columns written on either change). If logged out, only state + localStorage change.
- Context also exposes `reload()` (= `loadThemeFromDb`) and `isDark: boolean`.

### 2.3 System mode / dark resolution

- `systemDark` initialized from `window.matchMedia("(prefers-color-scheme: dark)").matches` and kept live via a `change` listener on that media query (so switching the OS theme flips the app instantly when in System mode).
- `isDark = colorMode === "dark" || (colorMode === "system" && systemDark)`.

### 2.4 DOM application

Effect on `[themeId, isDark]`:
- `document.documentElement.setAttribute("data-theme", themeId)`.
- `document.documentElement.classList.toggle("dark", isDark)`.

Tailwind is configured with `darkMode: ["class"]`, so `.dark` on the root drives dark variants; `data-theme` selects the accent-color palette.

### 2.5 CSS variable palettes (index.css) — the actual theme values

All color vars are HSL triples consumed as `hsl(var(--x))` via the Tailwind token mapping (`background`, `foreground`, `card`, `card-foreground`, `popover`, `popover-foreground`, `primary`, `primary-foreground`, `secondary`, `secondary-foreground`, `muted`, `muted-foreground`, `accent`, `accent-foreground`, `destructive`, `destructive-foreground`, `border`, `input`, `ring`, plus a `sidebar` group defined only for coral/default). Global `--radius: 0.75rem` (Tailwind `rounded-lg = var(--radius)`, `md = radius − 2px`, `sm = radius − 4px`).

Selector scheme: light coral is `:root, [data-theme="coral"]` (coral is default + fallback); dark coral is `[data-theme="coral"].dark, :root.dark` (also the dark fallback); other themes use `[data-theme="X"]` and `[data-theme="X"].dark`.

**Coral light**: background `350 30% 97%`; foreground `350 25% 15%`; card `350 20% 99%`; card-foreground `350 25% 15%`; popover `350 20% 99%`; popover-foreground `350 25% 15%`; primary `350 91% 60%`; primary-foreground `0 0% 100%`; secondary `160 59% 45%`; secondary-foreground `0 0% 100%`; muted `350 16% 94%`; muted-foreground `350 12% 42%`; accent `350 16% 90%`; accent-foreground `350 25% 15%`; destructive `0 84% 60%`; destructive-foreground `0 0% 100%`; border `350 14% 89%`; input `350 14% 89%`; ring `350 91% 60%`; sidebar-background `350 20% 97%`; sidebar-foreground `350 15% 26%`; sidebar-primary `350 25% 15%`; sidebar-primary-foreground `0 0% 98%`; sidebar-accent `350 14% 94%`; sidebar-accent-foreground `350 25% 15%`; sidebar-border `350 14% 89%`; sidebar-ring `350 91% 60%`.

**Coral dark**: background `222 20% 10%`; foreground `210 20% 92%`; card `222 18% 14%`; card-foreground `210 20% 92%`; popover `222 18% 14%`; popover-foreground `210 20% 92%`; primary `350 91% 60%`; primary-foreground `0 0% 100%`; secondary `160 59% 45%`; secondary-foreground `0 0% 100%`; muted `222 14% 18%`; muted-foreground `215 15% 55%`; accent `222 14% 22%`; accent-foreground `210 20% 92%`; destructive `0 84% 60%`; destructive-foreground `0 0% 100%`; border `222 14% 20%`; input `222 14% 20%`; ring `350 91% 60%`; sidebar-background `222 18% 12%`; sidebar-foreground `210 20% 85%`; sidebar-primary `210 20% 92%`; sidebar-primary-foreground `222 18% 12%`; sidebar-accent `222 14% 18%`; sidebar-accent-foreground `210 20% 92%`; sidebar-border `222 14% 20%`; sidebar-ring `350 91% 60%`.

**Ocean light**: background `200 35% 96%`; foreground `210 30% 14%`; card/popover `200 30% 98%` (fg `210 30% 14%`); primary `199 89% 48%` (fg white); secondary `172 66% 50%` (fg white); muted `200 22% 92%`; muted-foreground `210 16% 44%`; accent `200 22% 88%` (fg `210 30% 14%`); destructive `0 84% 60%` (fg white); border/input `200 20% 87%`; ring `199 89% 48%`.

**Ocean dark**: background `215 28% 9%`; foreground `210 20% 92%`; card/popover `215 25% 13%` (fg `210 20% 92%`); primary `199 89% 48%` (fg white); secondary `172 66% 50%` (fg white); muted `215 20% 18%`; muted-foreground `215 15% 55%`; accent `215 18% 22%` (fg `210 20% 92%`); destructive `0 84% 60%` (fg white); border/input `215 18% 20%`; ring `199 89% 48%`.

**Forest light**: background `140 30% 96%`; foreground `150 30% 12%`; card/popover `140 25% 98%` (fg `150 30% 12%`); primary `142 71% 45%` (fg white); secondary `47 96% 53%` (fg `150 25% 14%`); muted `140 20% 92%`; muted-foreground `150 14% 40%`; accent `140 20% 87%` (fg `150 30% 12%`); destructive `0 84% 60%` (fg white); border/input `140 18% 86%`; ring `142 71% 45%`.

**Forest dark**: background `150 20% 8%`; foreground `138 16% 90%`; card/popover `150 18% 12%` (fg `138 16% 90%`); primary `142 71% 45%` (fg white); secondary `47 96% 53%` (fg `150 25% 14%`); muted `150 14% 17%`; muted-foreground `140 10% 52%`; accent `150 14% 21%` (fg `138 16% 90%`); destructive `0 84% 60%` (fg white); border/input `150 14% 19%`; ring `142 71% 45%`.

**Lavender light**: background `268 32% 96%`; foreground `270 28% 16%`; card/popover `268 28% 98%` (fg `270 28% 16%`); primary `263 70% 71%` (fg white); secondary `330 80% 65%` (fg white); muted `268 20% 92%`; muted-foreground `270 14% 43%`; accent `268 20% 88%` (fg `270 28% 16%`); destructive `0 84% 60%` (fg white); border/input `268 18% 87%`; ring `263 70% 71%`.

**Lavender dark**: background `270 22% 9%`; foreground `270 15% 90%`; card/popover `270 20% 13%` (fg `270 15% 90%`); primary `263 70% 71%` (fg white); secondary `330 80% 65%` (fg white); muted `270 16% 18%`; muted-foreground `270 12% 53%`; accent `270 16% 22%` (fg `270 15% 90%`); destructive `0 84% 60%` (fg white); border/input `270 16% 20%`; ring `263 70% 71%`.

**Ember light**: background `28 35% 96%`; foreground `20 28% 13%`; card/popover `28 30% 98%` (fg `20 28% 13%`); primary `25 95% 53%` (fg white); secondary `43 96% 56%` (fg `20 25% 15%`); muted `28 22% 91%`; muted-foreground `20 14% 41%`; accent `28 22% 87%` (fg `20 28% 13%`); destructive `0 84% 60%` (fg white); border/input `28 20% 86%`; ring `25 95% 53%`.

**Ember dark**: background `20 22% 9%`; foreground `30 18% 90%`; card/popover `20 20% 13%` (fg `30 18% 90%`); primary `25 95% 53%` (fg white); secondary `43 96% 56%` (fg `20 25% 15%`); muted `20 16% 17%`; muted-foreground `20 10% 52%`; accent `20 16% 21%` (fg `30 18% 90%`); destructive `0 84% 60%` (fg white); border/input `20 16% 19%`; ring `25 95% 53%`.

**Slate light**: background `220 20% 95%`; foreground `224 22% 16%`; card/popover `220 16% 98%` (fg `224 22% 16%`); primary `215 16% 47%` (fg white); secondary `215 25% 60%` (fg white); muted `220 18% 90%`; muted-foreground `220 12% 44%`; accent `220 18% 86%` (fg `224 22% 16%`); destructive `0 84% 60%` (fg white); border/input `220 16% 85%`; ring `215 16% 47%`.

**Slate dark**: background `224 20% 9%`; foreground `220 14% 90%`; card/popover `224 18% 13%` (fg `220 14% 90%`); primary `215 20% 55%` (fg white; note: slate is the only theme whose dark primary differs from its light primary); secondary `215 25% 60%` (fg white); muted `224 14% 17%`; muted-foreground `220 10% 52%`; accent `224 14% 21%` (fg `220 14% 90%`); destructive `0 84% 60%` (fg white); border/input `224 14% 19%`; ring `215 20% 55%`.

### 2.6 Global visual base (index.css / tailwind.config.ts)

- Fonts imported from Google Fonts: Inter (weights 400, 500, 600, 700) and JetBrains Mono (500, 700), `display=swap`. Body: `bg-background text-foreground`, antialiased, `font-family: 'Inter', sans-serif`. Tailwind `font-sans` = Inter, `font-mono` = JetBrains Mono. Utility class `.font-mono-timer` = JetBrains Mono (used by the timer display).
- Every element gets `border-color: hsl(var(--border))` (`* { @apply border-border }`).
- Utility animations: `.task-done` → `task-complete` keyframes 0.3s ease-out forwards (opacity 1 → 0.4); `.timer-pulse` → `timer-pulse` 2s ease-in-out infinite (opacity 1 → 0.85 at 50% → 1). Accordion keyframes `accordion-down`/`accordion-up` 0.2s ease-out (height 0 ↔ content height).
- Themed scrollbars, all elements: Firefox `scrollbar-width: thin; scrollbar-color: hsl(var(--muted-foreground) / 0.3) transparent`; WebKit width/height 6px, transparent track, thumb `hsl(var(--muted-foreground) / 0.25)` with full-pill radius (9999px), hover `hsl(var(--muted-foreground) / 0.4)`.
- Tailwind container: centered, padding 2rem, `2xl` screen 1400px. `tailwindcss-animate` plugin enabled (provides `animate-in`, `slide-in-from-right`, `fade-in-0`, `zoom-in-95` used below).

---

## 3. Settings panel UI (SettingsPanel)

### 3.1 Component contract

Props (all optional): `onTagsChanged?: () => void` (called after any tag edit/delete so parent lists refresh), `onSignOut?: () => void` (renders Sign out section only if provided), `pushNotifications?: { isSupported: boolean; permission: NotificationPermission | "default"; subscribed: boolean; subscribe: () => Promise<any> }` (renders Notifications section only if provided AND `isSupported`).

Consumes: `useAuth()` (user), `useSettings()`, `useTheme()`. Local state: `open` (panel visibility, default false), `tags` list, `editingTagId`, `editForm { name, color, emoji }`, `confirmDeleteId`, `cityInput` (initialized to `settings.weatherCity || ""`).

### 3.2 Closed state (trigger button)

A 9×9 (h-9 w-9) icon button, lucide `Settings` icon at h-4 w-4, `rounded-lg`, `text-muted-foreground`, hover: `bg-muted` + `text-foreground`, `transition-colors`, `aria-label="Settings"`. Clicking opens the panel.

### 3.3 Open state (overlay + panel)

- Backdrop: fixed inset-0, z-40, `bg-background/60` with `backdrop-blur-sm`; clicking it closes the panel.
- Panel: fixed right-0 top-0, z-50, full height, `w-full max-w-sm` (384px), left border, `bg-card`, `shadow-xl`, entry animation `animate-in slide-in-from-right duration-200` (slides in from the right over 200 ms).
- Header: flex row, bottom border, padding `px-6 py-4`; title `Settings` (`text-base font-semibold`); close button 8×8 with lucide `X` h-4 w-4, same muted/hover styling as trigger.
- Body: `flex-1 overflow-y-auto px-6 py-5 space-y-8` (sections stacked with 2rem gaps). Each section: `space-y-4`, header row = icon (h-4 w-4, `text-primary`) + label (`text-sm font-medium`).
- On every open: tags are (re)loaded from DB and `cityInput` is re-synced from `settings.weatherCity`.

### 3.4 Shared controls

**ToggleRow** (switch row): left column = label (`text-sm`) over description (`text-xs text-muted-foreground`); right = custom switch button h-5 w-9, rounded-full, `transition-colors`; track `bg-primary` when on, `bg-accent` when off; white knob h-4 w-4 rounded-full `shadow-sm`, offset top-0.5 left-0.5, `translate-x-4` when on / `translate-x-0` when off, `transition-transform`. Click toggles.

**DurationSlider**: header row = label (`text-xs text-muted-foreground`) left, current value right as `"{value} {unit}"` (`text-xs font-medium tabular-nums`). Native `<input type="range">` with the given min/max, styled: track w-full h-1.5 rounded-full `bg-accent`, appearance-none, cursor-pointer; thumb (WebKit + Moz) h-4 w-4 rounded-full `bg-primary` `shadow-sm`, hover scales to 110% (WebKit). Changes fire on every input change (continuous while dragging).

### 3.5 Sections, in exact top-to-bottom order

1. **Widgets** — icon lucide `Monitor`, label `Widgets`. Three ToggleRows:
   - `Pomodoro Timer` / description `Show the Pomodoro timer card` → `showPomodoro`.
   - `Tasks` / `Show the tasks card` → `showTasks`.
   - `Notes` / `Show the notes card` → `showNotes`.

2. **Clock Settings** — icon lucide `Clock`, label `Clock Settings`. One ToggleRow: `Show Seconds` / `Display seconds in the clock (HH:mm:ss)` → `showSeconds`.

3. **Weather** — icon lucide `CloudSun`, label `Weather`.
   - ToggleRow `Show Weather` / `Display weather info on the clock card` → `showWeather`.
   - Only when `showWeather` is true, a City field appears: label row = `City` (`text-xs text-muted-foreground`) left and hint `Leave empty for auto-detect` (`text-[10px] text-muted-foreground`) right. Text input, placeholder `e.g. Tokyo, London...`, styled `rounded-lg border bg-card px-3 py-1.5 text-sm`, focus ring `ring-2 ring-ring/20`. Commit semantics: value is committed on blur or on Enter (Enter also blurs); committed value = trimmed input, empty string becomes `null`; only saved if different from current `settings.weatherCity`. When `weatherCity` is set (non-null), an `Auto` button shows beside the input (lucide `MapPin` h-3 w-3 + text `Auto`, `text-xs text-muted-foreground`, hover bg-muted/text-foreground) which clears the input and sets `weatherCity: null` (i.e. back to auto-detect).

4. **Timer Durations** — RENDERED ONLY when `settings.showPomodoro` is true. Icon lucide `Clock`, label `Timer Durations`. Four DurationSliders:
   - `Focus`: min 1, max 90, unit `min` → `focusDuration`.
   - `Short Break`: min 1, max 30, unit `min` → `shortBreakDuration`.
   - `Long Break`: min 1, max 60, unit `min` → `longBreakDuration`.
   - `Long Break Every`: min 2, max 8, unit `sessions` → `longBreakInterval`.

5. **Automation** — also only when `showPomodoro` is true. Icon lucide `Zap`, label `Automation`. Two ToggleRows:
   - `Auto-start Breaks` / `Automatically start break timer after focus ends` → `autoStartBreaks`.
   - `Auto-start Focus` / `Automatically start focus timer after break ends` → `autoStartFocus`.

6. **Sound** — icon is dynamic: lucide `Volume2` in `text-primary` when `soundEnabled`, else lucide `VolumeX` in `text-muted-foreground`. Label `Sound`.
   - ToggleRow `Notification Sound` / `Play a sound when a timer session ends` → `soundEnabled`.
   - Only when `soundEnabled`: DurationSlider `Volume`, min 0, max 100, unit `%` → `soundVolume`.

7. **Tags** — RENDERED ONLY when the user has at least one tag (`tags.length > 0`). Icon lucide `Tag`, label `Tags`. Tag management (tags stored in Supabase table `tags` with columns at least `id, name, color, emoji, user_id, created_at`; loaded on panel open, filtered by `user_id`, ordered by `created_at` ascending; `emoji` is nullable string):
   - Display row per tag (`group` row, `rounded-lg px-2 py-1.5`, hover `bg-muted/50`): a pill chip `rounded-full px-2 py-0.5 text-[11px] font-medium` with `backgroundColor = tag.color + "20"` (tag color at ~12.5% alpha hex) and `color = tag.color`; shows the emoji (if any) then the name. Row actions (right-aligned, hidden until row hover via `opacity-0 group-hover:opacity-100`): edit button (lucide `Pencil` h-3 w-3) and delete button (lucide `Trash2` h-3 w-3, hover turns `text-destructive` on `bg-destructive/10`).
   - Delete confirmation is inline (two-step): clicking Trash2 swaps in two small buttons — `Delete` (`text-[10px] font-medium text-destructive`, hover `bg-destructive/10`) which deletes, and `No` (`text-[10px] text-muted-foreground`, hover bg-muted) which cancels. Delete is optimistic: tag removed from local list first, then `DELETE FROM tags WHERE id = <id>`, then `onTagsChanged?.()`.
   - Edit mode (replaces the row with a bordered card `rounded-lg border bg-background p-3`):
     - Row 1: emoji input, width w-10, centered text, placeholder `😊`, `maxLength={2}`; name input (autoFocus), flex-1; both `text-xs`, `rounded border bg-card`, focus `ring-2 ring-ring/20`. Name input: Enter saves, Escape cancels.
     - Row 2: 10 color swatch buttons, h-4 w-4 rounded-full, from the fixed palette `TAG_COLORS = ["#6366f1", "#ec4899", "#f59e0b", "#10b981", "#3b82f6", "#8b5cf6", "#ef4444", "#14b8a6", "#f97316", "#64748b"]`. Selected swatch: `scale-125 ring-2 ring-offset-1 ring-offset-background`; others scale to 110% on hover.
     - Row 3: `Save` button (`text-[10px] font-medium text-primary`, `bg-primary/10`, hover `bg-primary/20`) and `Cancel` button (`text-[10px] text-muted-foreground`, hover bg-muted).
     - Save validation: no-op if name (trimmed) is empty. Saved values: trimmed name, chosen color, trimmed emoji or `null` if empty. Optimistic local update, then `UPDATE tags SET ... WHERE id = <id>`, then `onTagsChanged?.()`.
   - There is no tag creation in the settings panel (tags are created elsewhere; the section hides entirely when the list is empty — this doubles as the empty state).

8. **Appearance** — icon lucide `Palette`, label `Appearance`.
   - Sub-label `Theme` (`text-xs text-muted-foreground mb-2`). Grid `grid-cols-3 gap-1.5` of 6 theme buttons (Coral, Ocean, Forest, Lavender, Ember, Slate in that order). Each button: vertical stack (`flex-col items-center gap-1.5`), `rounded-lg px-2 py-2 text-[11px]`; contains a color dot h-5 w-5 rounded-full filled with the theme's accent hex, `ring-2 ring-offset-2 ring-offset-card`; selected → ring `ring-foreground/40` and button `bg-accent font-medium text-accent-foreground`; unselected → `ring-transparent`, `text-muted-foreground`, hover `bg-muted text-foreground`. Click → `setThemeId(id)` (applies instantly).
   - Sub-label `Mode` (`text-xs text-muted-foreground mb-2`). Row `flex gap-1` of 3 equal-width segmented buttons: `Light` (lucide `Sun`), `Dark` (lucide `Moon`), `System` (lucide `Monitor`); icons h-3.5 w-3.5; button `flex-1 rounded-lg px-2 py-1.5 text-[11px]`; selected/unselected styling identical to theme buttons. Click → `setColorMode(id)` (applies instantly).

9. **Notifications** — RENDERED ONLY when `pushNotifications?.isSupported`. Icon lucide `Bell`, label `Notifications`. Two states:
   - Not enabled (`permission !== "granted"` OR not `subscribed`): full-width bordered button, lucide `BellOff` h-4 w-4 + text `Enable push notifications`, `text-sm text-muted-foreground`, hover bg-muted/text-foreground; click calls `pushNotifications.subscribe()`.
   - Enabled (granted AND subscribed): static full-width pill `bg-primary/10 text-primary text-sm`, lucide `Bell` h-4 w-4 + text `Push notifications enabled`.

10. **Sign Out** — RENDERED ONLY when `onSignOut` prop given. Section separated by top border (`pt-2 border-t`). Full-width button: lucide `LogOut` h-4 w-4 + text `Sign out`, `text-sm text-muted-foreground`, hover `bg-destructive/10 text-destructive`; click calls `onSignOut()`.

### 3.6 Panel behavior notes

- Every settings change is applied immediately (optimistic) and persisted via `updateSettings` per change; there is no Save/Apply button and no dirty state.
- No toasts/snackbars anywhere in this panel; errors from Supabase calls are silently ignored (no error UI).
- Closing: backdrop click or the X header button. No Escape-key handler for the panel itself (Escape only cancels tag-name editing).

---

## 4. ThemeToggle (header quick theme popover)

A standalone header control duplicating the Appearance controls:

- Trigger: 8×8 (h-8 w-8) icon button, lucide `Palette` h-4 w-4, `rounded-lg text-muted-foreground`, hover `bg-muted text-foreground`, `aria-label="Theme settings"`. Click toggles a dropdown.
- Dropdown: absolutely positioned `right-0 top-full mt-2`, z-50, `w-56` (224px), `rounded-xl border bg-card p-3 shadow-lg`, entry animation `animate-in fade-in-0 zoom-in-95 duration-150` (fade + zoom from 95% over 150 ms).
- Closes on any mousedown outside the component (document-level listener against a wrapper ref). Clicking a theme/mode does NOT close it (user can try several).
- Contents:
  - Heading `Theme` (`text-[11px] font-medium text-muted-foreground mb-2 px-1`), then the same 6-theme `grid-cols-3 gap-1.5` swatch grid as in the settings panel (identical markup/classes/behavior, `mb-3` below).
  - Divider (`border-t pt-2`), heading `Appearance` (same heading style — note the popover calls the mode picker "Appearance" while the settings panel calls it "Mode"), then the same 3-button Light/Dark/System segmented row.
- Uses the exact same `setThemeId`/`setColorMode`, so changes here also persist to localStorage + DB and stay in sync with the settings panel.

---

## 5. NavLink (utility component)

`src/components/NavLink.tsx` is a small compatibility wrapper around react-router-dom's `NavLink`: props extend `NavLinkProps` minus `className`, adding optional `className`, `activeClassName`, `pendingClassName` strings. It renders the router NavLink with a `className` function that merges (via the `cn` class-merge util): base `className`, plus `activeClassName` when the link `isActive`, plus `pendingClassName` when `isPending`. Forwards ref to the anchor; `displayName = "NavLink"`. No visual styling of its own. For the KMP rebuild this maps to: a navigation link wrapper that appends an "active" style set when the current route matches, and a "pending" style set while the route is loading.

---

## 6. Persistence summary (for Firebase mapping)

| Data | Where stored | Key/row | When written | When read |
|---|---|---|---|---|
| All settings (1.1) | DB table `user_settings`, one row per user (`onConflict: user_id`), full-row upsert | `user_id` | On every individual setting change | On login/mount; refetches suppressed for 2000 ms after any local write |
| `themeId` | localStorage `"themeId"` AND `user_settings.theme_id` | — / `user_id` | On every theme change (both immediately) | localStorage at boot (instant paint); DB after auth resolves, DB value overwrites localStorage |
| `colorMode` | localStorage `"colorMode"` AND `user_settings.color_mode` | — / `user_id` | Same as themeId (theme + mode are upserted together) | Same as themeId |
| Tags | DB table `tags` (`id, user_id, name, color, emoji, created_at`) | per-tag rows | On tag edit (update) / delete, optimistic-first | On each settings-panel open, `ORDER BY created_at ASC` |

Anonymous/offline behavior: theme + mode work fully via localStorage; all other settings are in-memory defaults only (lost on reload) since there is no localStorage fallback for the `Settings` object; tags require a user.

Validation/fallback rules to replicate exactly: theme id must be one of the 6 valid ids else `coral`; color mode one of `light|dark|system` else `system`; missing DB columns fall back per the table in 1.1; `card_layout` goes through `parseCardLayout` (including the legacy `order` migration); weather city trims and null-ifies empty; tag name must be non-empty after trim; tag emoji max length 2 characters, null when empty.
