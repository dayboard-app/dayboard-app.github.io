package io.github.dayboard.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dayboard.domain.model.Tag
import io.github.dayboard.domain.model.Task
import io.github.dayboard.domain.model.completedTasks
import io.github.dayboard.domain.model.nextPosition
import io.github.dayboard.domain.model.normalizeTaskBody
import io.github.dayboard.domain.model.pendingTasks
import io.github.dayboard.domain.model.reorderCompacting
import io.github.dayboard.domain.model.reorderVisible
import io.github.dayboard.domain.model.subtasksOf
import io.github.dayboard.domain.model.taskTitleOrFallback
import io.github.dayboard.domain.model.topLevelTasks
import io.github.dayboard.domain.model.withCompletionToggled
import io.github.dayboard.domain.model.withPositions
import io.github.dayboard.domain.model.withTaskRemoved
import io.github.dayboard.domain.model.withTaskUpdated
import io.github.dayboard.domain.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The task list: what is in it, and what happens when it is changed.
 *
 * Every change is applied here first and written afterwards, so a tick or a drag
 * lands the instant it is made. The rules for *what* a change produces all live in
 * `:shared` and are tested; this owns the current value, the account, and the
 * writing.
 *
 * Tags come from [TagsController], shared with the notes.
 *
 * The filter and the set of expanded rows live here too, even though neither is
 * stored. They belong to the list rather than to the card that draws it: a collapsed
 * card takes its body out of the composition entirely, and holding them there would
 * silently reset the user's filter every time they folded the card away.
 */
