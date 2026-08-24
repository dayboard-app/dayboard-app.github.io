# Notes Area — Exhaustive Requirements (Focusly → Dayboard rebuild)

Source files analyzed (complete): `src/components/NotesList.tsx`, `src/components/NoteEditDialog.tsx`, `src/components/NoteViewDialog.tsx`, `src/components/FormattingToolbar.tsx`, plus `src/components/LinkifiedText.tsx` (fetched additionally; the notes UI depends on it for all rendered note text).

---

## 1. Data model

### 1.1 Note
| Field | Type | Notes |
|---|---|---|
| `id` | string (UUID) | Generated CLIENT-SIDE via `crypto.randomUUID()` before insert |
| `user_id` | string | Owner; set on insert; all reads filtered by `eq("user_id", user.id)` |
| `title` | string | Never null; empty title is coerced to `"Untitled"` on save |
| `body` | string \| null | Nullable; empty/whitespace-only body is stored as `null` |
| `position` | number (int) | 0-based manual sort order; list ordered by `position` ascending |

Stored in Supabase table `notes`. Query on load: `select("id, title, body, position")`, `eq("user_id", user.id)`, `order("position", { ascending: true })`.

### 1.2 Tag (shared with notes area; notes own a tag-creation UI)
| Field | Type | Notes |
|---|---|---|
| `id` | string (UUID) | Client-generated `crypto.randomUUID()` |
| `user_id` | string | Owner |
| `name` | string | Trimmed; duplicate check is case-insensitive |
| `color` | string | Hex color like `#6366f1` |
| `emoji` | string \| null | Max 2 chars in input; trimmed; empty → null |
| `created_at` | timestamp | Tags loaded ordered by `created_at` ascending |

Table `tags`. Load query: `select("id, name, color, emoji")`, `eq("user_id", user.id)`, `order("created_at", { ascending: true })`.

### 1.3 Note–Tag link
Join table `note_tags` with columns `note_id`, `tag_id`. Load query: `select("note_id, tag_id")` with NO user filter (relies on row-level security). Client keeps it as `noteTagMap: Record<noteId, tagId[]>`.

### 1.4 Formatting storage
Formatting is stored as inline plain-text markers inside the raw `title` and `body` strings (markdown-like, NOT HTML, NOT a rich-text document):
- Bold: `**text**`
- Italic: `*text*`
- Underline: `__text__` (non-standard markdown meaning: renders as underline, not bold)
- Inline code: `` `text` ``
- URLs are not marked up at all; plain `http(s)://…` substrings are auto-linkified at render time.
The edit dialog shows the raw markers in plain input/textarea fields; markers are rendered (parsed) only in the list rows and the view dialog.

---

## 2. NotesList component (the notes panel)

### 2.1 Props / integration
- `reloadRef?: MutableRefObject<(() => void) | null>` — parent (dashboard page) can trigger a reload (e.g., after realtime/external change). The exposed function reloads ONLY if the edit dialog is not currently open (tracked via `editDialogOpenRef`), so an external refresh never clobbers in-progress edits.
- `expanded?: boolean` — when true the whole panel is wrapped with `mx-auto w-full max-w-3xl` (centered, max width 48rem). Root container: `flex flex-col gap-4`.
- Requires an authenticated user from `useAuth()` (`user.id` used for all queries); with no user, load and add are no-ops.

### 2.2 Load behavior
- On mount (and whenever user changes): parallel fetch of `notes`, `tags`, `note_tags` via `Promise.all`. After responses, `loading` set to false.
- Loading UI: vertically padded block (`py-10`), centered column with gap-2: a spinner (5×5, `animate-spin`, `rounded-full border-2 border-primary border-t-transparent`) above text `Loading notes...` (`text-sm text-muted-foreground`).

### 2.3 Empty states
Shown when not loading and the visible (filtered) list is empty; `py-10 text-center text-sm text-muted-foreground`:
- With an active tag filter: `No notes with this tag.`
- Without filter: `No notes yet — type above to get started.` (exact string, includes an em dash)

