# Area: timer-clock — Exhaustive Requirements (Focusly → Dayboard rebuild)

Sources read completely: `src/components/PomodoroTimer.tsx`, `src/components/ClockDisplay.tsx`, `src/hooks/usePushNotifications.ts`, `public/sw.js`, plus essential referenced files: `src/contexts/SettingsContext.tsx`, `src/pages/Index.tsx`, `src/components/SettingsPanel.tsx`, `src/index.css`, `index.html`, `supabase/functions/ip-location/index.ts`, `supabase/functions/push-notifications/index.ts`, and all 14 SQL migrations.

---

## 1. Pomodoro Timer — State Machine

### 1.1 Modes
- `TimerMode` enum: `"focus" | "shortBreak" | "longBreak"`.
- User-facing labels (`MODE_LABELS`): `focus` → `"Focus"`, `shortBreak` → `"Short Break"`, `longBreak` → `"Long Break"`.
- Component state: `mode` (default `"focus"`), `timeLeft: number | null` (default `null` = not yet loaded), `isRunning` (default `false`), `completedSessions` (default `0`), `loaded` (default `false`).

### 1.2 Durations (seconds, derived from settings which are stored in minutes)
- `focus` = `settings.focusDuration * 60`
- `shortBreak` = `settings.shortBreakDuration * 60`
- `longBreak` = `settings.longBreakDuration * 60`

### 1.3 Defaults and ranges (from `SettingsContext` defaults + `SettingsPanel` slider bounds; also DB column defaults match)
| Setting | Default | Min | Max | Unit (slider label) |
|---|---|---|---|---|
| `focusDuration` | 25 | 1 | 90 | `min` |
| `shortBreakDuration` | 5 | 1 | 30 | `min` |
| `longBreakDuration` | 15 | 1 | 60 | `min` |
| `longBreakInterval` | 4 | 2 | 8 | `sessions` |
| `autoStartBreaks` | false | — | — | toggle |
| `autoStartFocus` | false | — | — | toggle |
| `soundEnabled` | true | — | — | toggle |
| `soundVolume` | 70 | 0 | 100 | `%` |
All sliders are native `<input type="range">` step 1 (implicit).

### 1.4 Tick loop
- Runs only when `loaded === true` and `isRunning === true`.
- `window.setInterval(..., 1000)`; each tick decrements `timeLeft` by 1.
- When current value `<= 1`: clear interval, set `isRunning = false`, set `timeLeft = 0`, then after `setTimeout(..., 50)` call `handleTimerComplete()`.
- When `isRunning` becomes false the interval is cleared; effect cleanup also clears it.

### 1.5 Completion / Skip logic (`handleTimerComplete` — identical for natural zero and Skip button)
1. Play the completion sound (see §1.9).
2. Call `onTimerEnd?.(...)` with the human string of the mode that just ended: `"Focus session"` (focus), `"Short break"` (shortBreak), `"Long break"` (longBreak). Parent uses this to send a push notification (see §6.4).
3. If ending mode was `focus`:
   - `next = completedSessions + 1`; set `completedSessions = next`.
   - If `next >= settings.longBreakInterval`: reset `completedSessions` to 0 and switch to `longBreak` (passing sessions=0), auto-start iff `settings.autoStartBreaks`.
   - Else: switch to `shortBreak` (passing sessions=next), auto-start iff `settings.autoStartBreaks`.
4. If ending mode was a break (short or long): switch to `focus` (sessions unchanged), auto-start iff `settings.autoStartFocus`.

IMPORTANT edge case: pressing **Skip during a focus session still counts it as a completed session** (increments the dot counter) and still plays the sound and fires the notification.

### 1.6 `switchMode(newMode, sessions?, autoStart?)`
- Clears the interval, sets `mode = newMode`, `timeLeft = full duration of newMode`, `isRunning = autoStart ?? false`, and immediately persists state (§1.11) with `sessions ?? current completedSessions`.
- Clicking a **mode tab** calls `switchMode(m)` with no extra args → timer resets to that mode's full duration, stopped, session count preserved. No confirmation dialog; switching while running just discards remaining time.

