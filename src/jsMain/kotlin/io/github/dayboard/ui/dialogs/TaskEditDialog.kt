package io.github.dayboard.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.bchmsl.keel.components.Button
import io.github.bchmsl.keel.components.ButtonSize
import io.github.bchmsl.keel.components.ButtonVariant
import io.github.bchmsl.keel.components.Dialog
import io.github.bchmsl.keel.components.FormattingField
import io.github.bchmsl.keel.components.IconButton
import io.github.bchmsl.keel.components.TextField
import io.github.bchmsl.keel.dom.classNames
import io.github.bchmsl.keel.dom.inputClasses
import io.github.bchmsl.keel.icons.Icon
import io.github.bchmsl.keel.icons.LucideIcon
import io.github.dayboard.data.ListDragController
import io.github.dayboard.data.TasksController
import io.github.dayboard.domain.model.Task
import io.github.dayboard.ui.cards.DragHandleOrSpacer
import io.github.dayboard.ui.cards.ICON_MICRO
import io.github.dayboard.ui.cards.ICON_TINY
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Everything about one task that the list cannot change in place.
 *
 * The task is looked up by id on every composition rather than passed in as a value,
 * so a change arriving from another device shows through while the dialog is open
 * instead of being hidden behind a stale copy.
 *
 * Tags and subtasks are only offered for a top-level task. Nesting stops at one
 * level, and a subtask carries no tags of its own.
 */
