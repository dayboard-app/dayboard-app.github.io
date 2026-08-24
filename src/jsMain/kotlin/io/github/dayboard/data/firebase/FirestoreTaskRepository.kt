package io.github.dayboard.data.firebase

import io.github.dayboard.data.firebase.externals.CollectionReference
import io.github.dayboard.data.firebase.externals.QueryDocumentSnapshot
import io.github.dayboard.data.firebase.externals.QuerySnapshot
import io.github.dayboard.data.firebase.externals.collection
import io.github.dayboard.data.firebase.externals.deleteDoc
import io.github.dayboard.data.firebase.externals.docIn
import io.github.dayboard.data.firebase.externals.onCollectionSnapshot
import io.github.dayboard.data.firebase.externals.serverTimestamp
import io.github.dayboard.data.firebase.externals.setDoc
import io.github.dayboard.domain.model.Tag
import io.github.dayboard.domain.model.Task
import io.github.dayboard.domain.repository.TagRepository
import io.github.dayboard.domain.repository.TaskRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * [TaskRepository] backed by one Firestore document per task.
 *
 * The collection sits at `users/{uid}/tasks`, inside the subtree the security rules
 * restrict to its owner, so nothing here has to prove who is asking.
 *
 * A document per task rather than one document holding them all. Firestore caps a
 * document at a megabyte, which a few hundred tasks with notes would approach, but
 * the real reason is writes: ticking one task off would otherwise rewrite every task
 * the account has, and two devices ticking different tasks would overwrite each
 * other rather than merge.
 */
class FirestoreTaskRepository : TaskRepository {

    override fun observe(uid: String, onChange: (List<Task>) -> Unit): () -> Unit =
        onCollectionSnapshot(
            tasksCollection(uid),
            { snapshot ->
                // This device's own writes echo back before the server confirms
                // them. The caller applied them already, and re-applying would undo
                // anything typed since.
                if (!snapshot.metadata.hasPendingWrites) {
                    onChange(snapshot.docs.map(::readTask))
                }
            },
            { error -> console.warn("task listener failed", error) },
        )

    override suspend fun save(uid: String, task: Task) {
        setDoc(docIn(tasksCollection(uid), task.id), taskDocument(task)).await()
    }

    override suspend fun saveAll(uid: String, tasks: List<Task>) {
        // In parallel rather than one after another: a reorder writes several
        // documents that do not depend on each other, and doing them in sequence
        // would make a long list visibly settle one row at a time.
        coroutineScope {
            tasks.map { task -> async { save(uid, task) } }.awaitAll()
        }
    }

    override suspend fun delete(uid: String, taskId: String, subtaskIds: List<String>) {
        val tasks = tasksCollection(uid)

        coroutineScope {
            (subtaskIds + taskId)
                .map { id -> async { deleteDoc(docIn(tasks, id)).await() } }
                .awaitAll()
        }
    }
}

/** [TagRepository] backed by one Firestore document per tag. */
class FirestoreTagRepository : TagRepository {

    override fun observe(uid: String, onChange: (List<Tag>) -> Unit): () -> Unit =
        onCollectionSnapshot(
            tagsCollection(uid),
            { snapshot ->
                if (!snapshot.metadata.hasPendingWrites) {
                    onChange(readTags(snapshot))
                }
            },
            { error -> console.warn("tag listener failed", error) },
        )

    override suspend fun save(uid: String, tag: Tag) {
        setDoc(docIn(tagsCollection(uid), tag.id), tagDocument(tag)).await()
    }

    override suspend fun delete(uid: String, tagId: String) {
        deleteDoc(docIn(tagsCollection(uid), tagId)).await()
    }
}

private fun tasksCollection(uid: String): CollectionReference =
    collection(Firebase.firestore, "users", uid, "tasks")

private fun tagsCollection(uid: String): CollectionReference =
    collection(Firebase.firestore, "users", uid, "tags")

/**
 * Reads one stored task.
 *
 * Every field falls back on its own. A document written by a newer version, or
 * half-written by something else, should cost the user that field rather than the
 * whole task - a task that failed to load is a task they cannot delete either.
 */
private fun readTask(document: QueryDocumentSnapshot): Task {
    val data = document.data()

    return Task(
        // The id is the document's name, not a stored field, so there is only one
        // of it and it cannot disagree with itself.
        id = document.id,
        text = data["text"] as? String ?: "",
        body = data["body"] as? String,
        done = data["done"] as? Boolean ?: false,
        position = (data["position"] as? Number)?.toInt() ?: 0,
        parentId = data["parentId"] as? String,
        tagIds = storedStringArray(data["tagIds"]),
    )
}

private fun taskDocument(task: Task): dynamic {
    val document = jsObject()
    document["text"] = task.text
    document["body"] = task.body
    document["done"] = task.done
    document["position"] = task.position
    document["parentId"] = task.parentId
    // `toTypedArray` matters: a Kotlin List reaches Firestore as an opaque object,
    // an Array reaches it as a real JSON array.
    document["tagIds"] = task.tagIds.toTypedArray()
    document["updatedAt"] = serverTimestamp()
    return document
}

/**
 * Reads the stored tags, oldest first.
 *
 * The order is the one the filter row and the tag pickers show, and it has to be
 * stable: tags reshuffling every time one is added would make them impossible to
 * find by position. `createdAt` is written for exactly this and read nowhere else.
 */
private fun readTags(snapshot: QuerySnapshot): List<Tag> =
    snapshot.docs
        .map { document ->
            val data = document.data()
            val createdAt = (data["createdAt"] as? Number)?.toDouble() ?: 0.0

            createdAt to Tag(
                id = document.id,
                name = data["name"] as? String ?: "",
                color = data["color"] as? String ?: DEFAULT_STORED_TAG_COLOR,
                emoji = data["emoji"] as? String,
            )
        }
        .sortedBy { (createdAt, _) -> createdAt }
        .map { (_, tag) -> tag }

private fun tagDocument(tag: Tag): dynamic {
    val document = jsObject()
    document["name"] = tag.name
    document["color"] = tag.color
    document["emoji"] = tag.emoji
    // The device's clock, not the server's, and deliberately: this is written once
    // and only ever used to sort tags against each other, so it has to be readable
    // as a number immediately rather than arriving as a placeholder that resolves
    // later. A few seconds of clock skew cannot reorder a list of tags noticeably.
    document["createdAt"] = kotlin.js.Date.now()
    return document
}

/** Used only for a stored tag whose colour is missing or not a string. */
private const val DEFAULT_STORED_TAG_COLOR = "#64748b"
