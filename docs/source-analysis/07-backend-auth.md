# Backend & Auth — Focusly (Supabase) — Exhaustive Requirements + Firebase Mapping for Dayboard

Sources read (all fetched from `bchmsl/focusly@main`): `supabase/config.toml`, `src/integrations/supabase/client.ts`, `src/integrations/supabase/types.ts`, `src/pages/Auth.tsx`, `src/contexts/AuthContext.tsx`, `src/App.tsx` (auth guard), `src/hooks/usePushNotifications.ts` (edge-function client contract), `src/components/ClockDisplay.tsx` (ip-location caller, excerpt), both edge functions, and all 14 migrations in timestamp order.

---

## 1. Supabase project configuration (`supabase/config.toml`)

```toml
project_id = "devbpmxbyxsfrbrvbowo"

[functions.push-notifications]
verify_jwt = false

[functions.ip-location]
verify_jwt = false
```

- Both edge functions are deployed with `verify_jwt = false` (callable without a Supabase JWT at the gateway level; `push-notifications` does its own token validation for POST actions, see §6.2).

## 2. Supabase client setup (`src/integrations/supabase/client.ts`)

- Created with `createClient<Database>(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, options)`.
- URL and key come from Vite env vars: `VITE_SUPABASE_URL`, `VITE_SUPABASE_PUBLISHABLE_KEY`.
- Auth options (exact): `storage: localStorage`, `persistSession: true`, `autoRefreshToken: true`.
- Consequence: the session (access + refresh token) is persisted in `localStorage` under the standard supabase-js key (`sb-<project-ref>-auth-token`), survives reloads, and refreshes automatically.

## 3. Auth flow

### 3.1 Providers
- **Email + password only.** No OAuth, no magic link, no phone, no anonymous/guest mode anywhere in the codebase. No `signInAnonymously`, no third-party provider buttons.

### 3.2 AuthContext (`src/contexts/AuthContext.tsx`)
- Context shape: `{ user: User | null, session: Session | null, loading: boolean, signOut: () => Promise<void> }`. Defaults: `user: null`, `session: null`, `loading: true`, `signOut: async () => {}`.
- On mount:
  1. Subscribes to `supabase.auth.onAuthStateChange((_event, session) => ...)` — sets `session`, sets `user = session?.user ?? null`, sets `loading = false`.
  2. Also calls `supabase.auth.getSession()` once and applies the same state updates (covers the initial restore-from-localStorage case).
  3. Unsubscribes on unmount.
- `signOut()` just calls `supabase.auth.signOut()` (state clears via the listener).

### 3.3 Route guarding (`src/App.tsx`)
- Routes: `/auth` → Auth page; `/` → `ProtectedRoute` wrapping `SettingsProvider` + `Index`; `*` → NotFound.
- `ProtectedRoute` behavior:
  - While `loading` is true: renders `<div className="min-h-screen bg-background" />` (a blank full-screen background, no spinner).
  - If not loading and `user` is null: `<Navigate to="/auth" replace />`.
  - Otherwise renders children.
- Provider nesting order: `QueryClientProvider > ThemeProvider > AuthProvider > TooltipProvider > (Toaster, Sonner) > BrowserRouter`.

### 3.4 Auth page (`src/pages/Auth.tsx`) — exact behavior and strings
- Single page toggling between Login and Sign-up modes via local state `isLogin` (default `true` = login mode).
- If `user` becomes non-null (already signed in / just signed in), `useEffect` navigates to `/` with `replace: true`.
- **Sign in**: `supabase.auth.signInWithPassword({ email, password })`. On error, shows `error.message` from Supabase verbatim (e.g. "Invalid login credentials"). On success no explicit navigate — the auth listener sets `user` and the effect redirects.
- **Sign up**: `supabase.auth.signUp({ email, password, options: { emailRedirectTo: window.location.origin } })`. On error shows `error.message`; on success shows the message: `Check your email for a confirmation link!` (user stays on the page). Email confirmation is the default Supabase flow: the account requires clicking the emailed link, which redirects to the site origin.
- Submitting sets `loading = true`, clears `error` and `message` first; `loading = false` after the call.
- **UI states**:
  - Submit button disabled while `loading` (`disabled:opacity-50`); shows a spinning `Loader2` lucide icon (`h-4 w-4 animate-spin`) instead of the label while loading.
  - Error text: `<p class="text-sm text-destructive">` under the inputs. Success message: `<p class="text-sm text-secondary">`.
