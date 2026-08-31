package io.github.dayboard.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.bchmsl.keel.color.SwatchShade
import io.github.bchmsl.keel.components.Badge
import io.github.bchmsl.keel.components.ButtonSize
import io.github.bchmsl.keel.components.ButtonVariant
import io.github.bchmsl.keel.components.Checkbox
import io.github.bchmsl.keel.components.CheckboxSize
import io.github.bchmsl.keel.components.EmptyState
import io.github.bchmsl.keel.components.FormattedText
import io.github.bchmsl.keel.components.IconButton
import io.github.bchmsl.keel.components.Pill
import io.github.bchmsl.keel.components.PillButton
import io.github.bchmsl.keel.components.PillSize
import io.github.bchmsl.keel.components.Spinner
import io.github.bchmsl.keel.components.TextField
import io.github.bchmsl.keel.dom.classNames
import io.github.bchmsl.keel.icons.Icon
import io.github.bchmsl.keel.icons.LucideIcon
import io.github.dayboard.data.ListDragController
import io.github.dayboard.data.TasksController
import io.github.dayboard.domain.model.Tag
import io.github.dayboard.domain.model.Task
import io.github.dayboard.domain.model.hasDetail
import io.github.dayboard.domain.model.subtaskProgress
import org.jetbrains.compose.web.attributes.onSubmit as onSubmitAttribute
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Form
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The task list: an add box, a tag filter, the unfinished tasks, and the finished
 * ones below them.
 *
 * Rows can be expanded in place to show their notes and subtasks read-only, which
 * is the common case - checking what a task involves. Changing any of it happens in
 * the dialogs.
 */
@Composable
fun TasksCard(
    tasks: TasksController,
    drag: ListDragController,
    expanded: Boolean,
    onEditTask: (String) -> Unit,
    onViewTask: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Div({ classes(*sized("tasks", expanded)) }) {
        AddTaskForm(
            draft = draft,
            onDraftChange = { draft = it },
            onSubmit = {
                tasks.addTask(draft)?.let { added ->
                    draft = ""
                    // Straight into the editor, as the original does: a task is
                    // added by typing one line, and notes, subtasks and tags all
                    // live one step further in.
                    onEditTask(added)
                }
            },
        )

        if (tasks.allTags.isNotEmpty()) {
            TagFilterRow(
                tags = tasks.allTags,
                selectedId = tasks.filterTagId,
                onSelect = tasks::setFilter,
            )
        }

        when {
            !tasks.loaded -> LoadingTasks()

            tasks.pending.isEmpty() && tasks.completed.isEmpty() -> EmptyTasks(tasks.filterTagId)

            else -> {
                PendingTasks(tasks, drag, onEditTask, onViewTask)
                CompletedTasks(tasks, drag, onEditTask, onViewTask)
            }
        }
    }
}

@Composable
private fun AddTaskForm(draft: String, onDraftChange: (String) -> Unit, onSubmit: () -> Unit) {
    Form(attrs = {
        classes("tasks__add")
        onSubmitAttribute { event ->
            event.preventDefault()
            onSubmit()
        }
    }) {
        AddRowField(
            draft = draft,
            onDraftChange = onDraftChange,
            placeholder = "Add a new task...",
            ariaLabel = "Add a new task",
        )

        AddRowButton(enabled = draft.isNotBlank(), ariaLabel = "Add task")
    }
}

/**
 * The text field in an add row.
 *
 * keel's `TextField` is the whole of it now. What was here was the field's border,
 * radius, padding, fill and focus glow written out, at values that were keel's to
 * within the horizontal padding and the thickness of the glow.
 *
 * Shared with the notes card, which had the same two controls a second time.
 */
@Composable
internal fun AddRowField(
    draft: String,
    onDraftChange: (String) -> Unit,
    placeholder: String,
    ariaLabel: String,
) {
    TextField(
        value = draft,
        onValueChange = onDraftChange,
        placeholder = placeholder,
        ariaLabel = ariaLabel,
        // keel's field is `width: 100%`, correctly - sharing a flex row is the row's
        // decision, so the class that makes it share one belongs to this app.
        attrs = { classNames("tasks__add-row-field") },
    )
}

/** The submit button in an add row. */
@Composable
internal fun AddRowButton(enabled: Boolean, ariaLabel: String) {
    IconButton(
        ariaLabel = ariaLabel,
        // The form's own submit handler does the work; this only has to *be* a submit
        // button, which is what makes Enter in the field add the row.
        onClick = {},
        size = ButtonSize.Icon,
        // Nothing to add is not an error worth explaining, so the button simply is
        // not available until there is something to add.
        enabled = enabled,
        attrs = { attr("type", "submit") },
    ) {
        Icon(LucideIcon.Plus, size = ICON_SMALL)
    }
}

/**
 * The tag filter: an "All" chip, then one per tag.
 *
 * Shared with the notes card, which filters by the same tag vocabulary and had this
 * row written out a second time. keel's `PillButton` made the two bodies identical,
 * so keeping both would have been a copy of a copy.
 */