### 1.7 Manual controls
- **Play/Pause toggle** (`toggleRunning`): flips `isRunning`, saves state with current `timeLeft`.
- **Reset** (`handleReset`): clears interval, `timeLeft = full duration of current mode`, `isRunning = false`, saves. Session count NOT reset.
- **Skip**: calls `handleTimerComplete()` directly (§1.5).

### 1.8 Settings-change behavior
- When any of `focusDuration` / `shortBreakDuration` / `longBreakDuration` changes (compared against previous values via refs, only after initial load completed): clear interval, set `timeLeft` to the NEW full duration of the CURRENT mode, `isRunning = false`, save state. I.e., changing any duration resets and pauses the current timer.
- Changing `longBreakInterval` does NOT reset the timer; it only changes the number of session dots rendered.

### 1.9 Completion sound (Web Audio API, no audio files)
- Skipped entirely if `settings.soundEnabled === false`.
- New `AudioContext`; a `GainNode` with `gain.value = settings.soundVolume / 100` connected to destination.
- Two sine-wave oscillator beeps: frequencies `440` Hz and `660` Hz; beep `i` starts at `ctx.currentTime + i * 0.25` s and stops at start + `0.2` s (so: 440 Hz for 0.2 s, then 660 Hz starting at 0.25 s for 0.2 s).
- Whole thing wrapped in try/catch (AudioContext may be unavailable) — silent failure.

### 1.10 Session counting & dots
- `completedSessions` counts finished focus sessions in the current cycle; resets to 0 when the long break is triggered (`next >= longBreakInterval`).
- UI shows `settings.longBreakInterval` dots; dot `i` is filled (`bg-primary`) when `i < completedSessions`, otherwise `bg-accent`. Dots: `rounded-full transition-colors`, size `h-2.5 w-2.5` (normal) / `h-3.5 w-3.5` (expanded); container `flex gap-2` (normal) / `gap-3` (expanded).

### 1.11 Persistence (Supabase table `timer_state`, one row per user)
Schema (migration `20260313095051`):
```sql
CREATE TABLE public.timer_state (
  id UUID PK DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
  mode TEXT NOT NULL DEFAULT 'focus',
  time_left INTEGER NOT NULL DEFAULT 1500,
  is_running BOOLEAN NOT NULL DEFAULT false,
  completed_sessions INTEGER NOT NULL DEFAULT 0,
  last_tick_at TIMESTAMPTZ,          -- nullable
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()  -- auto-updated by trigger
);
```
RLS: user can SELECT/INSERT/UPDATE own row (no DELETE policy). Table is in the `supabase_realtime` publication.

Write rules:
- Saves happen ONLY on explicit actions: play/pause toggle, reset, mode-tab click, completion/skip (via `switchMode`), and the duration-settings-change reset. There is NO periodic per-tick save (explicitly removed).
- Save is an `upsert` with `onConflict: "user_id"`, fields: `user_id, mode, time_left, is_running, completed_sessions, last_tick_at` where `last_tick_at = new Date().toISOString()` if running else `null`.
- Save is debounced through a `window.setTimeout(..., 50)` (50 ms; a new save cancels a pending one).
- Guards: no save until initial load fully applied (flag set inside `requestAnimationFrame` after load); before each save, set `ignoringRealtimeUntil = Date.now() + 2000` so the device ignores realtime echoes of its own save for 2 s.

