package io.github.dayboard.domain.model

/**
 * One note: a title, some text, and any tags put on it.
 *
 * Flat, unlike a task - notes have no subtasks, nothing to tick off, and no
 * finished group. What they share with tasks is the tag vocabulary and the inline
 * formatting, both of which live in one place and serve both.
 */
data class Note(
    override val id: String,
    val title: String,
    val body: String? = null,
    override val position: Int = 0,
    val tagIds: List<String> = emptyList(),
) : Positioned

/** Shown for a note whose title was cleared. */
const val UNTITLED_NOTE: String = "Untitled"

/**
 * The title to store for what was typed.
 *
 * A note with no title is a row with nothing to click on and nothing to find it by,
 * so an emptied one becomes a placeholder rather than being refused.
 */
fun noteTitleOrFallback(title: String?): String =
    title.orEmpty().trim().ifEmpty { UNTITLED_NOTE }

/** The text to store. Empty means absent, so "has content" is one null check. */
fun normalizeNoteBody(body: String?): String? = body.orEmpty().trim().ifEmpty { null }

/** Whether a note survives the active tag filter. No filter means everything. */
fun Note.matchesTagFilter(tagId: String?): Boolean = tagId == null || tagId in tagIds

/** The notes the filter allows, in the order they are shown. */
fun List<Note>.visibleNotes(tagId: String?): List<Note> =
    filter { it.matchesTagFilter(tagId) }.sortedBy { it.position }

/**
 * Whether a row has anything worth expanding to see.
 *
 * The chevron only appears when this is true, so a bare one-line note has no
 * control that does nothing.
 */
fun Note.hasDetail(): Boolean = body != null || tagIds.isNotEmpty()

/** Where a new note goes: after everything already there, filtered out or not. */
fun nextNotePosition(notes: List<Note>): Int = (notes.maxOfOrNull { it.position } ?: -1) + 1

/** Removes a note. Nothing is renumbered; positions are allowed to be sparse. */
fun List<Note>.withNoteRemoved(noteId: String): List<Note> = filterNot { it.id == noteId }

/** Replaces one note, leaving the rest alone. Unchanged if it is already gone. */
fun List<Note>.withNoteUpdated(noteId: String, transform: (Note) -> Note): List<Note> =
    map { if (it.id == noteId) transform(it) else it }

/** Applies a map of new positions to the notes it names. */
fun List<Note>.withPositions(positions: Map<String, Int>): List<Note> = map { note ->
    val position = positions[note.id]
    if (position == null) note else note.copy(position = position)
}