@Composable
internal fun TagFilterRow(tags: List<Tag>, selectedId: String?, onSelect: (String?) -> Unit) {
    Div({ classes("tasks__filters") }) {
        Icon(LucideIcon.Filter, size = ICON_TINY, className = "tasks__filter-icon")

        // A null colour is keel's pill for no particular tag, and it takes the theme's
        // own ink. Passing a neutral hex instead could not work: it would stay the one
        // colour across all twelve palettes.
        PillButton(
            label = "All",
            color = null,
            ariaLabel = "Show all",
            onClick = { onSelect(null) },
            selected = selectedId == null,
        )

        tags.forEach { tag ->
            val on = tag.id == selectedId

            PillButton(
                label = tag.name,
                color = tag.color,
                ariaLabel = "Filter by ${tag.name}",
                onClick = { onSelect(tag.id) },
                // Two shades rather than one, which `SwatchShade` names for exactly
                // this: the fill carries part of the on/off signal, and the rest is
                // keel's dim and ring. A tag's colour is the user's own and goes in
                // as stored, which is why it is inline and not a token.
                shade = if (on) SwatchShade.Selected else SwatchShade.Faint,
                emoji = tag.emoji,
                selected = on,
            )
        }
    }
}

@Composable
private fun LoadingTasks() {
    Div({ classes("tasks__notice") }) {
        // keel's `Spinner`, a ring at the same 1.25rem the rotated glyph was. Turning
        // an icon in place is what keel's `.spinner` utility is for; this is the other
        // thing, a wait with no measurable end. It carries `role="status"`, so the
        // wait is announced rather than only drawn.
        Spinner()
        Span { Text("Loading tasks...") }
    }
}

@Composable
private fun EmptyTasks(filterTagId: String?) {
    if (filterTagId != null) {
        EmptyState(title = "No tasks with this tag")
    } else {
        EmptyState(title = "No tasks yet", body = "Type above to get started.")
    }
}

@Composable
private fun PendingTasks(
    tasks: TasksController,
    drag: ListDragController,
    onEditTask: (String) -> Unit,
    onViewTask: (String) -> Unit,
) {
    val pending = tasks.pending
    val order = pending.map { it.id }

    Div({ classes("tasks__list") }) {
        pending.forEachIndexed { index, task ->
            // Keyed by id, and it matters more than it looks. Without it Compose
            // reuses a row's element for whatever task now sits at that position, so
            // the `ref` below - which runs once, when the element is created - would
            // go on reporting the element under the id of the task that used to be
            // there. The drag controller would then look up a row that is no longer
            // registered and decide the pointer had not moved at all.
            key(task.id) {
                TaskRow(
                    task = task,
                    tasks = tasks,
                    drag = drag,
                    onEditTask = onEditTask,
                    onViewTask = onViewTask,
                    dragging = drag.isDragging(task.id),
                    onDragStart = {
                        drag.begin(task.id, index, order) { from, to -> tasks.moveTask(from, to) }
                    },
                )
            }
        }
    }
}

/**
 * The finished tasks, under their own heading.
 *
 * Not draggable: their order is whatever it was when they were finished, and a list
 * whose whole purpose is "already dealt with" is not worth arranging.
 */