### 2.4 Add-note form (top of panel)
- A `<form>` (Enter submits) with an input + icon button, `flex gap-2`.
- Input: placeholder `Add a new note...`; classes `flex-1 rounded-xl border bg-card px-4 py-2.5 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring/20 transition-shadow`.
- Submit button: 40×40px (`h-10 w-10`), `rounded-xl bg-primary text-primary-foreground`, lucide `Plus` icon 16×16 (`h-4 w-4`), `aria-label="Add note"`. Hover: `scale-105`; active: `scale-95`. Disabled when trimmed input is empty: `opacity-40`, no hover scale.
- Add flow (`addNote`):
  1. Trim input; abort if empty or no user.
  2. `id = crypto.randomUUID()`, `position = notes.length` (count of currently loaded notes; positions are NOT compacted after deletions, so gaps/collisions can exist — replicate this).
  3. Optimistically append `{id, title, body: null, position}` to local list; clear the input; refocus the input.
  4. `insert` into `notes` with `{ id, user_id, title, position }` (no body field → null).
  5. If a tag filter is active: auto-attach that tag to the new note — optimistic `noteTagMap` update + `insert` into `note_tags` `{ note_id, tag_id }`.
  6. Auto-open the Edit dialog for the newly created note (sets editNote, opens dialog, sets `editDialogOpenRef = true`).

### 2.5 Tag filter row
Rendered only when `tags.length > 0`; `flex items-center gap-1.5 flex-wrap`:
- Leading lucide `Filter` icon 12×12 (`h-3 w-3 shrink-0 text-muted-foreground/40`).
- `All` chip: `rounded-full px-2.5 py-0.5 text-[10px] font-medium`; when no filter active: `bg-foreground/10 text-foreground`; otherwise `text-muted-foreground/60 hover:text-muted-foreground`. Clicking clears filter.
- One chip per tag (in created_at order): same size classes; inline style background = `tag.color + "15"` (hex alpha suffix) inactive / `tag.color + "30"` active; text color = `tag.color`. Active chip additionally `ring-1 ring-offset-1 ring-offset-background`; inactive chips `opacity-60 hover:opacity-100`. Optional emoji before name with `mr-0.5`. Clicking a chip selects it; clicking the active chip again deselects (filter → null). Single-select only.
- Filtering: a note matches when its tag list contains the active tag id. Non-matching notes are hidden (not dimmed).

