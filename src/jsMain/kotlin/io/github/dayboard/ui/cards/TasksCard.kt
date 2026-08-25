package io.github.dayboard.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.bchmsl.keel.color.SwatchShade
import io.github.bchmsl.keel.components.FormattedText
import io.github.bchmsl.keel.icons.Icon
import io.github.bchmsl.keel.icons.LucideIcon
import io.github.dayboard.data.ListDragController
import io.github.dayboard.data.TasksController
import io.github.dayboard.domain.model.Tag
import io.github.dayboard.domain.model.Task
import io.github.dayboard.domain.model.background
import io.github.dayboard.domain.model.hasDetail
import io.github.dayboard.domain.model.subtaskProgress
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.onSubmit as onSubmitAttribute
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Form
import org.jetbrains.compose.web.dom.Input
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
        Input(InputType.Text) {
            classes("tasks__add-input")
            value(draft)
            placeholder("Add a new task...")
            attr("aria-label", "Add a new task")
            onInput { event -> onDraftChange(event.value) }
        }

        Button({
            classes("tasks__add-button")
            attr("type", "submit")
            attr("aria-label", "Add task")
            // Nothing to add is not an error worth explaining, so the button simply
            // is not available until there is something to add.
            if (draft.isBlank()) attr("disabled", "")
        }) {
            Icon(LucideIcon.Plus, size = ICON_SMALL)
        }
    }
}

@Composable
private fun TagFilterRow(tags: List<Tag>, selectedId: String?, onSelect: (String?) -> Unit) {
    Div({ classes("tasks__filters") }) {
        Icon(LucideIcon.Filter, size = ICON_TINY, className = "tasks__filter-icon")

        Button({
            classes(
                *listOfNotNull("chip", "chip--all", "chip--on".takeIf { selectedId == null })
                    .toTypedArray(),
            )
            attr("aria-pressed", (selectedId == null).toString())
            onClick { onSelect(null) }
        }) {
            Text("All")
        }

        tags.forEach { tag ->
            val on = tag.id == selectedId

            Button({
                classes(*listOfNotNull("chip", "chip--on".takeIf { on }).toTypedArray())
                attr("aria-pressed", on.toString())
                style {
                    // The one colour in the app that is not a theme token: it is the
                    // user's own choice and has to survive exactly as stored, which
                    // is why it goes in as a plain string.
                    property(
                        "background-color",
                        tag.background(if (on) SwatchShade.Selected else SwatchShade.Faint),
                    )
                    property("color", tag.color)
                }
                onClick { onSelect(tag.id) }
            }) {
                tag.emoji?.let { Span({ classes("chip__emoji") }) { Text(it) } }
                Text(tag.name)
            }
        }
    }
}

@Composable
private fun LoadingTasks() {
    Div({ classes("tasks__notice") }) {
        Icon(LucideIcon.LoaderCircle, size = ICON_MEDIUM, className = "spinner")
        Span { Text("Loading tasks...") }
    }
}

@Composable
private fun EmptyTasks(filterTagId: String?) {
    Div({ classes("tasks__notice") }) {
        Text(
            if (filterTagId != null) {
                "No tasks with this tag."
            } else {
                "No tasks yet — type above to get started."
            },
        )
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

            TaskCheckbox(
                done = task.done,
                label = if (task.done) "Mark as not done" else "Mark as done",
                onToggle = { tasks.toggleDone(task.id) },
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
                        Span({
                            classes("pill", "pill--inline")
                            style {
                                property("background-color", tag.background(SwatchShade.Inline))
                                property("color", tag.color)
                            }
                        }) {
                            tag.emoji?.let { Span({ classes("pill__emoji") }) { Text(it) } }
                            Text(tag.name)
                        }
                    }

                    if (subtasks.isNotEmpty()) {
                        Span({ classes("task__progress") }) { Text(subtaskProgress(subtasks)) }
                    }
                }
            }

            Button({
                classes("task__edit")
                attr("aria-label", "Edit")
                onClick { onEditTask(task.id) }
            }) {
                Icon(LucideIcon.Pencil, size = ICON_TINY)
            }
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
                    Span({
                        classes("pill")
                        style {
                            property("background-color", tag.background(SwatchShade.Pill))
                            property("color", tag.color)
                        }
                    }) {
                        tag.emoji?.let { Span({ classes("pill__emoji") }) { Text(it) } }
                        Text(tag.name)
                    }
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
                            TaskCheckbox(
                                done = subtask.done,
                                label = if (subtask.done) "Mark as not done" else "Mark as done",
                                onToggle = { tasks.toggleDone(subtask.id) },
                                small = true,
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

@Composable
internal fun TaskCheckbox(
    done: Boolean,
    label: String,
    onToggle: () -> Unit,
    small: Boolean = false,
) {
    Button({
        classes(
            *listOfNotNull(
                "checkbox",
                "checkbox--done".takeIf { done },
                "checkbox--small".takeIf { small },
            ).toTypedArray(),
        )
        attr("role", "checkbox")
        attr("aria-checked", done.toString())
        attr("aria-label", label)
        onClick { onToggle() }
    }) {
        Icon(LucideIcon.Check, size = if (small) ICON_MICRO else ICON_TINY)
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
internal const val ICON_MEDIUM = 20
