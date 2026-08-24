# Dayboard

A distraction-free board: a Pomodoro timer, a clock with weather, tasks with
subtasks and tags, and notes. Everything syncs across devices behind email and
password sign-in.

Kotlin Multiplatform and Compose HTML, on Firebase, deployed as a static bundle to
[dayboard-app.github.io](https://dayboard-app.github.io).

It is a rebuild of [Focusly](https://github.com/bchmsl/focusly), matching its
behaviour and its look. What the original does is written down in
[REQUIREMENTS.md](REQUIREMENTS.md), how this is put together in
[ARCHITECTURE.md](ARCHITECTURE.md), and the order it was built in in
[PLAN.md](PLAN.md).

## What is in it

- **Clock** — 24-hour, optional seconds, with the weather where you are or in a
  city you name.
- **Pomodoro timer** — focus and break stretches, session dots, a progress ring,
  a completion chime and a notification. The countdown is measured against the
  clock rather than counted, so it survives a background tab and a reload.
- **Tasks** — subtasks, tags, inline formatting, drag reordering, and a separate
  group for what is finished.
- **Notes** — the same tags and formatting, with a two-line preview in the list.
- **Settings** — six themes in light and dark, timer durations, sound, widgets,
  and tag management. Everything applies as you change it.

## Running it

Needs a JDK. **21 or newer** if you want the Firebase emulators, which refuse to
start on anything older.

```bash
./gradlew jsBrowserDistribution
```

The bundle lands in `build/dist/js/productionExecutable`. Serve that directory
with anything; there is no server side.

```bash
cd build/dist/js/productionExecutable && python3 -m http.server 8000
```

## Working against the emulators

Anything served from `localhost` talks to the local Firebase emulators instead of
the real project, so development never touches real accounts or real data. The
switch is a hostname check ([`Environment.kt`](shared/src/commonMain/kotlin/io/github/dayboard/core/Environment.kt)),
and a test pins that the deployed hostname can never match it.

```bash
npx --yes firebase-tools emulators:start --only auth,firestore --project dayboard-app
```

That reads [`firebase.json`](firebase.json) and serves Auth on 9099, Firestore on
8080, and a UI on [localhost:4000](http://localhost:4000). Firestore runs the
real [`firebase/firestore.rules`](firebase/firestore.rules), so a rule that would
reject a query in production rejects it here too.

Sign-up sends a confirmation email, and the app refuses to sign in an account
that has not confirmed one. The emulator does not send mail — it queues the link,
which you can collect and follow:

```bash
curl -s http://localhost:9099/emulator/v1/projects/dayboard-app/oobCodes
```

Everything the emulators hold disappears when they stop.

## Tests

```bash
./gradlew :shared:allTests          # runs on the JVM and on Node
./gradlew :shared:koverHtmlReport   # coverage, at shared/build/reports/kover/html
```

Everything worth testing lives in `:shared` and needs no browser: the timer's
state machine, the reorder rules, the formatting parser, the routing guard. The
UI is verified by hand against the emulators, and each pull request records what
was checked.

`:shared` carries a JVM target purely so Kover can measure it, since Kover cannot
instrument Kotlin/JS.

## Layout

| Path | What is in it |
|---|---|
| `shared/` | The domain, the rules, and the pure logic. No Firebase, no DOM, no clock. |
| `src/jsMain/` | The web app: Compose HTML, the browser and Firebase adapters, the composition root. |
| `firebase/` | Security rules, and a record of how the project is configured. |
| `docs/source-analysis/` | What the original does, extracted file by file. The reference while building. |
| `tools/` | Generators for the icon set and the lucide catalogue. |

Deploys happen on every push to `main`, via
[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml). A failing test
stops the deploy.
