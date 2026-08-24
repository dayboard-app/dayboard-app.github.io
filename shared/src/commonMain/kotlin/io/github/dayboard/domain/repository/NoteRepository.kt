package io.github.dayboard.domain.repository

import io.github.dayboard.domain.model.Note

/**
 * Where notes live.
 *
 * The same shape as [TaskRepository], and for the same reasons: whole values rather
 * than field-by-field writes, and the whole set on every change rather than a delta.
 */
interface NoteRepository {

    /**
     * Follows one account's notes.
     *
     * Changes this device made are not reported back; the caller has applied them
     * already, and re-applying would fight whatever is being typed.
     */
    fun observe(uid: String, onChange: (List<Note>) -> Unit): () -> Unit

    suspend fun save(uid: String, note: Note)

    /** Writes several notes at once, for a reorder. */
    suspend fun saveAll(uid: String, notes: List<Note>)

    suspend fun delete(uid: String, noteId: String)
}