class TasksController(
    private val tasks: TaskRepository,
    private val tags: TagsController,
    private val scope: CoroutineScope,
) {

    var allTasks: List<Task> by mutableStateOf(emptyList())
        private set

    /** True once the tasks have arrived, or once we know there are none. */
    var loaded: Boolean by mutableStateOf(false)
        private set

    /** The account's tags, shared with the notes. */
    val allTags: List<Tag> get() = tags.all

    /** The tag being filtered by, or null for all of them. One at a time. */
    var filterTagId: String? by mutableStateOf(null)
        private set

    private var expandedIds: Set<String> by mutableStateOf(emptySet())

    private var uid: String? = null
    private var stopTasks: (() -> Unit)? = null

    // ------------------------------------------------------------------ reading

    /** The unfinished tasks the filter allows, in order. */
    val pending: List<Task> get() = allTasks.pendingTasks(filterTagId)

    /** The finished tasks the filter allows, in order. */
    val completed: List<Task> get() = allTasks.completedTasks(filterTagId)

    fun subtasksOf(taskId: String): List<Task> = allTasks.subtasksOf(taskId)

    fun taskById(taskId: String): Task? = allTasks.firstOrNull { it.id == taskId }

    /** A task's tags, in the order it carries them, skipping any that no longer exist. */
    fun tagsOf(task: Task): List<Tag> =
        task.tagIds.mapNotNull { id -> allTags.firstOrNull { it.id == id } }

    fun isExpanded(taskId: String): Boolean = taskId in expandedIds

    // ----------------------------------------------------------------- following

    /** Follows one account's tasks. Safe to call repeatedly. */
    fun start(uid: String) {
        if (this.uid == uid) return

        stop()
        this.uid = uid

        stopTasks = tasks.observe(uid) { stored ->
            allTasks = stored
            loaded = true
        }
    }

    /** Detaches, and forgets the previous account's tasks. */
    fun stop() {
        stopTasks?.invoke()
        stopTasks = null
        uid = null
        allTasks = emptyList()
        filterTagId = null
        expandedIds = emptySet()
        loaded = false
    }

    // ------------------------------------------------------------------ viewing

    fun setFilter(tagId: String?) {
        // Tapping the chip that is already on clears the filter, which is the only
        // way back to "all" that does not need a second control.
        filterTagId = if (tagId == filterTagId) null else tagId
    }

    fun toggleExpanded(taskId: String) {
        expandedIds = if (taskId in expandedIds) expandedIds - taskId else expandedIds + taskId
    }

    // ----------------------------------------------------------------- changing

    /**
     * Adds a task at the end of the list, and returns its id so the caller can open
     * it. Null when there was nothing to add.
     *
     * A task added while a filter is on takes that tag, so that it does not vanish
     * the moment it is created - which is what would otherwise happen, and would
     * look exactly like the add having failed.
     */
    fun addTask(text: String): String? {
        val title = text.trim().ifEmpty { return null }
        val account = uid ?: return null

        val task = Task(
            id = newId(),
            text = title,
            position = nextPosition(allTasks.topLevelTasks()),
            tagIds = listOfNotNull(filterTagId),
        )

        allTasks = allTasks + task
        write(account) { save(it, task) }
        return task.id
    }

    /** Ticks or unticks a task, carrying its subtasks with it. */
    fun toggleDone(taskId: String) {
        val account = uid ?: return
        val before = allTasks

        allTasks = allTasks.withCompletionToggled(taskId)

        // Only the rows that actually changed are written: a task with no subtasks
        // is one write, not a rewrite of the list.
        val changed = allTasks.filter { task -> before.none { it == task } }
        write(account) { saveAll(it, changed) }
    }

    fun setTitle(taskId: String, text: String) {
        updateTask(taskId) { it.copy(text = taskTitleOrFallback(text)) }
    }

    fun setBody(taskId: String, body: String?) {
        updateTask(taskId) { it.copy(body = normalizeTaskBody(body)) }
    }

    /** Removes a task, and everything under it. */
    fun deleteTask(taskId: String) {
        val account = uid ?: return
        val subtaskIds = allTasks.subtasksOf(taskId).map { it.id }

        allTasks = allTasks.withTaskRemoved(taskId)
        expandedIds = expandedIds - taskId
        write(account) { delete(it, taskId, subtaskIds) }
    }

    /** Commits a finished drag in the pending list. Indices are visible positions. */
    fun moveTask(fromIndex: Int, toIndex: Int) {
        applyPositions(reorderVisible(pending, fromIndex, toIndex))
    }

    fun addSubtask(parentId: String, text: String) {
        val title = text.trim().ifEmpty { return }
        val account = uid ?: return

        val subtask = Task(
            id = newId(),
            text = title,
            position = nextPosition(allTasks.subtasksOf(parentId)),
            parentId = parentId,
        )

        allTasks = allTasks + subtask
        write(account) { save(it, subtask) }
    }

    /** Commits a finished drag among one task's subtasks. */
    fun moveSubtask(parentId: String, fromIndex: Int, toIndex: Int) {
        applyPositions(reorderCompacting(allTasks.subtasksOf(parentId), fromIndex, toIndex))
    }

    // --------------------------------------------------------------------- tags

    /** Puts a tag on a task, or takes it off. */
    fun toggleTag(taskId: String, tagId: String) {
        updateTask(taskId) { task ->
            val tagIds = if (tagId in task.tagIds) task.tagIds - tagId else task.tagIds + tagId
            task.copy(tagIds = tagIds)
        }
    }

    /**
     * Creates a tag and puts it on a task, or attaches one that already exists.
     *
     * The making of it belongs to [TagsController], which owns the vocabulary the
     * notes share; all this decides is that the result goes on this task.
     */
    fun createTag(taskId: String, name: String, color: String, emoji: String?) {
        val tag = tags.createOrFind(name, color, emoji) ?: return

        if (tag.id !in (taskById(taskId)?.tagIds ?: emptyList())) toggleTag(taskId, tag.id)
    }

    // ------------------------------------------------------------------ private

    private fun updateTask(taskId: String, transform: (Task) -> Task) {
        val account = uid ?: return

        allTasks = allTasks.withTaskUpdated(taskId, transform)
        val updated = taskById(taskId) ?: return
        write(account) { save(it, updated) }
    }

    private fun applyPositions(positions: Map<String, Int>) {
        if (positions.isEmpty()) return
        val account = uid ?: return

        allTasks = allTasks.withPositions(positions)
        val moved = allTasks.filter { it.id in positions }
        write(account) { saveAll(it, moved) }
    }

    private fun write(account: String, block: suspend TaskRepository.(String) -> Unit) {
        scope.launch { tasks.block(account) }
    }
}
