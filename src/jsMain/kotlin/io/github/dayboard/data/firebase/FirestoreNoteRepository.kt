package io.github.dayboard.data.firebase

import io.github.dayboard.data.firebase.externals.CollectionReference
import io.github.dayboard.data.firebase.externals.QueryDocumentSnapshot
import io.github.dayboard.data.firebase.externals.collection
import io.github.dayboard.data.firebase.externals.deleteDoc
import io.github.dayboard.data.firebase.externals.docIn
import io.github.dayboard.data.firebase.externals.onCollectionSnapshot
import io.github.dayboard.data.firebase.externals.serverTimestamp
import io.github.dayboard.data.firebase.externals.setDoc
import io.github.dayboard.domain.model.Note
import io.github.dayboard.domain.repository.NoteRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * [NoteRepository] backed by one Firestore document per note.
 *
 * The collection sits at `users/{uid}/notes`, beside the tasks and inside the same
 * owner-only subtree. A document each rather than one document holding them all,
 * for the reason the tasks have: a note can be long, and editing one should not
 * rewrite every note the account has.
 */
class FirestoreNoteRepository : NoteRepository {

    override fun observe(uid: String, onChange: (List<Note>) -> Unit): () -> Unit =
        onCollectionSnapshot(
            notesCollection(uid),
            { snapshot ->
                // This device's own writes echo back before the server confirms
                // them, and applying them again would undo anything typed since.
                if (!snapshot.metadata.hasPendingWrites) {
                    onChange(snapshot.docs.map(::readNote))
                }
            },
            { error -> console.warn("note listener failed", error) },
        )

    override suspend fun save(uid: String, note: Note) {
        setDoc(docIn(notesCollection(uid), note.id), noteDocument(note)).await()
    }

    override suspend fun saveAll(uid: String, notes: List<Note>) {
        // In parallel: a reorder writes several documents that do not depend on one
        // another, and in sequence a long list would visibly settle a row at a time.
        coroutineScope {
            notes.map { note -> async { save(uid, note) } }.awaitAll()
        }
    }

    override suspend fun delete(uid: String, noteId: String) {
        deleteDoc(docIn(notesCollection(uid), noteId)).await()
    }
}

private fun notesCollection(uid: String): CollectionReference =
    collection(Firebase.firestore, "users", uid, "notes")

/**
 * Reads one stored note.
 *
 * Every field falls back on its own, so a document written by a newer version costs
 * the user that field rather than the whole note - and a note that failed to load
 * is a note they cannot delete either.
 */
private fun readNote(document: QueryDocumentSnapshot): Note {
    val data = document.data()

    return Note(
        // The id is the document's name rather than a stored field, so there is only
        // one of it and it cannot disagree with itself.
        id = document.id,
        title = data["title"] as? String ?: "",
        body = data["body"] as? String,
        position = (data["position"] as? Number)?.toInt() ?: 0,
        tagIds = storedStringArray(data["tagIds"]),
    )
}

private fun noteDocument(note: Note): dynamic {
    val document = jsObject()
    document["title"] = note.title
    document["body"] = note.body
    document["position"] = note.position
    // `toTypedArray` matters: a Kotlin List reaches Firestore as an opaque object,
    // an Array reaches it as a real JSON array.
    document["tagIds"] = note.tagIds.toTypedArray()
    document["updatedAt"] = serverTimestamp()
    return document
}
