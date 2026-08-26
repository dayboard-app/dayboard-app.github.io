package io.github.dayboard.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.bchmsl.keel.color.SwatchShade
import io.github.bchmsl.keel.components.Button
import io.github.bchmsl.keel.components.ButtonSize
import io.github.bchmsl.keel.components.ButtonVariant
import io.github.bchmsl.keel.components.Dialog
import io.github.bchmsl.keel.components.FormattedText
import io.github.bchmsl.keel.components.FormattingField
import io.github.bchmsl.keel.icons.Icon
import io.github.bchmsl.keel.icons.LucideIcon
import io.github.dayboard.data.NotesController
import io.github.dayboard.domain.model.background
import io.github.dayboard.ui.cards.ICON_TINY
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Writing a note: the title, the text, its tags, and deleting it.
 *
 * The note is looked up by id on every composition rather than passed in as a
 * value, so a change from another device shows through while the dialog is open
 * instead of being hidden behind a stale copy.
 */
@Composable
fun NoteEditDialog(noteId: String, notes: NotesController, onDismiss: () -> Unit) {
    val note = notes.noteById(noteId)
    if (note == null) {
        // Deleted, here or elsewhere. There is nothing left to edit.
        onDismiss()
        return
    }

    Dialog(
        title = "Edit Note",
        description = "Edit note details and tags",
        onDismiss = onDismiss,
    ) {
        Div({ classes("editor") }) {
            Div({ classes("editor__section") }) {
                Div({ classes("editor__label") }) { Text("Title") }
                FormattingField(
                    resetKey = note.id,
                    initial = note.title,
                    ariaLabel = "Title",
                    onCommit = { notes.setTitle(note.id, it) },
                )
            }

            Div({ classes("editor__section") }) {
                Div({ classes("editor__label") }) { Text("Content") }
                FormattingField(
                    resetKey = note.id,
                    initial = note.body.orEmpty(),
                    ariaLabel = "Content",
                    placeholder = "Write your note...",
                    multiline = true,
                    textRows = NOTE_ROWS,
                    onCommit = { notes.setBody(note.id, it) },
                )
            }

            TagsSection(
                attached = notes.tagsOf(note),
                available = notes.allTags.filterNot { it.id in note.tagIds },
                onToggle = { tagId -> notes.toggleTag(note.id, tagId) },
                onCreate = { name, color, emoji -> notes.createTag(note.id, name, color, emoji) },
            )

            DeleteNoteSection(
                onDelete = {
                    notes.deleteNote(note.id)
                    onDismiss()
                },
            )
        }
    }
}

/**
 * Deleting the note, behind one confirmation.
 *
 * Inline rather than a modal on top of a modal, matching the task editor: a dialog
 * over a dialog is awkward to escape and easy to dismiss by accident, which is the
 * opposite of what a confirmation is for.
 */
@Composable
private fun DeleteNoteSection(onDelete: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }

    Div({ classes("editor__delete") }) {
        if (confirming) {
            Span({ classes("editor__warning") }) { Text("Delete this note?") }
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
                label = "Delete note",
                onClick = { confirming = true },
                variant = ButtonVariant.QuietDestructive,
                size = ButtonSize.ExtraSmall,
                leading = { Icon(LucideIcon.Trash2, size = ICON_TINY) },
            )
        }
    }
}

/**
 * A note as it reads rather than as it is written.
 *
 * The whole text, with its formatting applied and its links live, where the editor
 * shows the raw markers. Nothing here can be changed.
 */
@Composable
fun NoteViewDialog(
    noteId: String,
    notes: NotesController,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val note = notes.noteById(noteId)
    if (note == null) {
        onDismiss()
        return
    }

    val tags = notes.tagsOf(note)

    Dialog(title = note.title, description = "View note content", onDismiss = onDismiss) {
        Div({ classes("viewer") }) {
            Div({ classes("viewer__title", "viewer__title--note") }) { FormattedText(note.title) }

            if (tags.isNotEmpty()) {
                Div({ classes("viewer__tags") }) {
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

            val body = note.body
            if (body == null) {
                P({ classes("viewer__empty") }) { Text("No content yet.") }
            } else {
                P({ classes("viewer__body", "viewer__body--note") }) { FormattedText(body) }
            }

            Div({ classes("viewer__actions") }) {
                Button(
                    label = "Edit note",
                    onClick = onEdit,
                    size = ButtonSize.ExtraSmall,
                    leading = { Icon(LucideIcon.Pencil, size = ICON_TINY) },
                )
            }
        }
    }
}

/** A note is written in, not annotated, so its field starts twice the height. */
private const val NOTE_ROWS = 12