@Composable
fun TaskEditDialog(
    taskId: String,
    tasks: TasksController,
    drag: ListDragController,
    onDismiss: () -> Unit,
) {
    val task = tasks.taskById(taskId)
    if (task == null) {
        // The task was deleted, here or elsewhere. There is nothing left to edit.
        onDismiss()
        return
    }

    Dialog(
        title = "Edit Task",
        description = "Edit task details, subtasks, and tags",
        onDismiss = onDismiss,
    ) {
        Div({ classes("editor") }) {
            Div({ classes("editor__section") }) {
                Div({ classes("editor__label") }) { Text("Title") }
                FormattingField(
                    resetKey = task.id,
                    initial = task.text,
                    ariaLabel = "Title",
                    onCommit = { tasks.setTitle(task.id, it) },
                )
            }

            Div({ classes("editor__section") }) {
                Div({ classes("editor__label") }) { Text("Notes") }
                FormattingField(
                    resetKey = task.id,
                    initial = task.body.orEmpty(),
                    ariaLabel = "Notes",
                    placeholder = "Add details or notes...",
                    multiline = true,
                    onCommit = { tasks.setBody(task.id, it) },
                )
            }

            if (task.isTopLevel) {
                TagsSection(
                    attached = tasks.tagsOf(task),
                    available = tasks.allTags.filterNot { it.id in task.tagIds },
                    onToggle = { tagId -> tasks.toggleTag(task.id, tagId) },
                    onCreate = { name, color, emoji ->
                        tasks.createTag(task.id, name, color, emoji)
                    },
                )
                SubtasksSection(task, tasks, drag)
            }

            DeleteSection(
                onDelete = {
                    tasks.deleteTask(task.id)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun SubtasksSection(task: Task, tasks: TasksController, drag: ListDragController) {
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var editingId: String? by remember { mutableStateOf(null) }

    val subtasks = tasks.subtasksOf(task.id)
    val order = subtasks.map { it.id }

    Div({ classes("editor__section") }) {
        Div({ classes("editor__label") }) { Text("Subtasks") }

        subtasks.forEachIndexed { index, subtask ->
            // Keyed for the same reason the task rows are: an element reused for a
            // different subtask would leave the drag controller holding a stale id.
            key(subtask.id) {
                SubtaskRow(
                    subtask = subtask,
                    editing = subtask.id == editingId,
                    onStartEditing = { editingId = subtask.id },
                    onCommit = { text ->
                        editingId = null
                        tasks.setTitle(subtask.id, text)
                    },
                    onCancelEditing = { editingId = null },
                    onDelete = { tasks.deleteTask(subtask.id) },
                    dragging = drag.isDragging(subtask.id),
                    onDragStart = {
                        drag.begin(subtask.id, index, order) { from, to ->
                            tasks.moveSubtask(task.id, from, to)
                        }
                    },
                    registerRow = { element -> drag.registerRow(subtask.id, element) },
                )
            }
        }

        if (subtasks.isEmpty() && !adding) {
            Div({ classes("editor__empty") }) { Text("No subtasks yet.") }
        }

        if (adding) {
            Div({ classes("editor__row") }) {
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = "Subtask title...",
                    ariaLabel = "Subtask title",
                    attrs = {
                        ref { element ->
                            element.focus()
                            onDispose { }
                        }
                        onKeyDown { event ->
                            when (event.key) {
                                "Enter" -> {
                                    event.preventDefault()
                                    tasks.addSubtask(task.id, draft)
                                    // The form stays open: adding subtasks is something
                                    // people do several of in a row.
                                    draft = ""
                                }

                                "Escape" -> {
                                    // Stops propagation, or this would also reach the
                                    // dialog's own Escape listener and close the whole
                                    // dialog along with abandoning this one field.
                                    event.stopPropagation()
                                    adding = false
                                    draft = ""
                                }
                            }
                        }
                    },
                )
                Button(
                    label = "Add",
                    onClick = {
                        tasks.addSubtask(task.id, draft)
                        draft = ""
                    },
                    size = ButtonSize.ExtraSmall,
                    enabled = draft.isNotBlank(),
                )
            }
        } else {
            Button(
                label = "Add subtask",
                onClick = { adding = true },
                variant = ButtonVariant.Quiet,
                size = ButtonSize.ExtraSmall,
                attrs = { classNames("editor__inline-action") },
                leading = { Icon(LucideIcon.Plus, size = ICON_TINY) },
            )
        }
    }
}

@Composable
private fun SubtaskRow(
    subtask: Task,
    editing: Boolean,
    onStartEditing: () -> Unit,
    onCommit: (String) -> Unit,
    onCancelEditing: () -> Unit,
    onDelete: () -> Unit,
    dragging: Boolean,
    onDragStart: () -> Unit,
    registerRow: (org.w3c.dom.HTMLElement?) -> Unit,
) {
    Div({
        classes(*listOfNotNull("subtask", "subtask--dragging".takeIf { dragging }).toTypedArray())
        ref { element ->
            registerRow(element)
            onDispose { registerRow(null) }
        }
    }) {
        DragHandleOrSpacer(onDragStart)

        // A span rather than a button: whether a subtask is done is not editable
        // here. It is shown so the list reads the same as everywhere else, and
        // changed from the list or the view dialog.
        Span({
            classes(
                *listOfNotNull(
                    "checkbox",
                    "checkbox--small",
                    "checkbox--done".takeIf { subtask.done },
                ).toTypedArray(),
            )
            attr("aria-hidden", "true")
        }) {
            Icon(LucideIcon.Check, size = ICON_MICRO)
        }

        if (editing) {
            // A raw `Input` rather than keel's `TextField`, which binds `value` to
            // state. This field is deliberately uncontrolled: the row's text is
            // written once through `ref` and read back on Enter or blur, so typing is
            // never at the mercy of a recomposition. The class still comes from keel.
            Input(InputType.Text) {
                classNames(inputClasses(), "subtask__input")
                attr("aria-label", "Subtask title")
                ref { element ->
                    element.value = subtask.text
                    element.focus()
                    element.select()
                    onDispose { }
                }
                onKeyDown { event ->
                    val element = event.target as? org.w3c.dom.HTMLInputElement
                    when (event.key) {
                        "Enter" -> {
                            event.preventDefault()
                            onCommit(element?.value.orEmpty())
                        }
                        // Escape abandons the edit. It has to be checked before the
                        // blur handler runs, which is why cancelling clears the
                        // editing state rather than committing. Stopped here too, or
                        // it would also close the dialog this row sits inside.
                        "Escape" -> {
                            event.stopPropagation()
                            onCancelEditing()
                        }
                    }
                }
                onBlur { event ->
                    val element = event.target as? org.w3c.dom.HTMLInputElement
                    onCommit(element?.value.orEmpty())
                }
            }
        } else {
            Span({
                classes(
                    *listOfNotNull("subtask__text", "subtask__text--done".takeIf { subtask.done })
                        .toTypedArray(),
                )
                onClick { onStartEditing() }
            }) {
                Text(subtask.text)
            }
        }

        IconButton(
            ariaLabel = "Delete subtask",
            onClick = onDelete,
            variant = ButtonVariant.QuietDestructive,
            size = ButtonSize.IconExtraSmall,
            attrs = { classNames("subtask__delete") },
        ) {
            Icon(LucideIcon.X, size = ICON_TINY)
        }
    }
}

/**
 * Deleting the task, behind one confirmation.
 *
 * Inline rather than a second dialog on top of the first, matching the original. A
 * modal over a modal is hard to escape from and easy to dismiss by accident, which
 * is the opposite of what a confirmation is for.
 */
@Composable
private fun DeleteSection(onDelete: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }

    Div({ classes("editor__delete") }) {
        if (confirming) {
            Span({ classes("editor__warning") }) { Text("Delete this task and all its subtasks?") }
            Button(
                label = "Delete",
                onClick = onDelete,
                variant = ButtonVariant.Destructive,
                size = ButtonSize.ExtraSmall,
            )
            Button(
                label = "Cancel",
                onClick = { confirming = false },
                variant = ButtonVariant.Quiet,
                size = ButtonSize.ExtraSmall,
            )
        } else {
            // `QuietDestructive`, not `Destructive`: this press only asks the
            // question. The solid red belongs on the one above, which answers it.
            Button(
                label = "Delete task",
                onClick = { confirming = true },
                variant = ButtonVariant.QuietDestructive,
                size = ButtonSize.ExtraSmall,
                leading = { Icon(LucideIcon.Trash2, size = ICON_TINY) },
            )
        }
    }
}
