# Architecture

How Dayboard is put together, and why. For what it does, see
[REQUIREMENTS.md](REQUIREMENTS.md); for the order it was built in, [PLAN.md](PLAN.md).

## The one rule

**Anything that decides something lives in `:shared`. Anything that touches the
outside world lives in `src/jsMain`.**

`:shared` has no DOM, no Firebase, no clock it can read and no network it can
reach. That is not tidiness for its own sake — it is what makes the interesting
parts testable without a browser or an account. The pomodoro cycle, the drag
reorder rules, the inline formatting parser and the tag vocabulary are all pure
functions with tests that run in seconds.

The split shows up in every feature:

| Decision | Lives in | Acting on it |
|---|---|---|
| What the timer becomes when a stretch ends | `TimerState.completed` | `TimerController` |
| Where a dragged card lands | `CardLayout.moveCard` | `DragController` |
| Which weather lookup to try | `loadWeather` | `OpenMeteoWeatherRepository` |
| What `**bold**` means | `parseFormattedText` | `FormattedText` |
| Whether a task survives a filter | `matchesTagFilter` | `TasksCard` |

`:shared` carries a JVM target purely so Kover can measure it — Kover cannot
instrument Kotlin/JS. The web app links the JS klib and never sees the JVM one.

## Layers

```
shared/commonMain
  domain/model        values and the rules about them
  domain/repository   what the app needs from the outside, as interfaces
  domain/text         the inline formatting parser and the toolbar's markers
  domain/usecase      rules that need more than one repository
  presentation        formatting for the screen; routing decisions
  core                the emulator switch

src/jsMain
  data/               state holders, and the browser and Firebase adapters
  ui/                 Compose HTML
  di/                 the Firebase config
```

There is no dependency-injection framework. `Main.kt` constructs everything and
hands it down; nothing else calls a constructor. With this many pieces that is
still one readable function, and it means the graph is a thing you can read
rather than a thing you have to run.

## State holders

Every feature has a controller in `data/`: `AuthController`, `SettingsController`,
`TimerController`, `TasksController`, `NotesController`, `TagsController`,
`ClockController`, `WeatherController`, `NotificationController`.

They all work the same way:

- Compose state (`mutableStateOf`) for anything the screen reads.
- **Optimistic**: apply the change locally, then write it. A tick or a drag lands
  instantly rather than waiting for a round trip.
- `start(uid)` / `stop()`, so a listener follows the account rather than the
  screen — attaching one inside a card would drop it whenever the card collapsed.

## Firestore

One document per thing that changes independently:

```
users/{uid}/state/settings     everything configurable, one document
users/{uid}/state/timer        the running timer
users/{uid}/tasks/{id}         one per task; subtasks carry a parentId
users/{uid}/notes/{id}         one per note
users/{uid}/tags/{id}          one per tag, shared by tasks and notes
```

Tasks and notes are a document each rather than one document holding them all.
Otherwise ticking off one task would rewrite every task the account has, and two
devices ticking different tasks would overwrite each other instead of merging.

The timer is separate from the settings because the two are written at completely
different rates — one when somebody changes their mind, the other on every Start,
Pause and Skip.

Security is one rule, in [firebase/firestore.rules](firebase/firestore.rules):
everything under `users/{uid}` is readable and writable only by that user. There
is no other subtree.

### Three Firestore traps, each of which cost a real bug

1. **A JS array arrives in Kotlin as `Array`, not `List`.** `value as? List<*>` is
   null for every stored array, so a saved layout reads as no layout at all, with
   no error anywhere. Converted once at the boundary in `JsInterop.kt`.
2. **Own-write echoes.** Firestore replays a local write to its own listener before
   the server confirms it. The caller has already applied that change, so applying
   it again undoes whatever came after — most visibly, it drags a running countdown
   backwards. Filtered on `metadata.hasPendingWrites`.
3. **Never put two listeners on one collection.** Because of (2), a write made
   through one listener arrives at the other as an ignorable echo — and Firestore
   sends no second snapshot when the server merely acknowledges it. Tags had a
   listener per list, and a tag made on a note never reached the task card until a
   reload. One `TagsController` now owns them.

## Compose HTML

Real DOM rather than a canvas, which is what lets the clone match the original's
CSS, text selection, native inputs and scrollbars rather than approximating them.

Three things about it are worth knowing before touching the UI:

- **Recomposition runs on `requestAnimationFrame`.** A browser pauses that in a tab
  it is not painting. The page renders once, click handlers still fire, and nothing
  ever updates — which looks exactly like a broken app. Anything that counts time
  must recompute from a stored instant rather than trusting its own ticks;
  `ClockController` and `TimerController` both do, and both also recompute on
  `visibilitychange`.
- **`classes()` throws on a token containing a space.** Each argument goes through
  `DOMTokenList.add`. Passing `"a b"` as one class name throws and kills the
  composition of that subtree — a row vanishes from the screen while its data sits
  correctly in the database.
- **Key every list row by id.** Compose reuses a row's element for whatever item now
  sits in that position, and a `ref` runs only once, when the element is created. A
  row that registers itself with a drag controller must be keyed, or after a filter
  change the controller holds elements filed under the ids of items that used to be
  there, and drags silently do nothing.

## Dragging

Hand-rolled, because the original's library is React-only. Two controllers, because
the questions are different:

- `DragController` — cards between two columns, a two-dimensional question. The
  target column is the **nearest** one by distance, not the one containing the
  pointer's x: below the tablet breakpoint the columns stack, and an x-only test
  would send every drag leftwards.
- `ListDragController` — a row up and down one list. One instance serves several
  lists, because the list being dragged is named when the drag begins; a dialog's
  subtasks and the task list behind it are on screen at once and must not measure
  against each other.

Both only track the gesture. Where things land is `:shared`, and tested.

### Two reorder rules, deliberately different

**Subtasks compact to 0, 1, 2.** The list being dragged is the whole list, so
renumbering cannot collide with anything.

**Top-level tasks and notes pool their positions.** Finished and filtered-out items
still hold positions in between the visible ones. Numbering the visible items 0, 1,
2 would collide with those, and everything hidden would reshuffle the moment the
filter was cleared. So the positions already held by the visible items are
collected, sorted, and dealt back out in the new order: the set of numbers in use
never changes, only which item holds which.

## Testing

`./gradlew :shared:allTests` runs everything on the JVM and on Node in seconds.
There are no browser tests: nothing in `src/jsMain` can be tested without a browser
and a signed-in session, and there is no fake for either.

What replaces them is the emulators. Every phase was exercised against local
Firebase Auth and Firestore before it shipped, and the PR for each records what was
checked and what could not be — see the repository's pull requests, which are the
verification log.

Coverage floors are per file: 100% line and method, 95% branch. Where a branch is
genuinely unreachable it is left alone and reported rather than contorted around.

## Deployment

Push to `main` → GitHub Actions → `:shared:allTests` → `jsBrowserDistribution` →
GitHub Pages. A failing test stops the deploy.

There is no server. Hash routing (`#/`) because Pages has no rewrites, so a deep
link cannot 404 on a path that only exists in the app.