@Composable
private fun CompletedTasks(
    tasks: TasksController,
    drag: ListDragController,
    onEditTask: (String) -> Unit,
    onViewTask: (String) -> Unit,
) {
    val completed = tasks.completed
    if (completed.isEmpty()) return

    Div({ classes("tasks__completed") }) {
        Div({ classes("tasks__completed-label") }) { Text("Completed · ${completed.size}") }

        Div({ classes("tasks__list") }) {
            completed.forEach { task ->
                key(task.id) {
                    TaskRow(
                        task = task,
                        tasks = tasks,
                        drag = drag,
                        onEditTask = onEditTask,
                        onViewTask = onViewTask,
                        dragging = false,
                        onDragStart = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    tasks: TasksController,
    drag: ListDragController,
    onEditTask: (String) -> Unit,
    onViewTask: (String) -> Unit,
    dragging: Boolean,
    onDragStart: (() -> Unit)?,
) {
    val subtasks = tasks.subtasksOf(task.id)
    val tags = tasks.tagsOf(task)
    val open = tasks.isExpanded(task.id)
    val hasDetail = task.hasDetail(subtasks)

    Div({
        classes(
            *listOfNotNull(
                "task",
                "task--open".takeIf { open },
                "task--dragging".takeIf { dragging },
            ).toTypedArray(),
        )
        ref { element ->
            drag.registerRow(task.id, element)
            onDispose { drag.registerRow(task.id, null) }
        }
    }) {
        Div({ classes("task__row") }) {
            DragHandleOrSpacer(onDragStart)

            Checkbox(
                checked = task.done,
                onCheckedChange = { tasks.toggleDone(task.id) },
                ariaLabel = if (task.done) "Mark as not done" else "Mark as done",
            )

            if (hasDetail) {
                Button({
                    classes(
                        *listOfNotNull("task__chevron", "task__chevron--open".takeIf { open })
                            .toTypedArray(),
                    )
                    attr("aria-expanded", open.toString())
                    attr("aria-label", if (open) "Hide details" else "Show details")
                    onClick { tasks.toggleExpanded(task.id) }
                }) {
                    Icon(LucideIcon.ChevronRight, size = ICON_TINY)
                }
            } else {
                Div({ classes("task__spacer") })
            }

            Div({
                classes("task__title")
                onClick { onViewTask(task.id) }
            }) {
                FormattedText(
                    text = task.text,
                    extraClasses = listOfNotNull(
                        "task__text",
                        "task__text--done".takeIf { task.done },
                    ),
                )

                // Only while collapsed: expanding shows the same things with room
                // around them, and both at once would be the same information twice.
                if (!open) {
                    tags.forEach { tag ->
                        Pill(
                            label = tag.name,
                            color = tag.color,
                            shade = SwatchShade.Inline,
                            emoji = tag.emoji,
                            size = PillSize.Inline,
                        )
                    }

                    if (subtasks.isNotEmpty()) {
                        // keel's `Badge` at `Neutral`, which its own docs call "a
                        // fact with no judgement attached: a count". The same fill,
                        // ink, padding and type size as the local rule; what changes
                        // is a squarer corner and a bolder, letterspaced figure.
                        Badge(label = subtaskProgress(subtasks))
                    }
                }
            }

            EditRowButton(ariaLabel = "Edit task", onClick = { onEditTask(task.id) })
        }

        if (open) {
            TaskDetail(task = task, tags = tags, subtasks = subtasks, tasks = tasks)
        }
    }
}

/**
 * What a row shows when it is opened: the tags, the notes, and the subtasks.
 *
 * Read-only except for ticking a subtask off, which is the one thing worth doing
 * without opening a dialog.
 */
@Composable
private fun TaskDetail(task: Task, tags: List<Tag>, subtasks: List<Task>, tasks: TasksController) {
    Div({ classes("task__detail") }) {
        if (tags.isNotEmpty()) {
            Div({ classes("task__tags") }) {
                tags.forEach { tag ->
                    Pill(label = tag.name, color = tag.color, emoji = tag.emoji)
                }
            }
        }

        task.body?.let { body ->
            P({ classes("task__body") }) { FormattedText(body) }
        }

        if (subtasks.isNotEmpty()) {
            Div({ classes("task__subtasks") }) {
                subtasks.forEach { subtask ->
                    key(subtask.id) {
                        Div({ classes("subtask") }) {
                            Checkbox(
                                checked = subtask.done,
                                onCheckedChange = { tasks.toggleDone(subtask.id) },
                                ariaLabel = if (subtask.done) {
                                    "Mark as not done"
                                } else {
                                    "Mark as done"
                                },
                                size = CheckboxSize.Small,
                            )
                            FormattedText(
                                text = subtask.text,
                                extraClasses = listOfNotNull(
                                    "subtask__text",
                                    "subtask__text--done".takeIf { subtask.done },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/* `TaskCheckbox` used to live here: a button carrying `role="checkbox"`, a tick hidden
   by colour, and a `--small` size for a subtask row. That is keel's `Checkbox`, down to
   the two tick sizes - it was the thing keel's was built from. The one difference is
   which attribute marks a ticked box: keel reads `aria-checked`, where this read a
   `--done` class beside it, so the state the eye sees and the state a screen reader
   hears can no longer drift apart. */

/**
 * A row's edit button: absent until the row is hovered.
 *
 * keel's `IconButton` at `IconExtraSmall`, which is the same 1.75rem box the local
 * rule drew - `--control-h-xs` has been that size since keel's own card headers were
 * written. `Quiet` is the variant for a control that is muted until reached for.
 *
 * The one thing left in `cards.css` is the part keel cannot know: at rest this button
 * is not dim, it is *invisible*, and only its row being hovered brings it out.
 */
@Composable
internal fun EditRowButton(ariaLabel: String, onClick: () -> Unit) {
    IconButton(
        ariaLabel = ariaLabel,
        onClick = onClick,
        variant = ButtonVariant.Quiet,
        size = ButtonSize.IconExtraSmall,
        attrs = { classNames("task__edit") },
    ) {
        Icon(LucideIcon.Pencil, size = ICON_TINY)
    }
}

/**
 * The grip, or the space one would take.
 *
 * A spacer rather than nothing, so that a finished task's checkbox lines up with the
 * unfinished ones above it instead of shifting left.
 */
@Composable
internal fun DragHandleOrSpacer(onDragStart: (() -> Unit)?) {
    if (onDragStart == null) {
        Div({ classes("task__spacer") })
        return
    }

    Span({
        classes("task__grip")
        onMouseDown { event ->
            // Left button only: a right-click belongs to the context menu.
            if (event.button.toInt() == 0) {
                event.preventDefault()
                onDragStart()
            }
        }
        // Touch reports no button, and the default here is the page scrolling under
        // the finger instead of the row following it.
        onTouchStart { event ->
            event.preventDefault()
            onDragStart()
        }
    }) {
        Icon(LucideIcon.GripVertical, size = ICON_SMALL)
    }
}

internal const val ICON_MICRO = 10
internal const val ICON_TINY = 12
internal const val ICON_SMALL = 16