Read/restore rules (`loadTimerState`, runs once per user id, only after settings loaded):
- `SELECT mode, time_left, is_running, completed_sessions, last_tick_at ... WHERE user_id = ... maybeSingle()`.
- If a row exists: `tl = time_left`; if `is_running && last_tick_at != null`, subtract wall-clock elapsed: `tl = max(0, tl - floor((now - last_tick_at)/1000))`. Set `mode`, `timeLeft = tl`, `isRunning = is_running && tl > 0`, `completedSessions`.
- If no row: `timeLeft = focus default duration` (from settings), other state stays default.
- Parent (`Index`) can pass a `reloadRef` so the timer state can be reloaded on demand (used by header refresh button which calls `reloadAll`; note: `reloadRef` is NOT wired for the timer in the current Index — only tasks/notes get reloadRefs there; the timer prop exists but Index passes `onTimerEnd` and `expanded` only).

Tab close / refresh behavior (consequence of the above):
- Closing/refreshing mid-run loses nothing conceptually: on next load the elapsed time since the last explicit-action save (`last_tick_at`) is subtracted, so the countdown effectively "ran" in the background.
- If the timer would have hit zero while the tab was closed: it restores as `timeLeft = 0`, `isRunning = false`, in the SAME mode. No completion fires — no sound, no notification, no mode advance, no session increment. The user sees 00:00 and must act (skip/reset/tab).
- There is no `beforeunload` handler and no localStorage persistence for the timer. All persistence is the Supabase row.

### 1.12 Cross-device realtime sync
- Subscribes to Supabase realtime channel named `timer_state_${user.id}`, postgres_changes `event: 'UPDATE'`, `schema: 'public'`, `table: 'timer_state'`, `filter: user_id=eq.<uid>`. Only after `loaded`.
- On event: skip if within own-echo window (2 s, §1.11). Recompute `tl` from payload with the same `last_tick_at` elapsed-subtraction. `shouldRun = is_running && tl > 0`.
- Anti-jump rule: if local is running AND remote should run AND same mode, ignore the update when `|localTimeLeft - remoteTl| <= 3` seconds ("close enough").
- Otherwise apply remote `mode`, `timeLeft`, `isRunning=shouldRun`, `completedSessions` wholesale.
- Channel removed on unmount/user change.

### 1.13 Loading state (skeleton) — shown while `timeLeft === null`
- Container `flex flex-col items-center gap-8`.
- Static (non-clickable) mode tab pills: labels `"Focus"`, `"Short Break"`, `"Long Break"` in a `flex gap-1 rounded-lg bg-muted p-1` container; each `rounded-md px-3 py-1.5 text-sm font-medium text-muted-foreground`.
- Ring: SVG `h-56 w-56 -rotate-90` viewBox `0 0 200 200`, only the track circle (`cx=100 cy=100 r=90 fill=none stroke=hsl(var(--accent)) strokeWidth=6`).
- Centered overlay: `--:--` in `font-mono-timer text-5xl font-bold text-muted-foreground/30`; below it label `"Loading"` in `mt-1 text-sm font-medium text-muted-foreground`.
- Spacer divs `h-2.5` (dots placeholder) and `h-14` (controls placeholder).

---

## 2. Pomodoro Timer — Visual Spec

### 2.1 Layout
- Root: `flex flex-col items-center`, gap `gap-8` normal / `gap-12` expanded. Order top→bottom: mode tabs, timer circle, session dots, controls.

### 2.2 Mode tabs
- Container: `flex gap-1 rounded-lg bg-muted p-1`.
- Each tab button: `rounded-md px-3 py-1.5 text-sm font-medium transition-colors`.
- Active tab: `bg-primary text-primary-foreground` when current mode is focus; `bg-secondary text-secondary-foreground` when current mode is a break (note: the ACTIVE tab color depends on the current mode being focus or not, applied to whichever tab is active).
- Inactive tabs: `text-muted-foreground hover:text-foreground`.

