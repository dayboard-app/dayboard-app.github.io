# Firebase setup

The live state of the Dayboard Firebase project, so the console and this repo cannot drift.
Created 2026-08-24 under the personal Google account `bachanamosulishvili@gmail.com` (the same
account that owns the `dakalebi` project).

## Project

| | |
|---|---|
| Display name | Dayboard |
| Project ID | `dayboard-app` |
| Project number | `333709728827` |
| Plan | **Spark (no-cost)** — deliberately not upgraded; see "Not enabled" below |
| Console | https://console.firebase.google.com/project/dayboard-app |

## Web app config

Registered web app nickname **Dayboard Web**, app ID `1:333709728827:web:1ca7949f2ab638b70d901e`.

```js
apiKey:            "AIzaSyD2hagNO0moNun79fje7tmBmH1h9bvnv4c"
authDomain:        "dayboard-app.firebaseapp.com"
projectId:         "dayboard-app"
storageBucket:     "dayboard-app.firebasestorage.app"
messagingSenderId: "333709728827"
appId:             "1:333709728827:web:1ca7949f2ab638b70d901e"
```

These are **publishable client values**, not secrets — Firebase documents them as safe to ship in
client code, exactly like the original app's `VITE_SUPABASE_PUBLISHABLE_KEY`. The security boundary
is [firestore.rules](firestore.rules), not the config. They belong in the committed source so the
GitHub Pages build needs no secret injection.

## Enabled

- **Authentication → Email/Password**: enabled. "Email link (passwordless sign-in)" left **off**, matching
  the original's email+password-only flow.
- **Authorized domains**: the three defaults (`localhost`, `dayboard-app.firebaseapp.com`,
  `dayboard-app.web.app`) plus **`dayboard-app.github.io`**. Without that last entry Firebase Auth
  rejects sign-in from the deployed site, so it must stay.
- **Cloud Firestore**: `(default)` database, Standard edition, location **`europe-west3` (Frankfurt)**.
  Chosen as the closest full-featured European region; the location is permanent and cannot be moved.
  Created in production mode (deny-all) and then given the rules in [firestore.rules](firestore.rules).

## Not enabled, on purpose

- **Google Analytics** — the original app had no analytics, so the project was created without it.
- **Gemini in Firebase** — declined at creation; its terms allow prompts to be used for model training.
- **Firebase Hosting** — deployment is GitHub Pages (see `.github/workflows/deploy.yml`), so the web app
  was registered without it.
- **Blaze plan / Cloud Functions / FCM send** — phase 2 only. Cross-device push needs a callable
  function, which needs Blaze. Phase 1 uses local notifications instead (REQUIREMENTS.md §11).
- **Storage** — Dayboard stores no files.

## Deploying rules

`firestore.rules` is the source of truth. It was published through the console Rules editor. To
publish from a machine instead:

```bash
firebase deploy --only firestore:rules --project dayboard-app
```
