package io.github.dayboard.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dayboard.domain.model.Note
import io.github.dayboard.domain.model.Tag
import io.github.dayboard.domain.model.nextNotePosition
import io.github.dayboard.domain.model.normalizeNoteBody
import io.github.dayboard.domain.model.noteTitleOrFallback
import io.github.dayboard.domain.model.reorderVisible
import io.github.dayboard.domain.model.visibleNotes
import io.github.dayboard.domain.model.withNoteRemoved
import io.github.dayboard.domain.model.withNoteUpdated
import io.github.dayboard.domain.model.withPositions
import io.github.dayboard.domain.repository.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The note list: what is in it, and what happens when it is changed.
 *
 * The same shape as [TasksController] and for the same reasons - optimistic first,
 * write afterwards, with every rule about *what* a change produces living in
 * `:shared`. Notes are the simpler of the two: no subtasks, nothing to tick off,
 * and no finished group.
 *
 * Tags come from [TagsController], which the tasks read too, so a tag made on a
 * task can go on a note straight away. One vocabulary for the whole board.
 */
class NotesController(
    private val notes: NoteRepository,
    private val tags: TagsController,
    private val scope: CoroutineScope,
) {

    var allNotes: List<Note> by mutableStateOf(emptyList())
        private set

    var loaded: Boolean by mutableStateOf(false)
        private set

    /** The account's tags, shared with the tasks. */
    val allTags: List<Tag> get() = tags.all

    /** The tag being filtered by, or null for all of them. One at a time. */
    var filterTagId: String? by mutableStateOf(null)
        private set

    private var expandedIds: Set<String> by mutableStateOf(emptySet())

    private var uid: String? = null
    private var stopNotes: (() -> Unit)? = null

    // ------------------------------------------------------------------ reading

    /** The notes the filter allows, in order. */
    val visible: List<Note> get() = allNotes.visibleNotes(filterTagId)

    fun noteById(noteId: String): Note? = allNotes.firstOrNull { it.id == noteId }

    /** A note's tags, in the order it carries them, skipping any that no longer exist. */
    fun tagsOf(note: Note): List<Tag> =
        note.tagIds.mapNotNull { id -> allTags.firstOrNull { it.id == id } }

    fun isExpanded(noteId: String): Boolean = noteId in expandedIds

    // ----------------------------------------------------------------- following

    /** Follows one account's notes. Safe to call repeatedly. */
    fun start(uid: String) {
        if (this.uid == uid) return

        stop()
        this.uid = uid

        stopNotes = notes.observe(uid) { stored ->
            allNotes = stored
            loaded = true
        }
    }

    /** Detaches, and forgets the previous account's notes. */
    fun stop() {
        stopNotes?.invoke()
        stopNotes = null
        uid = null
        allNotes = emptyList()
        filterTagId = null
        expandedIds = emptySet()
        loaded = false
    }

    // ------------------------------------------------------------------ viewing

    fun setFilter(tagId: String?) {
        filterTagId = if (tagId == filterTagId) null else tagId
    }

    fun toggleExpanded(noteId: String) {
        expandedIds = if (noteId in expandedIds) expandedIds - noteId else expandedIds + noteId
    }

    // ----------------------------------------------------------------- changing

    /**
     * Adds a note and returns its id, or null when there was nothing to add.
     *
     * A note added while a filter is on takes that tag, so it does not disappear the
     * instant it is created - which would look exactly like the add having failed.
     */
    fun addNote(title: String): String? {
        val text = title.trim().ifEmpty { return null }
        val account = uid ?: return null

        val note = Note(
            id = newId(),
            title = text,
            position = nextNotePosition(allNotes),
            tagIds = listOfNotNull(filterTagId),
        )

        allNotes = allNotes + note
        scope.launch { notes.save(account, note) }
        return note.id
    }

    fun setTitle(noteId: String, title: String) {
        updateNote(noteId) { it.copy(title = noteTitleOrFallback(title)) }
    }

    fun setBody(noteId: String, body: String?) {
        updateNote(noteId) { it.copy(body = normalizeNoteBody(body)) }
    }

    fun deleteNote(noteId: String) {
        val account = uid ?: return

        allNotes = allNotes.withNoteRemoved(noteId)
        expandedIds = expandedIds - noteId
        scope.launch { notes.delete(account, noteId) }
    }

    /**
     * Commits a finished drag. Indices are positions in the visible list.
     *
     * Uses the same position pool as the task list, which is a deliberate
     * difference from the original: that renumbers the visible notes 0, 1, 2 even
     * under a filter, which hands them positions the hidden notes are already using.
     * Two notes sharing a position sort in an arbitrary order, so the list would
     * quietly rearrange itself later. Pooling reuses only the numbers already held
     * by the notes on screen, so nothing hidden can be disturbed.
     */
    fun moveNote(fromIndex: Int, toIndex: Int) {
        val positions = reorderVisible(visible, fromIndex, toIndex)
        if (positions.isEmpty()) return
        val account = uid ?: return

        allNotes = allNotes.withPositions(positions)
        val moved = allNotes.filter { it.id in positions }
        scope.launch { notes.saveAll(account, moved) }
    }

    // --------------------------------------------------------------------- tags

    /** Puts a tag on a note, or takes it off. */
    fun toggleTag(noteId: String, tagId: String) {
        updateNote(noteId) { note ->
            val tagIds = if (tagId in note.tagIds) note.tagIds - tagId else note.tagIds + tagId
            note.copy(tagIds = tagIds)
        }
    }

    /**
     * Creates a tag and puts it on a note, or attaches one that already exists.
     *
     * The making of it belongs to [TagsController], which owns the vocabulary the
     * tasks share; all this decides is that the result goes on this note.
     */
    fun createTag(noteId: String, name: String, color: String, emoji: String?) {
        val tag = tags.createOrFind(name, color, emoji) ?: return

        if (tag.id !in (noteById(noteId)?.tagIds ?: emptyList())) toggleTag(noteId, tag.id)
    }

    // ------------------------------------------------------------------ private

    private fun updateNote(noteId: String, transform: (Note) -> Note) {
        val account = uid ?: return

        allNotes = allNotes.withNoteUpdated(noteId, transform)
        val updated = noteById(noteId) ?: return
        scope.launch { notes.save(account, updated) }
    }
}
