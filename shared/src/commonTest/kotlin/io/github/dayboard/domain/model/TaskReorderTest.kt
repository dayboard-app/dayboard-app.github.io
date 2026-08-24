package io.github.dayboard.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TaskReorderTest {

    // -------------------------------------------------------------------- moved

    @Test
    fun moved_takesAnItemOutAndPutsItBackAtTheNewIndex() {
        assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").moved(0, 2))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").moved(2, 0))
        assertEquals(listOf("a", "c", "b"), listOf("a", "b", "c").moved(1, 2))
    }

    @Test
    fun moved_leavesTheListAloneWhenThereIsNothingToDo() {
        val list = listOf("a", "b", "c")

        // Identity, not just equality: the callers use it to tell "nothing happened"
        // from "happened to come out the same".
        assertSame(list, list.moved(1, 1))
    }

    @Test
    fun moved_survivesAnIndexFromADragThatMissed() {
        val list = listOf("a", "b", "c")

        assertSame(list, list.moved(-1, 1))
        assertSame(list, list.moved(0, -1))
        assertSame(list, list.moved(0, 9))
        assertSame(list, list.moved(9, 0))
        assertSame(emptyList<String>(), emptyList<String>().moved(0, 0))
    }

    // ------------------------------------------------- top-level position pool

    @Test
    fun reorderingVisibleTasks_dealsOutTheirOwnPositions() {
        val visible = listOf(
            task("a", position = 0),
            task("b", position = 1),
            task("c", position = 2),
        )

        // Drag `a` to the end: the three positions 0, 1, 2 stay in use, just held by
        // different tasks.
        assertEquals(mapOf("b" to 0, "c" to 1, "a" to 2), reorderVisible(visible, 0, 2))
    }

    @Test
    fun theSetOfPositionsInUseNeverChanges() {
        // This is the whole point of pooling. Hidden and finished tasks hold
        // positions in between the visible ones; introducing a new number, or
        // dropping one, would shuffle them the moment the filter was cleared.
        val visible = listOf(
            task("a", position = 3),
            task("b", position = 7),
            task("c", position = 12),
        )

        val changes = reorderVisible(visible, 2, 0)
        val after = visible.withPositions(changes)

        assertEquals(listOf(3, 7, 12), after.map { it.position }.sorted())
        assertEquals(listOf("c", "a", "b"), after.sortedBy { it.position }.map { it.id })
    }

    @Test
    fun sparseAndUnsortedPositionsAreHandled() {
        // Top-level positions are never compacted, so they arrive arbitrary. The
        // pool is sorted before being dealt out, which is what makes this work.
        val visible = listOf(
            task("a", position = 40),
            task("b", position = 5),
            task("c", position = 22),
        )

        val after = visible.withPositions(reorderVisible(visible, 0, 2))

        assertEquals(listOf("b", "c", "a"), after.sortedBy { it.position }.map { it.id })
        assertEquals(listOf(5, 22, 40), after.map { it.position }.sorted())
    }

    @Test
    fun aDragThatEndsWhereItStartedWritesNothing() {
        val visible = listOf(task("a", position = 0), task("b", position = 1))

        assertEquals(emptyMap(), reorderVisible(visible, 1, 1))
        assertEquals(emptyMap(), reorderVisible(visible, 0, 5))
    }

    @Test
    fun onlyTheTasksThatMovedAreWritten() {
        // Dragging the last two around must not rewrite the first one.
        val visible = listOf(
            task("a", position = 0),
            task("b", position = 1),
            task("c", position = 2),
        )

        val changes = reorderVisible(visible, 1, 2)

        assertEquals(mapOf("c" to 1, "b" to 2), changes)
    }

    @Test
    fun reorderingASingleVisibleTaskWritesNothing() {
        val visible = listOf(task("a", position = 4))

        assertEquals(emptyMap(), reorderVisible(visible, 0, 0))
    }

    // -------------------------------------------------- subtask compact renumber

    @Test
    fun reorderingSubtasksRenumbersThemFromZero() {
        val subtasks = listOf(
            task("a1", position = 0, parentId = "a"),
            task("a2", position = 1, parentId = "a"),
            task("a3", position = 2, parentId = "a"),
        )

        assertEquals(mapOf("a3" to 0, "a1" to 1, "a2" to 2), reorderCompacting(subtasks, 2, 0))
    }

    @Test
    fun subtaskRenumberingTidiesPositionsThatHadDrifted() {
        // Subtasks are added and deleted freely, so their numbers drift. Compacting
        // on every reorder is what keeps them from drifting forever.
        val subtasks = listOf(
            task("a1", position = 0, parentId = "a"),
            task("a2", position = 5, parentId = "a"),
            task("a3", position = 9, parentId = "a"),
        )

        val after = subtasks.withPositions(reorderCompacting(subtasks, 0, 1))

        assertEquals(listOf(0, 1, 2), after.map { it.position }.sorted())
        assertEquals(listOf("a2", "a1", "a3"), after.sortedBy { it.position }.map { it.id })
    }

    @Test
    fun subtasksAlreadyInPlaceAreNotWritten() {
        val subtasks = listOf(
            task("a1", position = 0, parentId = "a"),
            task("a2", position = 1, parentId = "a"),
            task("a3", position = 2, parentId = "a"),
        )

        // Swapping the last two leaves the first where it is.
        assertEquals(mapOf("a3" to 1, "a2" to 2), reorderCompacting(subtasks, 1, 2))
        assertEquals(emptyMap(), reorderCompacting(subtasks, 1, 1))
    }

    // --------------------------------------------------------------- applying

    @Test
    fun withPositions_onlyTouchesTheTasksItNames() {
        val tasks = listOf(task("a", position = 0), task("b", position = 1))

        val updated = tasks.withPositions(mapOf("b" to 9))

        assertEquals(0, updated.single { it.id == "a" }.position)
        assertEquals(9, updated.single { it.id == "b" }.position)
    }

    @Test
    fun withPositions_ignoresNamesItDoesNotHave() {
        val tasks = listOf(task("a", position = 0))

        assertEquals(tasks, tasks.withPositions(mapOf("gone" to 4)))
    }

    private companion object {
        fun task(id: String, position: Int, parentId: String? = null) =
            Task(id = id, text = id, position = position, parentId = parentId)
    }
}