### 2.3 Progress ring
- SVG viewBox `0 0 200 200`, class `-rotate-90 transition-all` (progress starts at 12 o'clock, sweeps clockwise).
- Sizes: normal `h-56 w-56` (14 rem); expanded `h-80 w-80` and `lg:h-[28rem] lg:w-[28rem]`.
- Track circle: `cx=100 cy=100 r=90 fill=none stroke="hsl(var(--accent))" strokeWidth=6`.
- Progress circle: same geometry, `strokeWidth=6 strokeLinecap="round"`, stroke = `hsl(var(--primary))` in focus mode, `hsl(var(--secondary))` in break modes; `strokeDasharray = 2π·90 ≈ 565.4867`; `strokeDashoffset = 2π·90 · (1 − progress/100)`; class `transition-all duration-1000 ease-linear` (1 s linear tween per tick).
- `progress = timeLeft == null ? 0 : clamp(0, 100, ((totalTime − timeLeft) / totalTime) · 100)` where `totalTime` = full duration of current mode. Ring is empty at start and FILLS as time elapses.

### 2.4 Time text (centered absolutely over the ring)
- `MM:SS`, both zero-padded 2 digits: `String(minutes).padStart(2,"0") : String(seconds).padStart(2,"0")`; minutes = `floor(timeLeft/60)`, seconds = `timeLeft % 60` (minutes can exceed 59 for durations ≥ 60 min, still just padded, e.g. `90:00`).
- Classes: `font-mono-timer font-bold`, size `text-5xl` normal / `text-7xl lg:text-8xl` expanded, `transition-all`.
- While running, class `timer-pulse`: CSS keyframes `timer-pulse` — opacity 1 → 0.85 at 50% → 1, `2s ease-in-out infinite`.
- Below it the mode label (`MODE_LABELS[mode]`): `mt-1 font-medium text-muted-foreground`, `text-sm` normal / `text-lg` expanded.

### 2.5 Controls (lucide icons)
- Row: `flex items-center gap-3` normal / `gap-5` expanded.
- Reset button: icon `RotateCcw`, `aria-label="Reset"`; circle `h-10 w-10` normal / `h-14 w-14` expanded; `rounded-full text-muted-foreground transition-colors hover:bg-muted hover:text-foreground`; icon `h-4 w-4` / `h-6 w-6` expanded.
- Play/Pause button (center, primary CTA): icon `Play` (with `ml-0.5` optical offset) when paused, `Pause` when running; `aria-label` `"Start"`/`"Pause"`; circle `h-14 w-14` normal / `h-20 w-20` expanded; background `bg-primary` (focus) or `bg-secondary` (break), text `text-primary-foreground`; `transition-all hover:scale-105 active:scale-95`; icon `h-6 w-6` / `h-9 w-9` expanded.
- Skip button: icon `SkipForward`, `aria-label="Skip"`; same styling/sizes as Reset.

### 2.6 Typography / tokens
- `.font-mono-timer` = `'JetBrains Mono', monospace` (Google Fonts import, weights 500/700). Body font Inter (400/500/600/700).
- Colors are HSL CSS variables per theme (6 themes: coral default, ocean, forest, lavender, ember, slate; each with light+dark). For default coral light: `--primary: 350 91% 60%` (coral red), `--secondary: 160 59% 45%` (green), `--accent: 350 16% 90%`, `--muted: 350 16% 94%`, `--muted-foreground: 350 12% 42%`, `--background: 350 30% 97%`, `--card: 350 20% 99%`, radius `0.75rem`.

### 2.7 Card container (from Index)
- The timer renders inside a card titled `"Pomodoro"` (CARD_LABELS: clock→"Clock", timer→"Pomodoro", tasks→"Tasks", notes→"Notes"). Card: `rounded-2xl border bg-card shadow-sm`, header with collapse chevron + drag handle (`GripVertical`), maximize (`Maximize2`)/minimize (`Minimize2`) buttons; content padding `px-6 pb-6`; the timer card gets `centerContent` (content column centered).
- Timer card visibility gated by `settings.showPomodoro` (toggle "Pomodoro Timer" / "Show the Pomodoro timer card"). When hidden, the Timer Durations and Automation settings sections are also hidden.
- Expanded mode: card rendered `fixed inset-4 z-40` above a `fixed inset-0 z-30 bg-background/60 backdrop-blur-sm` backdrop (click backdrop to close); timer receives `expanded=true` (bigger ring/text/gaps per above).

---

## 3. Document Title
- **The document title is never updated at runtime.** Verified by repo-wide code search for `document.title`: 0 occurrences. No remaining-time-in-title behavior exists.
- Static title from `index.html`: `Focusly` (for Dayboard: `Dayboard`). Meta description: "A distraction-free Pomodoro timer with a built-in to-do list to help you stay focused and productive." Favicon `/favicon.png`, apple-touch-icon `/apple-touch-icon.png`.

---

## 4. Clock Display (`ClockDisplay`)

### 4.1 Time
- `new Date()` refreshed via `setInterval(..., 1000)` (1 s).
- **Always 24-hour format** — there is NO 12/24h setting. `hours = String(now.getHours()).padStart(2,"0")`, same for minutes and seconds.
- Rendered as `HH:MM`; if `settings.showSeconds` is true, appends `:SS` in a smaller, muted span.
- Main time classes: `font-mono-timer font-bold tracking-tight`; size `text-5xl sm:text-6xl` normal, `text-5xl sm:text-7xl md:text-[8rem] leading-none` expanded. Seconds span: `text-muted-foreground`, `text-3xl sm:text-4xl` normal / `text-3xl sm:text-5xl md:text-6xl` expanded.
- Uses the device's local timezone implicitly (JS `Date`); no timezone setting.

### 4.2 Date
- `now.toLocaleDateString("en-US", { weekday: "long", month: "long", day: "numeric" })` → e.g. `"Monday, August 24"`. No year.
- Classes: `mt-2 text-muted-foreground text-sm` normal / `text-sm sm:text-base md:text-xl` expanded.

### 4.3 Settings
- `showSeconds`: default false; SettingsPanel toggle label `"Show Seconds"`, description `"Display seconds in the clock (HH:mm:ss)"` under section `"Clock Settings"` (Clock icon).
- Layout: root `flex flex-col items-center gap-6` normal / `gap-10` expanded; clock block `flex flex-col items-center`.
- The Clock card (title `"Clock"`) is ALWAYS visible (not toggleable), always rendered at the top of the page, fixed position in layout, NOT draggable; it is collapsible and expandable like other cards.
- Note: `Settings.displayMode: "pomodoro" | "clock"` (default `"pomodoro"`, DB column `display_mode`) exists in the settings model but is not used by any current UI.

### 4.4 Weather (part of the clock card)
- Gated by `settings.showWeather` (default true; toggle "Show Weather" / "Display weather info on the clock card" under section "Weather" with CloudSun icon). Turning it off clears weather state.
- City resolution order:
  1. If `settings.weatherCity` set (text input, placeholder `"e.g. Tokyo, London..."`, hint `"Leave empty for auto-detect"`, commits on blur/Enter, trims, empty→null; an `Auto` button with MapPin icon clears it): forward geocode via `https://geocoding-api.open-meteo.com/v1/search?name=<encoded city>&count=1`; take `results[0]` latitude/longitude/name; if no results, abort silently (weather hidden).
  2. Else IP-based via Supabase edge function `ip-location` (see §4.6). 
  3. Else browser geolocation (`navigator.geolocation.getCurrentPosition`, `timeout: 5000` ms); reverse-geocode city name via `https://geocoding-api.open-meteo.com/v1/search?name=&latitude=<lat>&longitude=<lon>&count=1`, fallback city label `"Your location"`. If geolocation unavailable/denied → abort silently.
- Weather fetch: `https://api.open-meteo.com/v1/forecast?latitude=<lat>&longitude=<lon>&current_weather=true`; uses `current_weather.temperature` (rounded with `Math.round`) and `current_weather.weathercode`.
- Refresh interval: every `10 * 60 * 1000` ms (10 min), plus immediately on mount/setting change. All failures silent (weather non-critical).
- Loading UI (only while loading AND no data yet): pill `flex items-center rounded-xl bg-muted/50 gap-3 px-4 py-2.5` with `Loader2` icon `h-4 w-4 animate-spin text-muted-foreground` and text `"Loading weather..."` (`text-xs text-muted-foreground`).
- Weather pill (when data): `rounded-xl bg-muted/50`, padding `gap-3 px-4 py-2.5` normal (expanded scales up to `sm:gap-4 sm:px-5 sm:py-3 md:gap-5 md:px-6 md:py-4`). Contents: weather icon (`text-primary`, `h-5 w-5` normal, up to `md:h-8 md:w-8` expanded); `Thermometer` icon (`text-muted-foreground h-3.5 w-3.5`, expanded up to `md:h-5 md:w-5`) + temperature `"{temp}°C"` (`font-medium text-sm`, expanded up to `md:text-xl`); `MapPin` icon (`text-muted-foreground h-3 w-3`, expanded up to `md:h-4 md:w-4`) + city name (`text-muted-foreground text-xs`, expanded up to `md:text-base`).

### 4.5 WMO weather-code → lucide icon map (fallback `CloudSun` for unmapped codes)
- `0,1` → Sun; `2` → CloudSun; `3,45,48` → Cloud; `51,53,55,56,57` → CloudDrizzle; `61,63,65,66,67,80,81,82` → CloudRain; `71,73,75,77,85,86` → CloudSnow; `95,96,99` → CloudLightning.

### 4.6 `ip-location` edge function (server-side, avoids CORS/leaking client)
- Invoked from the client via `supabase.functions.invoke("ip-location")` (POST, no body). CORS `*`.
- Tries `https://ipapi.co/json/` → responds `{ lat: data.latitude, lon: data.longitude, city: data.city || "Your location" }`.
- On failure falls back to `https://ip-api.com/json/?fields=lat,lon,city` → `{ lat, lon, city: city || "Your location" }`.
- On both failing: `{ error: message }` with HTTP 500. Client treats any error/missing lat+lon as null (silent).

---

## 5. Push / Local Notification Flow

### 5.1 Client hook `usePushNotifications`
- State: `permission` (initial: `Notification.permission` if `Notification` defined, else `"default"`), `subscribed` (initial false), a `subscriptionRef`.
- `isSupported = typeof window !== "undefined" && "serviceWorker" in navigator && "PushManager" in window`.
- `getVapidKey()`: fetches `GET {VITE_SUPABASE_URL}/functions/v1/push-notifications?action=vapid-key` with header `apikey: {VITE_SUPABASE_PUBLISHABLE_KEY}`; returns `json.publicKey` or null. (Note: the code also contains a dead/no-op `supabase.functions.invoke("push-notifications", ...)` call before the fetch whose result is discarded — do not reimplement it.)
- `subscribe()` flow (returns boolean):
  1. Requires `user` and `session`; requires SW+PushManager support; else return false.
  2. `Notification.requestPermission()`; store result; if not `"granted"` return false.
  3. `navigator.serviceWorker.register("/sw.js")`; await `navigator.serviceWorker.ready`.
  4. Fetch VAPID public key; if null log `"Failed to get VAPID key"` and return false.
  5. Convert key with url-base64 → Uint8Array (standard padding + `-`→`+`, `_`→`/`, atob).
  6. `registration.pushManager.getSubscription()`; if none, `subscribe({ userVisibleOnly: true, applicationServerKey })`.
  7. POST subscription to `{VITE_SUPABASE_URL}/functions/v1/push-notifications?action=subscribe` with headers `Content-Type: application/json`, `Authorization: Bearer {session.access_token}`, `apikey`; body `{ endpoint, p256dh, auth }` from `subscription.toJSON()`.
  8. Set `subscribed = true`, return true. Any error: log `"Push subscription error:"`, return false.
- `sendNotification(title, body)`: requires session; POST `?action=send` (same headers) body `{ title, body }`; errors logged `"Send notification error:"`, silent to user.
- Auto-subscribe effect: on every render where `user && session && permission === "granted" && !subscribed` → call `subscribe()` (so returning users with previously granted permission silently re-register/re-subscribe at login/page load).
- **No local (non-push) `new Notification(...)` fallback exists.** The only end-of-timer notification path is the server push; the audible beep (§1.9) is the local cue.

### 5.2 Settings UI for notifications (SettingsPanel, section "Notifications", Bell icon; only when `isSupported`)
- If `permission !== "granted"` OR not subscribed: full-width outlined button, `BellOff` icon, label `"Enable push notifications"` → calls `subscribe()`.
- Else: static pill `bg-primary/10 text-primary` with `Bell` icon, text `"Push notifications enabled"`.

### 5.3 Timer-end wiring (Index)
- `handleTimerEnd(modeName)` → `sendNotification(title, body)` with:
  - title: `` `${modeName} complete!` `` → `"Focus session complete!"`, `"Short break complete!"`, `"Long break complete!"`.
  - body: `modeName.includes("Focus")` → `"Great work! Time for a break."`; otherwise `"Break's over — time to focus!"`.
- Fires on natural completion AND on Skip. Sent to ALL of the user's registered devices (server fan-out), including the device that finished the timer.

### 5.4 Service worker `public/sw.js`
- `push` event: parse `event.data.json()` (default `{}`); `title = data.title || 'Timer Complete'`; options: `body: data.body || 'Your session has ended!'`, `icon: '/favicon.png'`, `badge: '/favicon.png'`, `tag: 'timer-notification'` (so notifications replace each other), `renotify: true`, `vibrate: [200, 100, 200]`, `data: { url: self.location.origin }`. Shows via `self.registration.showNotification` inside `event.waitUntil`.
- `notificationclick`: close the notification; find all window clients (`type: 'window', includeUncontrolled: true`); focus the first whose URL includes the origin; otherwise `clients.openWindow(url)` where `url = event.notification.data?.url || '/'`.

### 5.5 Server: `push-notifications` edge function
- CORS `*`. Actions via query param `action`.
- `GET ?action=vapid-key` (no auth): returns `{ publicKey }`. VAPID keys are generated on first call (Web Crypto ECDSA P-256; public exported raw, base64url; private = JWK `d`) and persisted in table `push_config` (`key text PRIMARY KEY, value text NOT NULL`; keys `vapid_public_key`, `vapid_private_key`; RLS enabled with NO public policies — service-role only).
- All POST actions require `Authorization: Bearer <jwt>`; user resolved via `auth.getUser`; else 401 `{ error: "Unauthorized" }`.
- `POST ?action=subscribe`: upsert into `push_subscriptions` `{ user_id, endpoint, p256dh, auth }`, `onConflict: "user_id,endpoint"`. Returns `{ ok: true }`.
- `POST ?action=unsubscribe`: delete by user_id+endpoint. Returns `{ ok: true }`. (Currently never called by the client — there is no unsubscribe UI.)
- `POST ?action=send` body `{ title, body }`: loads all of the user's subscriptions; if none → `{ sent: 0 }`. Otherwise Web Push to each endpoint: payload `JSON.stringify({ title, body })` encrypted with `aes128gcm` (ECDH P-256 + HKDF-SHA-256 per RFC 8291, random 16-byte salt, record delimiter byte `2`), headers `Content-Type: application/octet-stream`, `Content-Encoding: aes128gcm`, `TTL: "2419200"` (4 weeks), `Urgency: "high"`, and VAPID auth header `vapid t=<ES256 JWT>, k=<publicKey>` with JWT claims `{ aud: endpoint origin, exp: now + 12h, sub: "mailto:noreply@focusly.app" }`. Sends in parallel (`Promise.allSettled`); any subscription returning HTTP `410 Gone` is deleted from `push_subscriptions`. Returns `{ sent: <success count>, total: <subscription count> }`.
- Unknown action → 400 `{ error: "Unknown action" }`; unexpected errors → 500 `{ error: message }`.
- `push_subscriptions` schema: `id uuid PK, user_id uuid NOT NULL, endpoint text NOT NULL, p256dh text NOT NULL, auth text NOT NULL, created_at timestamptz DEFAULT now(), UNIQUE(user_id, endpoint)`; RLS: user manages own rows (FOR ALL).

---

## 6. Related settings persistence (`user_settings` table, one row per user)
Columns relevant to this area with DB defaults: `focus_duration int 25`, `short_break_duration int 5`, `long_break_duration int 15`, `long_break_interval int 4`, `auto_start_breaks bool false`, `auto_start_focus bool false`, `sound_enabled bool true`, `sound_volume int 70`, `display_mode text 'pomodoro'`, `show_seconds bool false`, `weather_city text NULL`, `show_weather bool true`, `show_pomodoro bool true`. Settings save via upsert `onConflict: "user_id"` of the FULL settings object on every change; local state updates immediately (optimistic) with a 2 s self-echo suppression window; table is in the realtime publication and Index reloads settings on remote change debounced 300 ms. The Pomodoro timer waits for `settingsLoaded` before loading its own state (so durations are correct when computing the default `timeLeft`).

SettingsPanel section labels/strings for this area: sections `"Timer Durations"` (Clock icon), `"Automation"` (Zap icon), `"Sound"` (Volume2/VolumeX icon), `"Clock Settings"` (Clock icon), `"Weather"` (CloudSun icon), `"Notifications"` (Bell icon). Slider rows: `"Focus"`, `"Short Break"`, `"Long Break"`, `"Long Break Every"`, `"Volume"` — value shown right-aligned as `"{value} {unit}"` in `tabular-nums`. Toggle rows: `"Auto-start Breaks"` / "Automatically start break timer after focus ends"; `"Auto-start Focus"` / "Automatically start focus timer after break ends"; `"Notification Sound"` / "Play a sound when a timer session ends" (Volume slider only visible when sound enabled).

---

## 7. Behavior summary / edge cases checklist for the rebuild
1. Timer must not tick, render times, or save before initial DB load (skeleton with `--:--` / `"Loading"` until then).
2. Skip = full completion semantics (sound + notification + session count + auto-start rules).
3. Focus session completing when `completedSessions + 1 >= longBreakInterval` goes to LONG break and zeroes the counter; otherwise SHORT break carrying the incremented counter.
4. Auto-start: breaks auto-start only if `autoStartBreaks`; focus after any break auto-starts only if `autoStartFocus`; manual mode-tab switches never auto-start.
5. Saves only on explicit actions with `last_tick_at`; restore subtracts elapsed wall time; expired-while-away sessions restore to 00:00 paused with NO completion side effects.
6. Realtime cross-device sync with 2 s own-echo suppression and 3 s drift tolerance when both devices run the same mode.
7. Changing any duration setting resets + pauses the current timer to the new full duration.
8. Progress ring fills clockwise from top; 1 s linear CSS transition per tick; color primary (focus) / secondary (breaks); track uses accent.
9. Clock: strict 24-hour, optional seconds, en-US long date, 1 s refresh, device timezone.
10. Weather auto-location order: manual city → IP (ipapi.co, then ip-api.com, via server function) → browser geolocation (5 s timeout); 10 min refresh; °C only; all failures silent.
11. Push: VAPID keys server-generated and stored in DB; subscription per (user, endpoint); send fans out to all devices; 410 subscriptions pruned; SW notification tag `timer-notification` with renotify and vibration `[200,100,200]`; clicking focuses an existing app window or opens one.
12. No document-title countdown; title is static.
13. Sound is two-tone (440→660 Hz sine, 0.2 s each, 0.25 s apart) generated with Web Audio at `soundVolume/100` gain; suppressed when sound disabled.