- **Validation**: email input `type="email" required`; password input `type="password" required minLength={6}` (browser-native validation; 6 is also Supabase's default min password length).
- **Exact strings**:
  - App title next to `Timer` lucide icon (`h-6 w-6 text-primary`): `Focusly` (Dayboard replaces the brand name).
  - Login heading: `Welcome back`; subtext: `Sign in to sync your tasks & timer`.
  - Sign-up heading: `Create account`; subtext: `Sign up to save your progress`.
  - Placeholders: `Email`, `Password` (with `Mail` and `Lock` lucide icons, `h-4 w-4 text-muted-foreground`, absolutely positioned left-3, vertically centered inside the input).
  - Submit button label: `Sign in` / `Sign up`, followed by an `ArrowRight` lucide icon (`h-4 w-4`).
  - Toggle line: `Don't have an account?` / `Already have an account?` followed by a link-styled button `Sign up` / `Sign in` (`text-primary font-medium hover:underline`). Clicking it flips mode and clears both `error` and `message`.
- **Layout/visuals**: full-screen `min-h-screen bg-background`, centered, content column `max-w-sm` with `px-4`; logo row centered with `gap-2 mb-8`; card `rounded-2xl border bg-card p-6 shadow-sm`; headings `text-lg font-semibold mb-1` and `text-sm text-muted-foreground mb-6`; form `flex flex-col gap-4`; inputs `w-full rounded-lg border bg-background pl-10 pr-4 py-2.5 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring/20`; submit `flex items-center justify-center gap-2 rounded-lg bg-primary py-2.5 text-sm font-medium text-primary-foreground transition-all hover:bg-primary/90`.

---

## 4. Final effective database schema (after all 14 migrations in order)

Migration order applied: 20260313095051 → 20260313105212 → 20260313105342 → 20260313111558 → 20260313112147 → 20260313112644 → 20260313172350 → 20260314070219 → 20260315075312 → 20260315080327 → 20260315083333 → 20260315083946 → 20260315091503 → 20260315093543.

All tables in schema `public`. All have RLS ENABLED. No explicit `CREATE INDEX` anywhere; the only indexes are the implicit ones from PRIMARY KEY and UNIQUE constraints.

### 4.1 `tasks`
| column | type | constraints / default |
|---|---|---|
| id | uuid | PK, default `gen_random_uuid()` |
| user_id | uuid | NOT NULL, FK → `auth.users(id)` ON DELETE CASCADE |
| text | text | NOT NULL |
| done | boolean | NOT NULL, default `false` |
| subtitle | text | nullable |
| body | text | nullable |
| position | integer | NOT NULL, default `0` |
| parent_id | uuid | nullable, FK → `public.tasks(id)` ON DELETE CASCADE (self-reference; subtasks cascade-delete with parent) |
| created_at | timestamptz | NOT NULL, default `now()` |
| updated_at | timestamptz | NOT NULL, default `now()` (auto-maintained by trigger) |

- Migration 2 backfilled `position` with `ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at)` (positions start at 1 for pre-existing rows; default for new rows is 0 — the app sets position explicitly).
- Trigger: `update_tasks_updated_at` BEFORE UPDATE FOR EACH ROW → `update_updated_at_column()`.
- RLS (4 separate policies): "Users can view their own tasks" SELECT USING `auth.uid() = user_id`; "Users can create their own tasks" INSERT WITH CHECK `auth.uid() = user_id`; "Users can update their own tasks" UPDATE USING `auth.uid() = user_id`; "Users can delete their own tasks" DELETE USING `auth.uid() = user_id`.

### 4.2 `timer_state` (one row per user)
| column | type | constraints / default |
|---|---|---|
| id | uuid | PK, default `gen_random_uuid()` |
| user_id | uuid | NOT NULL, UNIQUE, FK → `auth.users(id)` ON DELETE CASCADE |
| mode | text | NOT NULL, default `'focus'` |
| time_left | integer | NOT NULL, default `1500` (seconds = 25 min) |
| is_running | boolean | NOT NULL, default `false` |
| completed_sessions | integer | NOT NULL, default `0` |
| last_tick_at | timestamptz | nullable |
| updated_at | timestamptz | NOT NULL, default `now()` (trigger-maintained) |

- Trigger: `update_timer_state_updated_at` BEFORE UPDATE → `update_updated_at_column()`.
- RLS: SELECT / INSERT / UPDATE own-row policies only ("Users can view their own timer", "Users can create their own timer", "Users can update their own timer", all `auth.uid() = user_id`). **No DELETE policy** — clients cannot delete timer rows.

### 4.3 `user_settings` (one row per user)
| column | type | constraints / default |
|---|---|---|
| id | uuid | PK, default `gen_random_uuid()` |
| user_id | uuid | NOT NULL, UNIQUE (**no FK** to auth.users) |
| focus_duration | integer | NOT NULL, default `25` (minutes) |
| short_break_duration | integer | NOT NULL, default `5` |
| long_break_duration | integer | NOT NULL, default `15` |
| long_break_interval | integer | NOT NULL, default `4` |
| auto_start_breaks | boolean | NOT NULL, default `false` |
| auto_start_focus | boolean | NOT NULL, default `false` |
| sound_enabled | boolean | NOT NULL, default `true` |
| sound_volume | integer | NOT NULL, default `70` |
| theme_id | text | NOT NULL, default `'coral'` |
| color_mode | text | NOT NULL, default `'system'` |
| display_mode | text | NOT NULL, default `'pomodoro'` |
| show_seconds | boolean | NOT NULL, default `false` |
| weather_city | text | nullable, default `NULL` |
| show_pomodoro | boolean | NOT NULL, default `true` |
| show_tasks | boolean | NOT NULL, default `true` |
| show_notes | boolean | NOT NULL, default `true` |
| show_weather | boolean | NOT NULL, default `true` |
| card_layout | jsonb | NOT NULL, default `'{"order":["clock","timer","tasks","notes"],"widths":{"clock":"full","timer":"half","tasks":"half","notes":"full"},"collapsed":[]}'` |
| created_at | timestamptz | NOT NULL, default `now()` |
| updated_at | timestamptz | NOT NULL, default `now()` (trigger-maintained) |

- Trigger: `update_user_settings_updated_at` BEFORE UPDATE → `update_updated_at_column()`.
- RLS: SELECT / INSERT / UPDATE own-row policies ("Users can view own settings", "Users can insert own settings", "Users can update own settings", all `auth.uid() = user_id`). **No DELETE policy.**

### 4.4 `push_subscriptions`
| column | type | constraints / default |
|---|---|---|
| id | uuid | PK, default `gen_random_uuid()` |
| user_id | uuid | NOT NULL (no FK) |
| endpoint | text | NOT NULL |
| p256dh | text | NOT NULL |
| auth | text | NOT NULL |
| created_at | timestamptz | NOT NULL, default `now()` |
| — | — | UNIQUE(user_id, endpoint) |

- RLS: single policy "Users can manage own subscriptions" FOR ALL USING `auth.uid() = user_id` WITH CHECK `auth.uid() = user_id`. (In practice writes go through the edge function with the service role.)

### 4.5 `push_config` (server-only key/value store)
| column | type | constraints |
|---|---|---|
| key | text | PRIMARY KEY |
| value | text | NOT NULL |

- RLS enabled with **zero policies** → completely inaccessible to clients; only the service role (edge function) can read/write. Holds two rows: `vapid_public_key`, `vapid_private_key` (see §6.2).

### 4.6 `tags`
| column | type | constraints / default |
|---|---|---|
| id | uuid | PK, default `gen_random_uuid()` |
| user_id | uuid | NOT NULL (no FK) |
| name | text | NOT NULL |
| color | text | NOT NULL, default `'#6366f1'` |
| emoji | text | nullable |
| created_at | timestamptz | NOT NULL, default `now()` |
| — | — | UNIQUE(user_id, name) — tag names unique per user |

- RLS: "Users manage own tags" FOR ALL USING/WITH CHECK `auth.uid() = user_id`.
- No updated_at column, no trigger.

### 4.7 `task_tags` (junction)
| column | type | constraints |
|---|---|---|
| task_id | uuid | NOT NULL, FK → tasks(id) ON DELETE CASCADE |
| tag_id | uuid | NOT NULL, FK → tags(id) ON DELETE CASCADE |
| — | — | PRIMARY KEY (task_id, tag_id) |

- RLS: "Users manage own task_tags" FOR ALL, USING and WITH CHECK both: `EXISTS (SELECT 1 FROM public.tasks WHERE tasks.id = task_tags.task_id AND tasks.user_id = auth.uid())` — ownership is derived through the task row.

### 4.8 `notes`
| column | type | constraints / default |
|---|---|---|
| id | uuid | PK, default `gen_random_uuid()` |
| user_id | uuid | NOT NULL (no FK) |
| title | text | NOT NULL |
| body | text | nullable |
| position | integer | NOT NULL, default `0` |
| created_at | timestamptz | NOT NULL, default `now()` |
| updated_at | timestamptz | NOT NULL, default `now()` (trigger-maintained) |

- Trigger: `update_notes_updated_at` BEFORE UPDATE → `update_updated_at_column()`.
- RLS: 4 policies ("Users can view own notes" SELECT, "Users can create own notes" INSERT, "Users can update own notes" UPDATE, "Users can delete own notes" DELETE), all `auth.uid() = user_id`.

### 4.9 `note_tags` (junction)
| column | type | constraints |
|---|---|---|
| note_id | uuid | NOT NULL, FK → notes(id) ON DELETE CASCADE |
| tag_id | uuid | NOT NULL, FK → tags(id) ON DELETE CASCADE |
| — | — | PRIMARY KEY (note_id, tag_id) |

- RLS: "Users manage own note_tags" FOR ALL, USING/WITH CHECK via `EXISTS (... notes.id = note_tags.note_id AND notes.user_id = auth.uid())`.

### 4.10 Shared function + triggers
```sql
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = public;
```
- Attached BEFORE UPDATE FOR EACH ROW on: `tasks`, `timer_state`, `user_settings`, `notes`. Effect: `updated_at` is always server-set on any update.

### 4.11 Realtime publication
- Migration 20260314070219 added to `supabase_realtime` publication: `tasks`, `task_tags`, `tags`, `timer_state`, `user_settings`.
- Migration 20260315083333 added `notes`.
- Migration 20260315093543 idempotently re-adds `tags`, `task_tags`, `note_tags`, `timer_state`, `user_settings` (DO block catching `duplicate_object`) — this is where `note_tags` first actually joins the publication.
- **Final realtime set (all 7 client tables except push_config/push_subscriptions... final list):** `tasks`, `task_tags`, `tags`, `timer_state`, `user_settings`, `notes`, `note_tags`. NOT in publication: `push_subscriptions`, `push_config`.

---

## 5. What is stored where / when (backend-relevant persistence)
- Session: `localStorage` via supabase-js (persistSession). Read on app boot by `getSession()`; refreshed automatically.
- All user data (tasks, notes, tags, junctions, timer state, settings) lives in Postgres, one owner per row (`user_id = auth.uid()`), synced live via Postgres Changes over the realtime publication above.
- VAPID keys: `push_config` table, generated lazily on first `vapid-key` request.
- Web Push subscriptions: `push_subscriptions`, upserted by the edge function on subscribe, deleted on unsubscribe or when a push returns HTTP 410.

---

## 6. Edge functions

### 6.1 `ip-location` (`supabase/functions/ip-location/index.ts`)
- Runtime: Deno (`Deno.serve`). No auth (verify_jwt=false, no token check). No secrets used.
- CORS: `Access-Control-Allow-Origin: *`; `Access-Control-Allow-Headers: authorization, x-client-info, apikey, content-type, x-supabase-client-platform, x-supabase-client-platform-version, x-supabase-client-runtime, x-supabase-client-runtime-version`. OPTIONS → 204-style empty response with CORS headers.
- Logic (any method):
  1. `fetch("https://ipapi.co/json/")`. If `res.ok`, respond 200 JSON: `{ lat: data.latitude, lon: data.longitude, city: data.city || "Your location" }`.
  2. On any failure, fallback: `fetch("https://ip-api.com/json/?fields=lat,lon,city")` → 200 JSON `{ lat: data.lat, lon: data.lon, city: data.city || "Your location" }`.
  3. If fallback also throws: 500 JSON `{ error: <message> }`.
- Purpose: server-side IP geolocation (the caller's IP as seen by the function) used by the weather feature. Caller: `src/components/ClockDisplay.tsx` → `supabase.functions.invoke("ip-location")`; result used only if `data.lat && data.lon`; silent fail returns null (browser geolocation is the other source).

### 6.2 `push-notifications` (`supabase/functions/push-notifications/index.ts`)
- Runtime: Deno; imports `@supabase/supabase-js@2.49.4` from esm.sh. Creates an **admin client** from env secrets `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` (auto-injected Supabase function secrets; no custom secrets).
- CORS: origin `*`; allowed headers `authorization, x-client-info, apikey, content-type`. OPTIONS handled.
- Routing by query param `action`:

**GET `?action=vapid-key`** (no auth):
- `getOrCreateVapidKeys`: reads `push_config` rows with keys `vapid_public_key`/`vapid_private_key`; if both exist, returns them. Otherwise generates an ECDSA P-256 key pair via WebCrypto, exports public key raw → base64url string, private key JWK → stores the `d` parameter as the private key string; upserts both rows into `push_config`. Response 200: `{ "publicKey": "<base64url>" }`.

**All POST actions**: require `Authorization` header; token extracted via `authHeader.replace("Bearer ", "")` and validated with `supabaseAdmin.auth.getUser(token)`. Missing header or invalid token → 401 `{ "error": "Unauthorized" }`.

**POST `?action=subscribe`** body `{ endpoint, p256dh, auth }`:
- Upsert into `push_subscriptions` `{ user_id, endpoint, p256dh, auth }` with `onConflict: "user_id,endpoint"`. Response 200 `{ "ok": true }`.

**POST `?action=unsubscribe`** body `{ endpoint }`:
- Delete `push_subscriptions` where `user_id` = caller and `endpoint` matches. Response 200 `{ "ok": true }`.

**POST `?action=send`** body `{ title, body }`:
- Loads VAPID keys (creating if needed), selects all of the caller's subscriptions (`endpoint, p256dh, auth`).
- If none: 200 `{ "sent": 0 }`.
- Sends to every subscription in parallel (`Promise.allSettled`) implementing the raw **Web Push protocol** (RFC 8291 aes128gcm):
  - Payload = `JSON.stringify({ title, body })`, encrypted with ECDH P-256 + HKDF-SHA256 + AES-128-GCM (aes128gcm content encoding, random 16-byte salt, padding delimiter byte `2`).
  - VAPID auth header: ES256 JWT with `aud` = endpoint origin, `exp` = now + 12 hours, `sub` = `mailto:noreply@focusly.app`; header `authorization: vapid t=<jwt>, k=<publicKey>`.
  - POST to the subscription endpoint with headers `Content-Type: application/octet-stream`, `Content-Encoding: aes128gcm`, `TTL: 2419200` (4 weeks), `Urgency: high`.
- Cleanup: any result with HTTP status **410** (Gone/expired) → delete that subscription row.
- Response 200 `{ "sent": <count of successful>, "total": <subscription count> }`.

**Unknown action** → 400 `{ "error": "Unknown action" }`. Any thrown error → 500 `{ "error": <message> }` (also `console.error("Edge function error:", ...)`).

### 6.3 Client contract for push (`src/hooks/usePushNotifications.ts`)
- `getVapidKey()`: fetches `GET {VITE_SUPABASE_URL}/functions/v1/push-notifications?action=vapid-key` with only the `apikey` header (publishable key). (There is a dead `supabase.functions.invoke` call before it whose result is ignored.) Returns `json.publicKey` or null.
- `subscribe()`: requires `user` and `session`; feature-gated on `"serviceWorker" in navigator && "PushManager" in window`. Flow: `Notification.requestPermission()` → must be `"granted"`; register service worker `/sw.js` and await `navigator.serviceWorker.ready`; get VAPID key, convert base64url → `Uint8Array`; reuse existing `pushManager.getSubscription()` or `subscribe({ userVisibleOnly: true, applicationServerKey })`; POST the subscription (`endpoint`, `keys.p256dh`, `keys.auth`) to `?action=subscribe` with headers `Content-Type: application/json`, `Authorization: Bearer <session.access_token>`, `apikey: <publishable key>`. Sets `subscribed = true`.
- `sendNotification(title, body)`: POST `?action=send` with the same auth headers and body `{ title, body }`. Fire-and-forget with console.error on failure.
- Auto-subscribe effect: when `user && session && permission === "granted" && !subscribed`, calls `subscribe()` (i.e. silently re-subscribes returning users who already granted permission).
- Exposes `{ permission, subscribed, subscribe, sendNotification, isSupported }`.

---

## 7. Firebase mapping for Dayboard (KMP Compose for Web + Firebase)

### 7.1 Supabase Auth → Firebase Auth
- Provider: **Email/Password** only (enable just that provider). No Google/anonymous sign-in.
- `signInWithPassword` → `signInWithEmailAndPassword`. `signUp` with `emailRedirectTo: origin` → `createUserWithEmailAndPassword` + `sendEmailVerification(actionCodeSettings { url = window.location.origin })`, then show the same message `Check your email for a confirmation link!`.
- **Behavior difference to handle**: Supabase (default config) blocks sign-in until the email is confirmed and returns an error message on login attempts; Firebase signs users in immediately with `emailVerified=false`. For a 1:1 clone either gate the app on `user.emailVerified` (sign out + show an equivalent error if unverified), or accept the difference and let unverified users in. Decide explicitly; the original UX is "cannot use the app until confirmed".
- Session persistence: supabase-js localStorage persistence + autoRefresh → Firebase `browserLocalPersistence` (the default on web) with automatic token refresh. `onAuthStateChange` + `getSession` → a single `onAuthStateChanged` listener (fires immediately with the restored user), mapping to the same `{ user, loading }` guard: blank `min-h-screen` div while loading, redirect to `/auth` when signed out, redirect to `/` when signed in.
- `signOut()` → `Firebase auth.signOut()`.
- Password min length 6 matches Firebase's default minimum — keep `minLength=6` on the field.
- Error strings: Supabase error messages (e.g. "Invalid login credentials") will differ from Firebase codes (`auth/invalid-credential`); map Firebase error codes to short human-readable strings shown in the same red error slot.

### 7.2 Tables → Firestore collections
Recommended layout (per-user subcollections make ownership rules trivial). All server timestamps via `FieldValue.serverTimestamp()`.

- `tasks` → `users/{uid}/tasks/{taskId}` fields: `text: string`, `done: bool` (default false), `subtitle: string|null`, `body: string|null`, `position: number` (default 0), `parentId: string|null` (task doc id), `createdAt: timestamp`, `updatedAt: timestamp`. (`user_id` becomes the path segment.)
- `notes` → `users/{uid}/notes/{noteId}` fields: `title: string`, `body: string|null`, `position: number`, `createdAt`, `updatedAt`.
- `tags` → `users/{uid}/tags/{tagId}` fields: `name: string`, `color: string` (default `#6366f1`), `emoji: string|null`, `createdAt`. Enforce per-user unique `name` in app logic or by using a normalized name as the doc id (Firestore has no unique constraints).
- `task_tags` / `note_tags` junctions → **no clean Firestore equivalent**; store `tagIds: array<string>` directly on each task/note document (simplest and realtime-friendly), or mirror as subcollections. Array field is the recommended clone approach.
- `timer_state` → single doc `users/{uid}/state/timer` fields: `mode: string` (default `"focus"`), `timeLeft: number` (default 1500), `isRunning: bool` (default false), `completedSessions: number` (default 0), `lastTickAt: timestamp|null`, `updatedAt`. (Supabase UNIQUE(user_id) → one fixed doc id.)
- `user_settings` → single doc `users/{uid}/state/settings` with all 19 setting fields and their exact defaults from §4.3, including `cardLayout` as a map: `{order:["clock","timer","tasks","notes"], widths:{clock:"full",timer:"half",tasks:"half",notes:"full"}, collapsed:[]}`.
- `push_subscriptions` → replaced by FCM tokens: `users/{uid}/fcmTokens/{token}` fields: `token: string`, `createdAt`. (Doc id = token gives the UNIQUE(user_id, endpoint) semantics for free.)
- `push_config` → **not needed** with FCM (Firebase manages Web Push VAPID keys; you configure one Web Push certificate key pair in the Firebase console and pass the public key to `getToken`). If you replicate raw Web Push instead, keep keys in Secret Manager / functions config, never in a client-readable collection.

### 7.3 RLS → Firestore security rules (plain words)
- Everything under `users/{uid}/**`: allow read and write only when `request.auth != null && request.auth.uid == uid`. This reproduces every own-row policy (`auth.uid() = user_id`) in one rule.
- Junction EXISTS policies (task_tags/note_tags) disappear because tag links live on the owner's own task/note docs, already covered by the path rule.
- `push_config` had zero policies (service-role only) → with FCM nothing to protect; if kept, rule: `allow read, write: if false;` (Admin SDK bypasses rules, like the service role bypasses RLS).
- Supabase's missing DELETE policies on `timer_state` and `user_settings` (clients cannot delete them) → optionally add `allow delete: if false;` on `users/{uid}/state/{doc}` for exact parity; low impact either way since the app never deletes them.
- Firestore rules cannot easily reproduce "no FK" vs "FK to auth.users" distinctions; irrelevant for behavior.

### 7.4 Edge functions → Cloud Functions
- `ip-location` → HTTPS Cloud Function (onRequest, CORS `*`, unauthenticated / `allow-unauthenticated`): call `https://ipapi.co/json/` first, fall back to `https://ip-api.com/json/?fields=lat,lon,city`, return `{lat, lon, city}` with the `"Your location"` fallback string, 500 `{error}` on double failure. Caveat: on Cloud Functions the outbound request carries the **server's** IP, not the user's, so a 1:1 port must geolocate the caller instead — read the client IP from `X-Forwarded-For` / `req.ip` and call `https://ipapi.co/{ip}/json/` (or `http://ip-api.com/json/{ip}?fields=lat,lon,city`). (The Supabase version has the same conceptual flaw — ipapi.co/json geolocates the edge runtime's egress IP — so matching behavior means "some IP-based coarse location", which the X-Forwarded-For approach does better.)
- `push-notifications` → mostly **replaced by FCM**, which eliminates all the hand-rolled crypto:
  - `action=vapid-key` → unnecessary; the FCM Web Push public key is a static config value shipped with the client (`getToken(messaging, { vapidKey })`). No key generation, no `push_config`.
  - `action=subscribe` → client obtains an FCM token via `getToken` (service worker `firebase-messaging-sw.js` replaces `/sw.js`) and writes it to `users/{uid}/fcmTokens/{token}` directly (rules already restrict to owner); no function needed. Keep the same gating: Notification permission must be `granted`; auto-resubscribe on login when permission is already granted.
  - `action=unsubscribe` → delete the token doc (+ `deleteToken`).
  - `action=send` → callable Cloud Function `sendPush({title, body})` (callable gives you verified `context.auth.uid`, replacing the manual Bearer-token check): read all of the caller's tokens, `admin.messaging().sendEachForMulticast({tokens, notification/webpush payload {title, body}})`, delete tokens that come back `messaging/registration-token-not-registered` (the analog of the 410 cleanup), return `{sent, total}` (and `{sent: 0}` when no tokens).
  - Match delivery params where FCM allows: webpush headers TTL `2419200`, `Urgency: high`.
- Secrets: `SUPABASE_SERVICE_ROLE_KEY` → not needed; the Admin SDK inside Cloud Functions has implicit admin credentials.

### 7.5 Realtime → Firestore listeners
- Supabase Postgres Changes on `tasks`, `notes`, `tags`, `task_tags`, `note_tags`, `timer_state`, `user_settings` → Firestore `onSnapshot` listeners (KMP: GitLive firebase-firestore snapshot Flows) on `users/{uid}/tasks`, `users/{uid}/notes`, `users/{uid}/tags`, `users/{uid}/state/timer`, `users/{uid}/state/settings`. Junction listeners vanish (tagIds arrays ride along with their parent docs). No publication/config step exists in Firestore; listeners are the default. Firestore also gives offline cache + latency compensation for free (better than the original; keep writes going through the same code path).

### 7.6 Things with no clean Firebase equivalent (handle explicitly)
1. **`ON DELETE CASCADE`** (auth.users → tasks/timer_state; tasks → subtasks via parent_id; tasks/notes/tags → junction rows): Firestore never cascades. Reproduce in app code: deleting a task must also delete its subtasks (query `parentId == taskId`); deleting a tag must remove its id from every task/note `tagIds` array (batched `arrayRemove`); account deletion should use the "Delete User Data" Firebase Extension or an `auth.onDelete` Cloud Function that wipes `users/{uid}`.
2. **DB triggers for `updated_at`**: no BEFORE UPDATE triggers. Set `updatedAt: serverTimestamp()` on every client write (or a Firestore `onWrite` function, but client-side serverTimestamp is the practical clone).
3. **Unique constraints**: `UNIQUE(user_id, name)` on tags and `UNIQUE(user_id, endpoint)` on subscriptions. Firestore has none; emulate with deterministic doc ids (token as id; optionally slugified tag name as id) or a pre-write duplicate check in a transaction.
4. **SQL upsert `onConflict`**: use `set(..., merge)` on a deterministic doc id.
5. **Position backfill with ROW_NUMBER**: not applicable to a fresh app; new items get explicit positions from the client (Supabase default was 0).
6. **RLS "enabled with no policies" (push_config)**: nearest analog is `allow read, write: if false` — but with FCM the table disappears entirely.
7. **Server-generated VAPID keys stored in DB**: replaced by Firebase console Web Push certificates; note this in config, not code.
8. **Email-confirmation-gated login** (Supabase default): Firebase requires manual enforcement via `emailVerified` (see §7.1).
