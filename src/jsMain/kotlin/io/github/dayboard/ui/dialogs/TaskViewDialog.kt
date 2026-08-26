package io.github.dayboard.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import io.github.bchmsl.keel.components.Button
import io.github.bchmsl.keel.components.ButtonSize
import io.github.bchmsl.keel.components.Checkbox
import io.github.bchmsl.keel.components.Dialog
import io.github.bchmsl.keel.components.FormattedText
import io.github.bchmsl.keel.components.Pill
import io.github.bchmsl.keel.icons.Icon
import io.github.bchmsl.keel.icons.LucideIcon
import io.github.dayboard.data.ListDragController
import io.github.dayboard.data.TasksController
import io.github.dayboard.ui.cards.DragHandleOrSpacer
import io.github.dayboard.ui.cards.ICON_TINY
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * A task as it reads rather than as it is edited.
 *
 * Opened by clicking a task's title, which is the gesture for "what is this again?".
 * Everything is rendered with its formatting applied and its links live, where the
 * edit dialog shows the raw markers.
 *
 * The two things that can be changed from here are ticking a subtask off and
 * reordering the subtasks - the two that are useful while reading rather than while
 * writing.
 */
@Composable
fun TaskViewDialog(
    taskId: String,
    tasks: TasksController,
    drag: ListDragController,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val task = tasks.taskById(taskId)
    if (task == null) {
        onDismiss()
        return
    }

    val tags = tasks.tagsOf(task)
    val subtasks = tasks.subtasksOf(task.id)
    val order = subtasks.map { it.id }

    Dialog(title = task.text, description = "View task details", onDismiss = onDismiss) {
        Div({ classes("viewer") }) {
            Div({ classes("viewer__title") }) { FormattedText(task.text) }

            if (tags.isNotEmpty()) {
                Div({ classes("viewer__tags") }) {
                    tags.forEach { tag ->
                        Pill(label = tag.name, color = tag.color, emoji = tag.emoji)
                    }
                }
            }

            task.body
                ?.let { body -> P({ classes("viewer__body") }) { FormattedText(body) } }
                ?: P({ classes("viewer__no-notes") }) { Text("No notes.") }

            if (subtasks.isNotEmpty()) {
                Div({ classes("viewer__label") }) {
                    Text("Subtasks · ${subtasks.count { it.done }}/${subtasks.size}")
                }

                subtasks.forEachIndexed { index, subtask ->
                    // Keyed so a reused element cannot leave the drag controller
                    // holding the id of the subtask that used to be in this slot.
                    key(subtask.id) {
                        Div({
                        classes(
                            *listOfNotNull(
                                "subtask",
                                "subtask--dragging".takeIf { drag.isDragging(subtask.id) },
                            ).toTypedArray(),
                        )
                        ref { element ->
                            drag.registerRow(subtask.id, element)
                            onDispose { drag.registerRow(subtask.id, null) }
                        }
                    }) {
                        DragHandleOrSpacer {
                            drag.begin(subtask.id, index, order) { from, to ->
                                tasks.moveSubtask(task.id, from, to)
                            }
                        }

                        Checkbox(
                            checked = subtask.done,
                            onCheckedChange = { tasks.toggleDone(subtask.id) },
                            ariaLabel = if (subtask.done) {
                                "Mark as not done"
                            } else {
                                "Mark as done"
                            },
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

            Div({ classes("viewer__actions") }) {
                Button(
                    label = "Edit task",
                    onClick = onEdit,
                    size = ButtonSize.ExtraSmall,
                    leading = { Icon(LucideIcon.Pencil, size = ICON_TINY) },
                )
            }
        }
    }
}
