package io.github.dayboard.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.bchmsl.keel.color.SwatchShade
import io.github.bchmsl.keel.components.FormattedText
import io.github.bchmsl.keel.components.Pill
import io.github.bchmsl.keel.components.PillSize
import io.github.bchmsl.keel.icons.Icon
import io.github.bchmsl.keel.icons.LucideIcon
import io.github.dayboard.data.ListDragController
import io.github.dayboard.data.NotesController
import io.github.dayboard.domain.model.Note
import io.github.dayboard.domain.model.Tag
import io.github.dayboard.domain.model.hasDetail
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
 * The note list: an add box, a tag filter, and the notes.
 *
 * Simpler than the task list by design - nothing to tick off, so no finished group
 * and no cascade. A row opens in place to show its tags and the first couple of
 * lines; the full text is a click further in.
 */
@Composable
fun NotesCard(
    notes: NotesController,
    drag: ListDragController,
    expanded: Boolean,
    onEditNote: (String) -> Unit,
    onViewNote: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Div({ classes(*sized("notes", expanded)) }) {
        AddNoteForm(
            draft = draft,
            onDraftChange = { draft = it },
            onSubmit = {
                notes.addNote(draft)?.let { added ->
                    draft = ""
                    // Straight into the editor: a note is created by typing a title,
                    // and the text itself is written in the dialog.
                    onEditNote(added)
                }
            },
        )

        if (notes.allTags.isNotEmpty()) {
            TagFilterRow(
                tags = notes.allTags,
                selectedId = notes.filterTagId,
                onSelect = notes::setFilter,
            )
        }

        when {
            !notes.loaded -> NotesNotice {
                Icon(LucideIcon.LoaderCircle, size = ICON_MEDIUM, className = "spinner")
                Span { Text("Loading notes...") }
            }

            notes.visible.isEmpty() -> NotesNotice {
                Text(
                    if (notes.filterTagId != null) {
                        "No notes with this tag."
                    } else {
                        "No notes yet — type above to get started."
                    },
                )
            }

            else -> NoteRows(notes, drag, onEditNote, onViewNote)
        }
    }
}

@Composable
private fun AddNoteForm(draft: String, onDraftChange: (String) -> Unit, onSubmit: () -> Unit) {
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
            placeholder("Add a new note...")
            attr("aria-label", "Add a new note")
            onInput { event -> onDraftChange(event.value) }
        }

        Button({
            classes("tasks__add-button")
            attr("type", "submit")
            attr("aria-label", "Add note")
            if (draft.isBlank()) attr("disabled", "")
        }) {
            Icon(LucideIcon.Plus, size = ICON_SMALL)
        }
    }
}

/* `NoteFilterRow` used to live here, and it was `TagFilterRow` again: the same row,
   over the same tag vocabulary, differing only in which controller it called. Once
   both were keel's `PillButton` the two bodies were identical, so the tasks card's is
   the one that remains. */

@Composable
private fun NotesNotice(content: @Composable () -> Unit) {
    Div({ classes("tasks__notice") }) { content() }
}

@Composable
private fun NoteRows(
    notes: NotesController,
    drag: ListDragController,
    onEditNote: (String) -> Unit,
    onViewNote: (String) -> Unit,
) {
    val visible = notes.visible
    val order = visible.map { it.id }

    Div({ classes("tasks__list") }) {
        visible.forEachIndexed { index, note ->
            // Keyed by id: Compose reuses a row's element for whatever note now sits
            // in that position, and the `ref` that registers it with the drag
            // controller runs only at creation - so without this, a drag after the
            // filter changed would measure itself against the wrong rows.
            key(note.id) {
                NoteRow(
                    note = note,
                    notes = notes,
                    drag = drag,
                    onEditNote = onEditNote,
                    onViewNote = onViewNote,
                    onDragStart = {
                        drag.begin(note.id, index, order) { from, to -> notes.moveNote(from, to) }
                    },
                )
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    notes: NotesController,
    drag: ListDragController,
    onEditNote: (String) -> Unit,
    onViewNote: (String) -> Unit,
    onDragStart: () -> Unit,
) {
    val tags = notes.tagsOf(note)
    val open = notes.isExpanded(note.id)

    Div({
        classes(
            *listOfNotNull(
                "task",
                "task--open".takeIf { open },
                "task--dragging".takeIf { drag.isDragging(note.id) },
            ).toTypedArray(),
        )
        ref { element ->
            drag.registerRow(note.id, element)
            onDispose { drag.registerRow(note.id, null) }
        }
    }) {
        Div({ classes("task__row") }) {
            DragHandleOrSpacer(onDragStart)

            if (note.hasDetail()) {
                Button({
                    classes(
                        *listOfNotNull("task__chevron", "task__chevron--open".takeIf { open })
                            .toTypedArray(),
                    )
                    attr("aria-expanded", open.toString())
                    attr("aria-label", if (open) "Hide details" else "Show details")
                    onClick { notes.toggleExpanded(note.id) }
                }) {
                    Icon(LucideIcon.ChevronRight, size = ICON_TINY)
                }
            } else {
                Div({ classes("task__spacer") })
            }

            Div({
                classes("task__title")
                onClick { onViewNote(note.id) }
            }) {
                FormattedText(text = note.title, extraClasses = listOf("task__text"))

                // Only while collapsed: expanding shows the same tags with room
                // around them, and both at once would be the same thing twice.
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
                }
            }

            Button({
                classes("task__edit")
                attr("aria-label", "Edit")
                onClick { onEditNote(note.id) }
            }) {
                Icon(LucideIcon.Pencil, size = ICON_TINY)
            }
        }

        if (open) {
            NoteDetail(note = note, tags = tags, onViewNote = { onViewNote(note.id) })
        }
    }
}

@Composable
private fun NoteDetail(note: Note, tags: List<Tag>, onViewNote: () -> Unit) {
    Div({ classes("task__detail") }) {
        if (tags.isNotEmpty()) {
            Div({ classes("task__tags") }) {
                tags.forEach { tag ->
                    Pill(label = tag.name, color = tag.color, emoji = tag.emoji)
                }
            }
        }

        note.body?.let { body ->
            // Two lines and no more. A note can be pages long, and a list where one
            // row is a wall of text is a list nobody can scan.
            P({
                classes("task__body", "note__preview")
                attr("title", "Click to view full note")
                onClick { onViewNote() }
            }) {
                FormattedText(body)
            }
        }
    }
}
