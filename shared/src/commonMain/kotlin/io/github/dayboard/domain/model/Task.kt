package io.github.dayboard.domain.model

/**
 * One task, or one subtask.
 *
 * The same type for both, told apart by [parentId]. Nesting stops at one level:
 * a task with a parent can never have children of its own, and the dialogs enforce
 * it by not offering the option. A separate Subtask type would duplicate every
 * field and every query for the sake of a rule that is one null check.
 *
 * [position] orders a task among its siblings only - among the top-level tasks, or
 * among one parent's subtasks. The numbers are not compacted for top-level tasks,
 * so they may be sparse; nothing may assume they run 0, 1, 2.
 */
data class Task(
    val id: String,
    val text: String,
    val body: String? = null,
    val done: Boolean = false,
    val position: Int = 0,
    val parentId: String? = null,
    val tagIds: List<String> = emptyList(),
) {

    /** True for a task that sits in the list, false for one that sits under another. */
    val isTopLevel: Boolean get() = parentId == null
}

/** Shown for a task whose title was cleared. */
const val UNTITLED_TASK: String = "Untitled"

/**
 * The title to store for what was typed.
 *
 * A task with no title at all would be a row nobody could find again or click on,
 * so an emptied title becomes a placeholder rather than being rejected - the user
 * was mid-edit, not making a mistake.
 */
fun taskTitleOrFallback(text: String?): String =
    text.orEmpty().trim().ifEmpty { UNTITLED_TASK }

/**
 * The notes to store for what was typed.
 *
 * Empty means absent rather than an empty string, so "has notes" is one null check
 * everywhere instead of a null check and a blank check that can disagree.
 */
fun normalizeTaskBody(body: String?): String? = body.orEmpty().trim().ifEmpty { null }

/** The top-level tasks, in the order they are shown. */
fun List<Task>.topLevelTasks(): List<Task> = filter { it.isTopLevel }.sortedBy { it.position }

/** One task's subtasks, in the order they are shown. */
fun List<Task>.subtasksOf(parentId: String): List<Task> =
    filter { it.parentId == parentId }.sortedBy { it.position }

/**
 * Whether a task survives the active tag filter.
 *
 * No filter means everything. Subtasks are never tested: they are shown under their
 * parent, and hiding a subtask whose parent matched would leave a task looking as
 * though it had lost work.
 */
fun Task.matchesTagFilter(tagId: String?): Boolean = tagId == null || tagId in tagIds

/** The unfinished top-level tasks the filter allows, in order. */
fun List<Task>.pendingTasks(tagId: String?): List<Task> =
    topLevelTasks().filter { !it.done && it.matchesTagFilter(tagId) }

/** The finished top-level tasks the filter allows, in order. */
fun List<Task>.completedTasks(tagId: String?): List<Task> =
    topLevelTasks().filter { it.done && it.matchesTagFilter(tagId) }

/**
 * Where a new task or subtask goes: after everything already there.
 *
 * Computed over all siblings, including finished and filtered-out ones, so that
 * adding a task while a filter is on does not drop it into the middle of the list
 * the moment the filter is cleared.
 */
fun nextPosition(siblings: List<Task>): Int =
    (siblings.maxOfOrNull { it.position } ?: -1) + 1

/**
 * Ticks or unticks a task, carrying its subtasks with it.
 *
 * Finishing a task finishes everything under it, and un-finishing it brings them
 * all back. That is what makes the progress badge trustworthy: a task can never be
 * done while something under it is not.
 *
 * The cascade runs one way only. Ticking every subtask by hand does **not** finish
 * the parent, matching the original, and rightly: only the person doing the work
 * can say whether the task itself is finished.
 */
fun List<Task>.withCompletionToggled(taskId: String): List<Task> {
    val target = firstOrNull { it.id == taskId } ?: return this
    val done = !target.done

    return map { task ->
        when {
            task.id == taskId -> task.copy(done = done)
            task.parentId == taskId -> task.copy(done = done)
            else -> task
        }
    }
}

/**
 * Removes a task and everything under it.
 *
 * Done here rather than left to the database, because a task and its subtasks have
 * to disappear from the screen together. A parent that vanished while its subtasks
 * lingered would look like data loss even if the database caught up a moment later.
 */
fun List<Task>.withTaskRemoved(taskId: String): List<Task> =
    filterNot { it.id == taskId || it.parentId == taskId }

/** Replaces one task, leaving the rest alone. Returns the list unchanged if it is gone. */
fun List<Task>.withTaskUpdated(taskId: String, transform: (Task) -> Task): List<Task> =
    map { if (it.id == taskId) transform(it) else it }

/**
 * Whether a row has anything worth expanding to see.
 *
 * The chevron only appears when this is true, so that a bare one-line task has no
 * control that does nothing.
 */
fun Task.hasDetail(subtasks: List<Task>): Boolean =
    body != null || subtasks.isNotEmpty() || tagIds.isNotEmpty()

/** How a subtask count is shown on a collapsed row, as "done/total". */
fun subtaskProgress(subtasks: List<Task>): String =
    "${subtasks.count { it.done }}/${subtasks.size}"
