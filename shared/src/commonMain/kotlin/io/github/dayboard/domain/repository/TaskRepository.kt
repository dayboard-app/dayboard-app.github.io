package io.github.dayboard.domain.repository

import io.github.dayboard.domain.model.Tag
import io.github.dayboard.domain.model.Task

/**
 * Where tasks live.
 *
 * Every write takes a whole [Task] rather than the field that changed. That is not
 * laziness: the caller has already applied the change to what is on screen, so it
 * holds the complete new value, and sending it whole means there is one shape to
 * store and one to read rather than a method per field.
 */
interface TaskRepository {

    /**
     * Follows one account's tasks, reporting the whole set on every change.
     *
     * The whole set rather than a delta because there is no pagination and the list
     * is small - a few hundred at most - and because the list's own rules (ordering,
     * grouping, filtering) all read the whole set anyway.
     *
     * Changes this device made are not reported back; the caller has already applied
     * them, and re-applying would fight whatever it is editing.
     */
    fun observe(uid: String, onChange: (List<Task>) -> Unit): () -> Unit

    /** Writes one task, creating it if it is new. */
    suspend fun save(uid: String, task: Task)

    /** Writes several tasks at once, for a reorder. */
    suspend fun saveAll(uid: String, tasks: List<Task>)

    /**
     * Removes a task and everything under it.
     *
     * The subtasks are named explicitly because Firestore has no cascade: a parent
     * deleted on its own would leave its subtasks behind, invisible - filtered out
     * of every list by a parent id that no longer matches anything, and impossible
     * to reach again.
     */
    suspend fun delete(uid: String, taskId: String, subtaskIds: List<String>)
}

/** Where tags live. Small, rarely changed, and read by both tasks and the settings. */
interface TagRepository {

    /** Follows one account's tags. As with tasks, the whole set on every change. */
    fun observe(uid: String, onChange: (List<Tag>) -> Unit): () -> Unit

    suspend fun save(uid: String, tag: Tag)

    /**
     * Removes a tag.
     *
     * Taking it off the tasks that carry it is the caller's job, and has to be:
     * those are task writes, and this interface does not touch tasks.
     */
    suspend fun delete(uid: String, tagId: String)
}
