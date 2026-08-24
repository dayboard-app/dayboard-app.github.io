# Todo Area — Exhaustive Requirements (Focusly → Dayboard rebuild)

Source files read completely: `src/components/TodoList.tsx`, `src/components/TaskEditDialog.tsx`, `src/components/TaskViewDialog.tsx`, `src/components/LinkifiedText.tsx`, plus `src/components/FormattingToolbar.tsx` (fetched because TaskEditDialog depends on it for the edit flow).

---

## 1. Data model

### 1.1 Task
Client-side interface (mirrors Supabase `tasks` table columns selected):

| Field | Type | Notes |
|---|---|---|
| `id` | string (UUID) | Generated client-side via `crypto.randomUUID()` before insert |
| `text` | string | Task title. Never empty after edit: falls back to `"Untitled"` |
| `body` | string \| null | Notes/description. Trimmed; empty string is stored as `null` |
| `done` | boolean | Completion flag. Default `false` on create |
| `position` | number | Sort order within its sibling group (top-level or within one parent) |
| `parent_id` | string \| null | `null` = top-level task; non-null = subtask of that task id |

Server-side columns also include `user_id` (set on insert to the authenticated user's id) and, implicitly, whatever else the table has; the client only selects `id, text, body, done, position, parent_id`.

Only ONE level of nesting exists: subtasks cannot have their own subtasks (the edit dialog hides the Tags and Subtasks sections when `task.parent_id` is non-null).

There are NO due dates and NO priorities anywhere in the todo feature.

### 1.2 Tag (used by todo for filtering/labeling; tag CRUD partially lives in the edit dialog)
| Field | Type | Notes |
|---|---|---|
| `id` | string (UUID) | Client-generated |
| `name` | string | |
| `color` | string | Hex color like `#6366f1` |
| `emoji` | string \| null | Optional, max input length 2 characters |

### 1.3 Task↔Tag join
Supabase table `task_tags` with columns `task_id`, `tag_id`. Client keeps it as `taskTagMap: Record<string, string[]>` (taskId → array of tagIds, in insertion order).

---

## 2. Persistence & sync

- **Everything is remote (Supabase Postgres); nothing todo-related is in localStorage.** Tables used: `tasks`, `tags`, `task_tags`.
- **Load** (`loadTasks`), runs on mount and whenever the user changes; does nothing if no user. Three parallel queries:
  1. `tasks`: select `id, text, body, done, position, parent_id` where `user_id = user.id`, ordered by `position` ascending.
  2. `tags`: select `id, name, color, emoji` where `user_id = user.id`, ordered by `created_at` ascending.
  3. `task_tags`: select `task_id, tag_id` (no user filter client-side; relies on RLS).
- **All mutations are optimistic**: local state is updated first, then the Supabase call is awaited. No error handling/rollback exists on failed writes (no toasts, no retry).
- **External reload hook**: the component takes an optional `reloadRef` prop (a mutable ref). It assigns a function that calls `loadTasks()` UNLESS the edit dialog is currently open (`editDialogOpenRef.current`) OR a reorder write is in flight (`reorderingRef.current`). The parent uses this for realtime (Supabase realtime subscription lives outside this component) — requirement: realtime-triggered reloads must be suppressed while editing or reordering to avoid clobbering in-progress local state.
- **Reorder guard timing**: after a drag-reorder's DB writes settle, the guard stays up for a further **800 ms** (`setTimeout`) so realtime echoes of the app's own writes do not trigger a reload that visually reshuffles items.
- **On edit dialog close**: if the close was NOT caused by a delete, the list does a full `loadTasks()` refresh. A `deletedRef` boolean flag distinguishes delete-closes from normal closes (set true in `handleTaskDeleted`, consumed and reset on close).

---

## 3. TodoList main view

### 3.1 Layout / props
- Component props: `reloadRef?` (see above) and `expanded?: boolean`. When `expanded` is true the whole list gets `mx-auto w-full max-w-3xl` (centered, max width 48rem); otherwise no width constraint. Root is a vertical flex column with `gap-4` (1rem).
- Order of sections top to bottom: (1) add-task form, (2) tag filter row (only if any tags exist), (3) task list area (loading / empty / pending droppable / completed group), (4) the two dialogs.

### 3.2 Add-task form
- A `<form>` (submit = add) with horizontal `gap-2`:
  - Text input, placeholder exactly: `Add a new task...`. Styling: `flex-1 rounded-xl border bg-card px-4 py-2.5 text-sm`, focus ring `ring-2 ring-ring/20`, transition on shadow. The input keeps a ref and **re-focuses itself after a task is added**.
  - Submit button: 40×40 px (`h-10 w-10`), `rounded-xl bg-primary text-primary-foreground`, contains lucide `Plus` icon at 16px (`h-4 w-4`), `aria-label="Add task"`. Hover scales to 105%, active scales to 95%. **Disabled when trimmed input is empty**; disabled style: `opacity-40`, no hover scale.
- **Add flow** (on submit):
  1. Trim input; abort if empty or no user.
  2. Generate UUID client-side.
  3. `position` = `max(position of existing top-level tasks) + 1`, or `0` if there are none. (Note: computed over ALL top-level tasks, including completed and filtered-out ones.)
  4. Optimistically append `{id, text, body: null, done: false, position, parent_id: null}` to local state; clear input; refocus input.
  5. Insert into Supabase `tasks` with `user_id`.
  6. **If a tag filter is active**, the new task automatically gets that tag: optimistic map update + insert into `task_tags`.
  7. **The edit dialog auto-opens for the newly created task** (so the user can immediately add notes/subtasks/tags).

### 3.3 Loading state
While the initial load is pending: a centered column (`py-10`) with a spinner (20×20 px circle, `border-2 border-primary border-t-transparent`, `animate-spin`) and text `Loading tasks...` (`text-sm text-muted-foreground`).

### 3.4 Empty states
Shown only when not loading and both pending and completed (after filter) are empty; centered `py-10 text-sm text-muted-foreground`:
- With an active tag filter: `No tasks with this tag.`
- Otherwise: `No tasks yet — type above to get started.`

### 3.5 Sorting / grouping
- `topLevel` = tasks with `parent_id == null`.
- `pendingTop` = topLevel, not done, passing the tag filter, sorted by `position` asc.
- `completedTop` = topLevel, done, passing the tag filter, sorted by `position` asc.
- Subtasks of a parent: filter by `parent_id`, sorted by `position` asc.
- Pending list renders first; completed tasks render below in their own group.

### 3.6 Completed section
Rendered only if `completedTop.length > 0`, with top margin `mt-4`:
- Header label: `Completed · {count}` — exact format with the middle-dot; styled `text-[11px] font-medium text-muted-foreground/60 uppercase tracking-widest`, inside `mb-1.5 px-3`.
- The completed rows container has `opacity-75`.
- Completed tasks are NOT draggable (rendered without drag handle; a `w-5` spacer sits where the handle would be).

### 3.7 Task row (top-level), collapsed
Row wrapper: `rounded-xl border transition-all`; when collapsed: `border-transparent hover:border-border/30 hover:bg-muted/30`; when expanded: `border-border/60 bg-card shadow-sm`. Inner row: `group flex items-center gap-2 px-3 py-2.5`. Left-to-right contents:
1. **Drag handle** (pending only): lucide `GripVertical` 16px, `cursor-grab active:cursor-grabbing`, invisible by default (`text-muted-foreground/0`), fades to `/40` on row hover, full `text-muted-foreground` on direct hover. Non-draggable rows get a `w-5` spacer instead.
2. **Checkbox button**: 20×20 px (`h-5 w-5`), `rounded-md border-2`, containing lucide `Check` 12px (`h-3 w-3`). Done: `border-primary bg-primary text-primary-foreground`. Not done: `border-muted-foreground/30 text-transparent`, hover: `border-primary text-primary scale-110` (checkmark previews on hover).
3. **Expand chevron**: rendered only when the task "has content" = has body OR has subtasks OR has ≥1 tag. Lucide `ChevronRight` 14px (`h-3.5 w-3.5`), `text-muted-foreground/40 hover:text-foreground`, rotates 90° when expanded with `duration-200` transition. Otherwise a `w-5` spacer.
4. **Title area** (`flex-1 min-w-0 cursor-pointer`): clicking it opens the **View dialog**. Title text `text-sm`; when done: `line-through text-muted-foreground`. Title is rendered through `LinkifiedText` (links + inline formatting; see §7).
   - **Inline tag pills** (collapsed only, if tags exist): after the title, `ml-1.5`, each pill `rounded-full px-1.5 py-px text-[9px] font-medium`, background = tag color + hex alpha `18`, text color = tag color; optional emoji at `text-[8px]`.
   - **Subtask progress badge** (collapsed only, if subtasks exist): `ml-1.5 rounded-full bg-muted px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground`, text `{doneCount}/{totalCount}` (e.g. `2/5`).
5. **Edit pencil button**: 28×28 px (`h-7 w-7`) `rounded-md`, lucide `Pencil` 12px (`h-3 w-3`), `aria-label="Edit"`. Invisible (`text-transparent`) until row hover (`group-hover:text-muted-foreground`), direct hover: `text-primary bg-primary/5`. Opens the Edit dialog.

### 3.8 Task row, expanded (inline, read-only)
Toggled per-task via the chevron; expansion state is a local `Set<string>` of task ids (not persisted). Expanded content area: `px-3 pb-3 pl-[3.25rem] space-y-2`, animated in with fade + slide-from-top over 150 ms. Contains, in order (each only if present):
1. **Tags**: wrap row `gap-1`; pill `rounded-full px-2 py-0.5 text-[10px] font-medium`, background tag color + alpha `20`, text tag color, emoji at `text-[10px]`.
2. **Body/notes**: `<p>` `text-xs text-muted-foreground whitespace-pre-wrap leading-relaxed rounded-lg bg-muted/30 px-3 py-2`, content via LinkifiedText.
3. **Subtasks** (`space-y-0.5 pt-1`): each row `flex items-center gap-2 px-2 py-1.5 rounded-md hover:bg-muted/40`; checkbox 16×16 (`h-4 w-4 rounded border-2`, Check 10px `h-2.5 w-2.5`), same done/undone color scheme (undone hover: `border-primary text-primary`, no scale); text `text-xs`, done = strikethrough + muted; via LinkifiedText. **In this inline view subtasks can only be toggled — no drag, no edit, no delete.**
- Toggling a subtask here does NOT affect the parent's done state (no auto-complete-parent logic anywhere).

### 3.9 Completion behavior (`toggleTask`)
- Toggling a **top-level task** flips its `done` and **cascades the same new value to ALL of its subtasks** (both when completing and when un-completing). Optimistic local update for parent+subtasks together, then: one Supabase update for the parent, then parallel updates for each subtask.
- Toggling a **subtask** flips only itself (its `subtaskIds` list is empty because it has a `parent_id`).
- Completed top-level tasks move to the Completed group (they keep their `position` value; no repositioning happens on completion).

### 3.10 Tag filter row
Rendered only when `tags.length > 0`. Layout: `flex items-center gap-1.5 flex-wrap`, prefixed by lucide `Filter` icon 12px (`h-3 w-3 text-muted-foreground/40`).
- **"All" chip** first: `rounded-full px-2.5 py-0.5 text-[10px] font-medium`. Active (no filter): `bg-foreground/10 text-foreground`; inactive: `text-muted-foreground/60 hover:text-muted-foreground`. Clicking clears the filter.
- **One chip per tag** (tags ordered by `created_at` asc): same size classes; background = tag color + alpha `30` when selected / `15` when not, text = tag color; selected also gets `ring-1 ring-offset-1 ring-offset-background`, unselected gets `opacity-60 hover:opacity-100`. Optional emoji before the name with `mr-0.5`.
- Clicking a tag chip selects it; clicking the already-selected chip **toggles the filter off** (back to All). Single-select only (one active tag max).
- Filter semantics: a top-level task matches if its tag list contains the active tag id. Applies to both pending and completed groups. Subtasks are not independently filtered (they show under their parent).
- Active filter also affects Add (auto-tags new tasks, §3.2) and drag-reorder (position pool, §3.11).

### 3.11 Drag-and-drop reorder (top-level pending only)
Library: `@hello-pangea/dnd` (`DragDropContext`/`Droppable`/`Draggable`). Droppable id: `"pending"`. Only pending top-level tasks are draggable; completed are not; inline subtasks are not (subtask reorder happens only in the dialogs).
- While dragging, the dragged row gets `opacity-90 shadow-lg rounded-xl`.
- `onDragEnd` logic:
  1. Ignore if no destination, or if source index == destination index in the same droppable, or if the source droppable is not `"pending"`.
  2. Reorder a copy of the **currently visible** pending list (i.e., respecting the active tag filter).
  3. **Position-pool algorithm**: collect the `position` values of the visible items, sort ascending, then reassign them in the new visual order (visible item at new index i gets the i-th smallest pooled position). This guarantees no collision with positions held by hidden (filtered-out or completed) tasks and keeps relative order stable across the full list.
  4. Optimistically apply new positions to local state.
  5. Compute the set of tasks whose position actually changed; if none, stop.
  6. Set the reordering guard; write each changed task's `position` to Supabase in parallel; when all writes settle (`finally`), release the guard after an additional **800 ms**.

---

## 4. TaskEditDialog (edit flow)

Modal dialog (shadcn Dialog), content `max-w-3xl max-h-[85vh] overflow-y-auto`. Screen-reader-only title `Edit Task` and description `Edit task details, subtasks, and tags`. Body is `space-y-5`. Opens: from the pencil button, automatically after adding a new task, or via "Edit task" in the View dialog. Renders nothing when `task` is null.

**State initialization**: when the dialog opens (or the task id changes while open), it seeds title/body from the task and resets: subtask input hidden and empty, no subtask being edited, tag creator hidden, delete confirmation off.

**The dialog always shows the freshest task object**: the parent passes `tasks.find(t => t.id === editTask.id) || editTask`, so external updates to the same task propagate in.

### 4.1 Title field
- Label `Title` (`text-[11px] font-medium text-muted-foreground uppercase tracking-wider`).
- Text input, `text-base`, `rounded-lg border bg-card px-3 py-2.5`, focus ring `ring-2 ring-ring/20`. No placeholder.
- **Save on blur** and **on Enter** (Enter also blurs; preventDefault stops form submit). Save rule: trimmed value, or `"Untitled"` if empty. Optimistic local update + Supabase `update {text}`.
- A **FormattingToolbar** (see §6) is attached below the input.

### 4.2 Notes field
- Label `Notes` (same label styling).
- `<textarea>`, 6 rows, vertically resizable (`resize-y`), placeholder exactly: `Add details or notes...`, `text-sm`, same border/focus styling.
- **Save on blur**: trimmed value; empty saves as `null`. Optimistic + Supabase `update {body}`.
- FormattingToolbar attached below.

### 4.3 Auto-save on close
When the dialog is closed by any means, before propagating the close: compare trimmed title (fallback `"Untitled"`) and trimmed body (empty→null) against the task's current values; write each to local state + Supabase only if changed. (This covers closing while a field still has unsaved edits.)

### 4.4 Tags section (hidden for subtasks, i.e. only when `parent_id == null`)
- Label: lucide `Tag` icon 12px + text `Tags` (label styling as above).
- **Current tags** (attached): pills `rounded-full px-2.5 py-0.5 text-[11px] font-medium`, bg tag color+alpha `20`, text tag color, emoji shown, trailing lucide `X` icon (10px, `h-2.5 w-2.5 ml-0.5`). Whole pill is clickable → **removes** the tag from the task (`hover:opacity-80`).
- **Available tags** (not attached): smaller pills `px-2 py-0.5 text-[10px]`, bg tag color+alpha `15`; clicking **attaches** the tag.
- Toggle is optimistic (map update) then Supabase: delete from `task_tags` (by task_id+tag_id) or insert into it.
- **New tag creator**: toggle button `+ New tag` (lucide `Plus` 12px, `text-xs text-muted-foreground`, hover foreground + `bg-muted/50`). Opening it resets name/emoji to empty and color to the first palette color. The creator panel (`rounded-lg border bg-muted/30 p-2.5`):
  - Emoji input: autofocused, width `w-10`, centered text, placeholder `😊`, **maxLength 2**.
  - Name input: placeholder `Tag name`, Enter creates.
  - **Color palette** (10 fixed swatches, exact order): `#6366f1`, `#ec4899`, `#f59e0b`, `#10b981`, `#3b82f6`, `#8b5cf6`, `#ef4444`, `#14b8a6`, `#f97316`, `#64748b`. Swatches are 16×16 circles; selected: `scale-125 ring-2 ring-offset-1`; unselected hover: `scale-110`. Default selection: `#6366f1`.
  - Buttons: `Create` (`bg-primary/10 text-primary`, `text-[10px]`, disabled at `opacity-40` when name is blank) and `Cancel` (plain muted).
  - **Create logic**: if a tag with the same name (case-insensitive, trimmed) already exists, do NOT create a duplicate — just toggle that existing tag on the task, clear the name, close the creator. Otherwise: client UUID, trimmed name, chosen color, trimmed emoji (empty→null); optimistic add to tags list; insert into `tags` (with user_id); then attach it to the task via the toggle; clear name+emoji; close creator.

### 4.5 Subtasks section (hidden for subtasks — only one nesting level)
- Label `Subtasks` (label styling).
- **List** (drag-and-drop context, droppable id `"dialog-subtasks"`): each row `group flex items-center gap-2 rounded-lg px-2 py-1.5`; dragging: `shadow-md bg-card`; hover: `bg-muted/40`. Row contents:
  1. Drag handle: `GripVertical` 14px, `text-muted-foreground/30`, `/60` on row hover, grab cursors.
  2. **Read-only done indicator** (a `<span>`, NOT a button — subtask completion cannot be toggled in the edit dialog): 16×16 `rounded border-2`, Check 10px; done = primary-filled, undone = transparent check.
  3. Text `text-sm`, done = strikethrough+muted, `cursor-text select-none`. **Click the text to edit in place**: swaps to an autofocused input (with its own FormattingToolbar). Save on blur or Enter (trimmed, empty→`"Untitled"`); **Escape cancels** editing without saving. Optimistic + Supabase update.
  4. Delete button: lucide `X` 12px, invisible until row hover (`text-transparent group-hover:text-muted-foreground/50`), direct hover `text-destructive`. **Deletes immediately with NO confirmation**: optimistic removal + Supabase delete by id.
- **Empty state** (no subtasks and the add-input hidden): `No subtasks yet.` (`text-xs text-muted-foreground/50 px-1`).
- **Add subtask**: toggle button `+ Add subtask` (Plus 12px, `text-xs` muted). Shows a form: autofocused input, placeholder exactly `Subtask title...` (`text-xs`), Escape hides the form, submit button labeled `Add` (`bg-primary/10 text-primary text-xs`). Add flow mirrors task add: trim (abort if empty), client UUID, `position = max(sibling positions)+1` or `0`, `done:false`, `parent_id = task.id`, optimistic append + Supabase insert, input cleared (form stays open for adding more).
- **Reorder subtasks by drag**: on drop (ignore if no destination or same index), reorder the array and **renumber positions to compact 0..n-1** (unlike top-level reorder's position pool). Optimistic replace in the global list; Supabase writes only for rows whose id at index i differs from the old id at that index.

### 4.6 Delete section
At the bottom, separated by a top border (`border-t pt-4`). Two states:
- Default: button `Delete task` with lucide `Trash2` 12px, `text-xs text-muted-foreground hover:text-destructive`.
- Confirmation (inline, not a nested dialog): text `Delete this task and all its subtasks?` (`text-xs text-destructive`), a `Delete` button (`bg-destructive text-destructive-foreground text-xs font-medium rounded-lg px-3 py-1.5`, hover `/90`), and a `Cancel` button (muted).
- Confirmed delete: calls the parent's `onTaskDeleted(task.id)` (which sets the deleted flag, removes the task AND every task whose `parent_id` equals it from local state, and drops it from the expanded set), then Supabase delete of the parent row only (**subtask rows are expected to be removed by DB cascade — the client deletes only the parent id**), then closes the dialog. Because of the deleted flag, closing after delete does NOT trigger the usual reload.

---

## 5. TaskViewDialog (read view)

Opened by clicking a task's title in the list. Modal, same sizing: `max-w-3xl max-h-[85vh] overflow-y-auto`. Renders nothing when task is null. Also always resolves the freshest task object from the list. SR-only description: `View task details`.

Content, top to bottom:
1. **Title** as the dialog header: `text-lg font-semibold pr-8`, rendered via LinkifiedText (formatting + clickable links).
2. **Tags** (if any): pills `rounded-full px-2.5 py-0.5 text-xs font-medium`, bg tag color+alpha `20`, text tag color, emoji shown.
3. **Notes**: if body exists — `text-sm text-foreground/90 whitespace-pre-wrap leading-relaxed rounded-lg bg-muted/30 px-4 py-3`, via LinkifiedText. If not — italic muted `No notes.`
4. **Subtasks** (only if ≥1): label `Subtasks · {done}/{total}` (uppercase tracking-wider `text-xs` muted; exact middle-dot format). List is a DnD context (droppable id `"view-subtasks"`), rows `px-2 py-2`:
   - Drag handle `GripVertical` 16px (same hover behavior as edit dialog).
   - **Toggleable checkbox** (20×20 `rounded-md border-2`, Check 12px; undone hover previews primary): toggles only that subtask, optimistic + Supabase `update {done}`. No cascade to/from parent.
   - Text `text-sm` via LinkifiedText, done = strikethrough+muted. Not editable here.
   - **Reorder by drag** with the exact same compact-0..n-1 algorithm and diff-write as the edit dialog.
   - No delete in this dialog.
5. **`Edit task` button** bottom-right (`bg-primary/10 text-primary text-sm font-medium rounded-lg px-4 py-2`, hover `bg-primary/20`, lucide `Pencil` 14px): closes the view dialog and opens the Edit dialog for the same task.

Differences vs Edit dialog: view can toggle subtask done but not edit/delete/add them, cannot change title/body/tags, renders formatting/links instead of raw text.

---

## 6. FormattingToolbar (used in Edit dialog: title input, notes textarea, subtask inline edit)

A small always-visible toolbar under the field: `rounded-lg border bg-popover px-1 py-1 shadow-sm w-fit`, buttons 28×28 (`h-7 w-7 rounded-md`), icons 14px, muted → foreground+`bg-accent` on hover. `onMouseDown` is prevented on the toolbar so clicking a button does not blur the field (blur would trigger save).

Four buttons, in order, each with a `title` tooltip:
| Button | Icon (lucide) | Wraps selection with |
|---|---|---|
| Bold | `Bold` | `**` … `**` |
| Italic | `Italic` | `*` … `*` |
| Underline | `Underline` | `__` … `__` |
| Code | `Code` | `` ` `` … `` ` `` |

Behavior (`applyFormat`):
- Reads the field's current selection (start/end; collapsed selection allowed — inserts the marker pair around the empty point).
- **Toggle-off**: if the characters immediately before the selection equal the prefix AND immediately after equal the suffix, the markers are removed instead of added, and the selection shifts back by the prefix length.
- Otherwise wraps the selected text; new selection covers the same text inside the markers.
- After applying, refocuses the field and restores the computed selection on the next animation frame.

---

## 7. LinkifiedText (rendering of titles, bodies, subtask texts)

Renders a `<span>` (optional `className`) whose content is the input text parsed with a single combined regex:

```
/(https?:\/\/[^\s<>"')\]},]+|\*\*(.+?)\*\*|(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|__(.+?)__|`([^`]+)`)/g
```

Token semantics (a fresh regex instance is created per call to avoid shared `lastIndex` state; plain text between matches passes through unchanged):
1. **URL auto-detection**: `http://` or `https://` followed by any run of characters excluding whitespace and `< > " ' ) ] } ,`. Rendered as `<a>` with `target="_blank"`, `rel="noopener noreferrer"`, classes `underline decoration-primary/40 underline-offset-2 text-primary/80 hover:text-primary hover:decoration-primary/70 break-all`, and **click stopPropagation** so clicking a link inside a task row does NOT open the view dialog. Link text = the URL itself.
2. `**text**` → `<strong class="font-bold">`, content parsed **recursively** (nested formatting works).
3. `*text*` (single asterisks, lookarounds prevent matching inside `**`) → `<em class="italic">`, recursive.
4. `__text__` → underline span (`underline underline-offset-2 decoration-foreground/50`), recursive.
5. `` `text` `` → `<code>` styled `rounded-[4px] bg-muted px-1.5 py-0.5 text-[0.85em] font-mono text-accent-foreground`; content is NOT recursively parsed (literal).

Evaluation-order nuance: bold (`match[2]`) is checked before underline (`match[4]`) before italic (`match[3]`) before code (`match[5]`); URL wins first since the alternation lists it first and the handler checks `startsWith("http")`.

Used in: list row titles (collapsed and expanded), inline expanded body, inline subtask rows, view dialog title/body/subtask rows. NOT used in the edit dialog (raw markers are shown there for editing).

---

## 8. Cross-component contracts & misc requirements

- Icons used across the todo area (lucide): `Plus`, `Check`, `ChevronRight`, `GripVertical`, `Pencil`, `Filter`, `X`, `Tag`, `Trash2`, `Bold`, `Italic`, `Underline`, `Code`.
- Auth comes from an `AuthContext` (`useAuth().user`); all task/tag reads and inserts are scoped by `user.id`.
- All ids are generated client-side with `crypto.randomUUID()` before insert (needed for optimistic UI).
- No pagination; the entire task/tag set loads at once.
- No undo, no toasts, no error surfacing in this area.
- Expansion state, active filter, and dialog states are ephemeral (in-memory only, reset on reload).
- Deleting a task must remove its subtasks both locally (explicit filter) and in the DB (client deletes only the parent row — the backend must cascade `tasks.parent_id` and `task_tags` references).
- The edit and view dialogs must always render the live task record (re-resolved by id from the current list) so concurrent updates show through.
- Positions are integers but are never compacted for top-level tasks (only subtask reorders compact to 0..n-1); top-level ordering must tolerate sparse/duplicate-free arbitrary integer positions.

### Alpha-suffix convention for tag colors (hex8)
Tag color backgrounds are the tag's 6-digit hex plus a 2-digit hex alpha suffix, used at these exact levels: `15` (filter chip unselected / available tag pill), `18` (inline collapsed pill), `20` (expanded pill, dialogs' pills), `30` (filter chip selected). Text color is always the raw tag color.