### 2.6 Note list & rows
- List container: `flex flex-col gap-0.5`. Visible list = notes filtered by active tag, sorted by `position` ascending.
- Drag & drop reordering via `@hello-pangea/dnd` (single vertical `Droppable` id `"notes"`, one `Draggable` per note keyed/id'd by note id). While dragging, the dragged wrapper gets `opacity-90 shadow-lg rounded-xl`.
- Row container: `rounded-xl border transition-all`; when expanded: `border-border/60 bg-card shadow-sm`; when collapsed: `border-transparent hover:border-border/30 hover:bg-muted/30`.
- Row inner layout (`group flex items-center gap-2 px-3 py-2.5`), left→right:
  1. **Drag handle**: lucide `GripVertical` 16×16 in a `p-0.5` wrapper; invisible by default (`text-muted-foreground/0`), 40% opacity on row hover (`group-hover:text-muted-foreground/40`), full `text-muted-foreground` on direct hover; `cursor-grab`, `active:cursor-grabbing`. When not draggable, a `w-5` spacer instead.
  2. **Expand chevron**: shown only when the note "has content" = non-null body OR ≥1 tag. lucide `ChevronRight` 14×14 (`h-3.5 w-3.5`), rotates 90° when expanded (`transition-transform duration-200`); button `p-0.5 text-muted-foreground/40 hover:text-foreground`. Otherwise a `w-5` spacer. Toggles per-note expanded state held in a `Set` (multiple rows can be expanded; not persisted).
  3. **Title area** (`flex-1 min-w-0 cursor-pointer`): clicking anywhere opens the View dialog. Title rendered at `text-sm` through `LinkifiedText` (formatting markers and URLs render). When collapsed and the note has tags, tiny inline tag pills follow the title (`ml-1.5`, gap-1): `rounded-full px-1.5 py-px text-[9px] font-medium`, background `tag.color + "18"`, text `tag.color`, emoji at `text-[8px]`.
  4. **Edit button**: 28×28 (`h-7 w-7`) rounded-md, lucide `Pencil` 12×12 (`h-3 w-3`), `aria-label="Edit"`. Invisible (`text-transparent`) until row hover (`group-hover:text-muted-foreground`); direct hover: `text-primary` + `bg-primary/5`. Opens the Edit dialog.
- **Expanded section** (below the row, only when expanded): `px-3 pb-3 pl-[3.25rem] space-y-2` with entry animation `animate-in fade-in slide-in-from-top-1 duration-150`:
  - Tag pills (if any): `flex flex-wrap gap-1`, each `rounded-full px-2 py-0.5 text-[10px] font-medium`, background `tag.color + "20"`, text `tag.color`, emoji `text-[10px]`.
  - Body preview (if body non-null): `<p>` with `text-xs text-muted-foreground whitespace-pre-wrap leading-relaxed rounded-lg bg-muted/30 px-3 py-2 line-clamp-2 cursor-pointer hover:bg-muted/50 transition-colors`, native tooltip `title="Click to view full note"`, clamped to 2 lines, rendered via `LinkifiedText`; clicking opens the View dialog.

### 2.7 Reordering semantics
`onDragEnd`: abort when there is no destination or source index equals destination index. The reorder operates on the FILTERED, position-sorted list: remove item at source index, insert at destination index, then reassign `position = index` (0..n-1) across that filtered list only. Local state: keep all notes not part of the reordered subset, replace the subset with the renumbered items. Persistence: parallel `update({ position })` calls, but only for items whose id changed at its index versus the pre-drag list (an optimization — untouched rows are not written). Consequence to replicate: reordering while a tag filter is active renumbers only the visible subset 0..n-1, which can collide with hidden notes' positions; the app accepts this.

### 2.8 Dialog wiring
- The Edit and View dialogs receive `note` resolved live from the current notes array (`notes.find(n => n.id === editNote.id) || editNote`), so optimistic updates propagate into the open dialog.
- Edit dialog close: when closing and the close was NOT caused by a delete (`deletedRef` flag), the list reloads from the DB (`loadNotes()`); the delete flag is then reset. `editDialogOpenRef` mirrors open state for the reload guard.
- Delete callback `handleNoteDeleted(id)`: sets `deletedRef = true` and optimistically removes the note from local state. Positions of remaining notes are NOT renumbered.
- View dialog `onEdit`: closes View, opens Edit for the same note (also sets `editDialogOpenRef = true`).

---

## 3. NoteEditDialog

### 3.1 Shell
- shadcn `Dialog`; content `max-w-3xl max-h-[85vh] overflow-y-auto`. Screen-reader-only header: title `Edit Note`, description `Edit note details and tags`. Renders nothing when `note` is null.
- On (re)open for a note: local `title`/`body` seeded from the note (`body || ""`), tag creator and delete confirmation are reset closed. Body sections spaced with `space-y-5`.

### 3.2 Title field
- Label `Title`: `text-[11px] font-medium text-muted-foreground uppercase tracking-wider`.
- Plain text input showing raw title (markers visible): `w-full rounded-lg border bg-card px-3 py-2.5 text-base ... focus:ring-2 focus:ring-ring/20`.
- Saves on blur; Enter also saves (preventDefault) and blurs the field.
- Save rule: `trim() || "Untitled"` — an empty title becomes literally `Untitled`. Optimistic update to the parent notes state, then `update({ title })` on `notes` by id.
- A `FormattingToolbar` sits directly below the input, bound to it.

### 3.3 Body field
- Label `Content` (same label styling).
- `<textarea rows={12}>`, placeholder `Write your note...`, classes `w-full rounded-lg border bg-card px-3 py-2.5 text-sm ... resize-y` (vertically resizable).
- Saves on blur. Save rule: `trim() || null` (empty → null). Optimistic parent update, then `update({ body })`.
- Its own `FormattingToolbar` below, bound to the textarea.
- Note: the local state keeps the untrimmed text; only the persisted/parent value is trimmed-or-null.

### 3.4 Close-time flush
On dialog close, before propagating: compute `trimmedTitle = title.trim() || "Untitled"` and `trimmedBody = body.trim() || null`; if either differs from the note's current values, optimistically update parent state and persist each changed field separately. (Safety net for closing without blurring; there is no Cancel — all edits autosave.)

### 3.5 Tags section
- Label `Tags` with lucide `Tag` icon 12×12, same label styling, `flex items-center gap-1.5`.
- **Assigned tags** (in `noteTagMap` order): pills `rounded-full px-2.5 py-0.5 text-[11px] font-medium cursor-pointer hover:opacity-80`, background `tag.color + "20"`, text `tag.color`, optional emoji, then name, then lucide `X` 10×10 (`h-2.5 w-2.5 ml-0.5`). Click removes the tag from the note.
- **Available tags** (all user tags not on this note): smaller pills `px-2 py-0.5 text-[10px]`, background `tag.color + "15"`; click adds to the note.
- Toggle persistence: optimistic map update, then `insert`/`delete` on `note_tags` (`eq note_id` + `eq tag_id` for delete). Tag assignment saves immediately (independent of dialog close).
- **Tag creator**:
  - Trigger button: `Plus` icon 12×12 + text `New tag`; `text-xs text-muted-foreground hover:text-foreground hover:bg-muted/50 rounded-md px-2 py-1`. Opening resets name and emoji to empty and color to the first palette color.
  - Panel: `rounded-lg border bg-muted/30 p-2.5 space-y-1.5`.
  - Emoji input: autofocused, width `w-10`, centered, `text-xs`, placeholder `😊`, `maxLength={2}`.
  - Name input: flex-1, `text-xs`, placeholder `Tag name`; Enter triggers create.
  - Color palette, exactly 10 swatches in this order: `#6366f1`, `#ec4899`, `#f59e0b`, `#10b981`, `#3b82f6`, `#8b5cf6`, `#ef4444`, `#14b8a6`, `#f97316`, `#64748b`. Swatch: 16×16 circle (`h-4 w-4 rounded-full`); selected: `scale-125 ring-2 ring-offset-1 ring-offset-background`; hover (unselected): `scale-110`.
  - Buttons: `Create` (`bg-primary/10 text-primary text-[10px] font-medium px-2.5 py-1 rounded hover:bg-primary/20`, disabled `opacity-40` when trimmed name empty) and `Cancel` (plain, `text-[10px] text-muted-foreground hover:bg-muted`).
  - Create logic: if a tag with the same name exists (case-insensitive, trimmed), do NOT create — instead toggle that existing tag on the note (note: toggle, so if it was already attached it gets removed), clear the name, close the creator (emoji value is left as-is in this branch). Otherwise: client UUID, optimistic append to tags list, `insert` into `tags` `{ id, user_id, name, color, emoji }` (emoji trimmed or null), attach to the note via the same toggle, clear name and emoji, close creator.

### 3.6 Delete flow (two-step inline confirmation, no separate dialog)
- Section separated by `border-t pt-4`.
- Initial button: lucide `Trash2` 12×12 + text `Delete note`; `text-xs text-muted-foreground hover:text-destructive`.
- Clicking swaps in a confirm row (`flex items-center gap-2`): text `Delete this note?` (`text-xs text-destructive`), button `Delete` (`rounded-lg bg-destructive px-3 py-1.5 text-xs font-medium text-destructive-foreground hover:bg-destructive/90`), button `Cancel` (plain, `text-xs text-muted-foreground hover:bg-muted`).
- Confirm: calls `onNoteDeleted(id)` (optimistic list removal + suppresses the close-time reload), `delete` from `notes` by id (tag links cascade at DB level or become orphans — client does not delete `note_tags` rows explicitly), then closes the dialog. Remaining notes keep their positions (no renumber).
- The confirmation state resets whenever the dialog reopens.

---

## 4. NoteViewDialog (read-only view)

- shadcn `Dialog`, content `max-w-3xl max-h-[85vh] overflow-y-auto`; renders nothing when `note` is null. SR-only description: `View note content`.
- Header title: the note title rendered through `LinkifiedText` (formatting + links live here), `text-base font-semibold pr-8`.
- Content column `space-y-4`:
  - Tags (if any): pills `rounded-full px-2.5 py-0.5 text-[11px] font-medium`, background `tag.color + "20"`, text `tag.color`, optional emoji. Not clickable.
  - Body, if present: container `text-sm text-foreground/90 whitespace-pre-wrap leading-relaxed rounded-lg bg-muted/30 px-4 py-3 min-h-[6rem]`, content via `LinkifiedText` (full text, no clamp).
  - Body empty state: `No content yet.` — `text-sm text-muted-foreground italic py-4 text-center`.
  - Bottom-right `Edit note` button: lucide `Pencil` 12×12 + label; `rounded-lg bg-primary/10 px-3 py-1.5 text-xs font-medium text-primary hover:bg-primary/20`. Fires `onEdit` → parent closes View and opens Edit for the same note.

---

## 5. FormattingToolbar

- Props: a ref to the target input/textarea, the current string value, and an onChange callback. One toolbar instance per field (title and body each have their own in the Edit dialog). It appears nowhere else.
- Always visible (not selection-triggered), placed under the field with `mt-1`: `flex items-center gap-0.5 rounded-lg border bg-popover px-1 py-1 shadow-sm w-fit`. The container calls `preventDefault()` on mousedown so clicking a button never blurs the field or collapses the selection.
- Exactly 4 buttons, in order, each `h-7 w-7 rounded-md text-muted-foreground hover:text-foreground hover:bg-accent`, icon 14×14 (`h-3.5 w-3.5`), with native `title` tooltip:
  | Order | Icon (lucide) | Tooltip/label | Marker pair |
  |---|---|---|---|
  | 1 | `Bold` | Bold | `**` / `**` |
  | 2 | `Italic` | Italic | `*` / `*` |
  | 3 | `Underline` | Underline | `__` / `__` |
  | 4 | `Code` | Code | `` ` `` / `` ` `` |
- `applyFormat(prefix, suffix)` toggle algorithm (replicate exactly):
  1. Read `selectionStart`/`selectionEnd` (each defaults to 0 when null); `selected = value.slice(start, end)`.
  2. Peek at `prefix.length` chars immediately BEFORE the selection and `suffix.length` chars immediately AFTER it.
  3. **Unwrap**: if those equal the prefix and suffix, remove both markers from the string; new selection = same text shifted left by `prefix.length`.
  4. **Wrap**: otherwise insert `prefix + selected + suffix` in place of the selection; new selection = the original text, now between the markers (`start + prefix.length` to `end + prefix.length`).
  5. Call `onChange(newValue)`, then on the next animation frame refocus the field and `setSelectionRange(newStart, newEnd)` (selection preserved for chained formatting).
  - With a collapsed cursor (no selection) this inserts an empty marker pair and leaves the caret between the markers.
  - Toggle detection only looks directly adjacent to the selection; selecting text INCLUDING its markers and pressing the button wraps again rather than unwrapping.

---

## 6. LinkifiedText (render-time formatting/link parser — required dependency)

Applied to: note title in list rows, body preview in expanded rows, and the View dialog's title and body. NOT applied in the Edit dialog (raw markers shown).

- Single combined regex (create a fresh instance per parse to avoid shared lastIndex):
  `/(https?:\/\/[^\s<>"')\]},]+|\*\*(.+?)\*\*|(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|__(.+?)__|`([^`]+)`)/g`
  Alternation order (first match wins): URL, bold, italic, underline, code.
- **URL** (`http://` or `https://`, terminated by whitespace or any of `< > " ' ) ] } ,`): rendered as an anchor opening in a new tab (`target="_blank" rel="noopener noreferrer"`), classes `underline decoration-primary/40 underline-offset-2 text-primary/80 hover:text-primary hover:decoration-primary/70 transition-colors break-all`; click handler stops propagation so clicking a link inside a note row/preview does NOT open the View dialog.
- **Bold** `**x**`: `<strong class="font-bold">`, inner content parsed RECURSIVELY (nested italic/underline/code/links inside bold render).
- **Italic** `*x*`: single asterisks, with negative lookbehind/lookahead on `*` at both marker boundaries so it never consumes `**`; `<em class="italic">`, recursive.
- **Underline** `__x__`: `<span class="underline underline-offset-2 decoration-foreground/50">`, recursive.
- **Code** `` `x` `` (content = one or more non-backtick chars): `<code class="rounded-[4px] bg-muted px-1.5 py-0.5 text-[0.85em] font-mono text-accent-foreground">`; content rendered LITERALLY (no recursive parsing inside code).
- Non-matching text passes through as plain text (framework-escaped; no HTML injection). Lazy (`.+?`) quantifiers → shortest match. Newlines preserved by the surrounding `whitespace-pre-wrap` containers, not by the parser.

---

## 7. Persistence, sync, and offline summary

- **Backend**: Supabase Postgres only — tables `notes`, `tags`, `note_tags`. No localStorage for any notes data. In Dayboard: Firebase equivalents (e.g., Firestore collections) with per-user scoping.
- **Write pattern everywhere**: optimistic local state update first, then a fire-and-forget awaited DB call. No error handling, no rollback, no retry, no toasts for failures — a failed write silently diverges until the next reload.
- **Reads**: full reload of all three datasets on mount, on external `reloadRef` trigger (skipped while the edit dialog is open), and after the edit dialog closes (skipped when the close came from a delete).
- **Client-generated UUIDs** for notes and tags make optimistic inserts possible without waiting for the server.
- **Position bookkeeping quirks to replicate**: new note position = current loaded count; deletes leave position gaps; reordering under an active tag filter renumbers only the visible subset to 0..n-1.
- **No search feature, no pinning, no per-note colors** exist in the notes area (color exists only on tags; filtering is by tag only).

## 8. Exact user-facing strings (complete list)
- `Add a new note...` (add input placeholder)
- `Add note` (aria-label, add button)
- `All` (filter chip)
- `Loading notes...`
- `No notes yet — type above to get started.`
- `No notes with this tag.`
- `Edit` (aria-label, row pencil button)
- `Click to view full note` (tooltip on expanded body preview)
- `Edit Note` / `Edit note details and tags` (SR-only edit dialog header)
- `Title`, `Content`, `Tags` (edit dialog field labels, uppercase-styled)
- `Untitled` (fallback title on empty save)
- `Write your note...` (body textarea placeholder)
- `New tag`, `Tag name` (placeholder), `😊` (emoji placeholder), `Create`, `Cancel` (tag creator)
- `Delete note`, `Delete this note?`, `Delete`, `Cancel` (delete flow)
- `View note content` (SR-only view dialog description)
- `No content yet.` (view dialog empty body)
- `Edit note` (view dialog button)
- Toolbar tooltips: `Bold`, `Italic`, `Underline`, `Code`

## 9. Icon inventory (lucide)
`Plus` (add note, new tag), `ChevronRight` (expand, rotates 90°), `GripVertical` (drag handle), `Pencil` (edit, both list row and view dialog), `Filter` (tag filter row), `X` (remove tag pill), `Tag` (tags label), `Trash2` (delete note), `Bold`, `Italic`, `Underline`, `Code` (toolbar).
