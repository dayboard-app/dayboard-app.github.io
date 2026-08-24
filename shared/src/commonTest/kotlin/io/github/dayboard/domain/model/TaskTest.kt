package io.github.dayboard.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskTest {

    // -------------------------------------------------------------- normalising

    @Test
    fun aClearedTitleBecomesAPlaceholder() {
        // A row with no title is a row nobody can find again or click on.
        listOf(null, "", "   ", "\n\t").forEach { typed ->
            assertEquals(UNTITLED_TASK, taskTitleOrFallback(typed), "typed \"$typed\"")
        }
    }

    @Test
    fun aRealTitleIsTrimmedAndKept() {
        assertEquals("Buy milk", taskTitleOrFallback("  Buy milk  "))
    }

    @Test
    fun emptyNotesAreStoredAsAbsentRatherThanBlank() {
        // So that "has notes" is one null check, not a null check and a blank check
        // that can disagree.
        listOf(null, "", "   ").forEach { typed ->
            assertEquals(null, normalizeTaskBody(typed), "typed ${typed?.let { "\"$it\"" }}")
        }
        assertEquals("some notes", normalizeTaskBody("  some notes  "))
    }

    // ------------------------------------------------------------------ queries

    @Test
    fun topLevelTasks_excludeSubtasksAndComeBackInOrder() {
        val tasks = listOf(
            task("c", position = 2),
            task("sub", position = 0, parentId = "a"),
            task("a", position = 0),
            task("b", position = 1),
        )

        assertEquals(listOf("a", "b", "c"), tasks.topLevelTasks().map { it.id })
    }

    @Test
    fun subtasksOf_findOnlyThatParentsChildren() {
        val tasks = listOf(
            task("a"),
            task("a2", position = 1, parentId = "a"),
            task("a1", position = 0, parentId = "a"),
            task("b1", position = 0, parentId = "b"),
        )

        assertEquals(listOf("a1", "a2"), tasks.subtasksOf("a").map { it.id })
        assertEquals(emptyList(), tasks.subtasksOf("nobody").map { it.id })
    }

    @Test
    fun pendingAndCompleted_splitTheTopLevelTasks() {
        val tasks = listOf(
            task("a", position = 0),
            task("b", position = 1, done = true),
            task("c", position = 2),
            task("sub", parentId = "a", done = true),
        )

        assertEquals(listOf("a", "c"), tasks.pendingTasks(null).map { it.id })
        assertEquals(listOf("b"), tasks.completedTasks(null).map { it.id })
    }

    @Test
    fun theTagFilterAppliesToBothGroups() {
        val tasks = listOf(
            task("a", position = 0, tagIds = listOf("work")),
            task("b", position = 1, tagIds = listOf("home")),
            task("c", position = 2, done = true, tagIds = listOf("work")),
            task("d", position = 3, done = true),
        )

        assertEquals(listOf("a"), tasks.pendingTasks("work").map { it.id })
        assertEquals(listOf("c"), tasks.completedTasks("work").map { it.id })
    }

    @Test
    fun aTaskWithNoTagsSurvivesOnlyTheEmptyFilter() {
        val plain = task("a")

        assertTrue(plain.matchesTagFilter(null))
        assertFalse(plain.matchesTagFilter("work"))
    }

    @Test
    fun aTaskWithSeveralTagsMatchesAnyOfThem() {
        val tagged = task("a", tagIds = listOf("work", "urgent"))

        assertTrue(tagged.matchesTagFilter("work"))
        assertTrue(tagged.matchesTagFilter("urgent"))
        assertFalse(tagged.matchesTagFilter("home"))
    }

    // ---------------------------------------------------------------- positions

    @Test
    fun aNewTaskGoesAfterEverythingAlreadyThere() {
        assertEquals(0, nextPosition(emptyList()))
        assertEquals(3, nextPosition(listOf(task("a", position = 2), task("b", position = 0))))
    }

    @Test
    fun aNewTaskCountsFinishedAndHiddenSiblingsToo() {
        // Otherwise adding a task while a filter is on would drop it into the middle
        // of the list the moment the filter was cleared.
        val siblings = listOf(
            task("a", position = 0),
            task("b", position = 7, done = true),
            task("c", position = 3, tagIds = listOf("hidden")),
        )

        assertEquals(8, nextPosition(siblings))
    }

    @Test
    fun aNegativePositionStillProducesAValidNextOne() {
        assertEquals(0, nextPosition(listOf(task("a", position = -1))))
    }

    // --------------------------------------------------------------- completion

    @Test
    fun finishingATaskFinishesEverythingUnderIt() {
        val tasks = listOf(
            task("a"),
            task("a1", parentId = "a"),
            task("a2", parentId = "a"),
            task("b"),
        )

        val toggled = tasks.withCompletionToggled("a")

        assertTrue(toggled.single { it.id == "a" }.done)
        assertTrue(toggled.single { it.id == "a1" }.done)
        assertTrue(toggled.single { it.id == "a2" }.done)
        assertFalse(toggled.single { it.id == "b" }.done, "another task is not touched")
    }

    @Test
    fun unfinishingATaskBringsItsSubtasksBackToo() {
        val tasks = listOf(
            task("a", done = true),
            task("a1", parentId = "a", done = true),
        )

        val toggled = tasks.withCompletionToggled("a")

        assertFalse(toggled.single { it.id == "a" }.done)
        assertFalse(toggled.single { it.id == "a1" }.done)
    }

    @Test
    fun finishingASubtaskDoesNotFinishItsParent() {
        // The cascade runs one way only, as in the original. Only the person doing
        // the work can say whether the task itself is finished.
        val tasks = listOf(task("a"), task("a1", parentId = "a"))

        val toggled = tasks.withCompletionToggled("a1")

        assertTrue(toggled.single { it.id == "a1" }.done)
        assertFalse(toggled.single { it.id == "a" }.done)
    }

    @Test
    fun togglingATaskThatIsNoLongerThereChangesNothing() {
        // Reachable: another device can delete a task between a render and a click.
        val tasks = listOf(task("a"))

        assertEquals(tasks, tasks.withCompletionToggled("gone"))
    }

    // ----------------------------------------------------------------- removing

    @Test
    fun removingATaskTakesItsSubtasksWithIt() {
        val tasks = listOf(
            task("a"),
            task("a1", parentId = "a"),
            task("b"),
            task("b1", parentId = "b"),
        )

        assertEquals(listOf("b", "b1"), tasks.withTaskRemoved("a").map { it.id })
    }

    @Test
    fun removingASubtaskLeavesItsParentAlone() {
        val tasks = listOf(task("a"), task("a1", parentId = "a"))

        assertEquals(listOf("a"), tasks.withTaskRemoved("a1").map { it.id })
    }

    // ----------------------------------------------------------------- updating

    @Test
    fun updatingOneTaskLeavesTheRestAlone() {
        val tasks = listOf(task("a", text = "old"), task("b", text = "b"))

        val updated = tasks.withTaskUpdated("a") { it.copy(text = "new") }

        assertEquals("new", updated.single { it.id == "a" }.text)
        assertEquals("b", updated.single { it.id == "b" }.text)
    }

    @Test
    fun updatingATaskThatIsGoneChangesNothing() {
        val tasks = listOf(task("a"))

        assertEquals(tasks, tasks.withTaskUpdated("gone") { it.copy(text = "x") })
    }

    // ------------------------------------------------------------------ display

    @Test
    fun aBareTaskHasNothingWorthExpanding() {
        // The chevron only appears when this is true, so a one-line task has no
        // control that does nothing.
        assertFalse(task("a").hasDetail(emptyList()))
    }

    @Test
    fun anythingExtraMakesATaskWorthExpanding() {
        assertTrue(task("a", body = "notes").hasDetail(emptyList()))
        assertTrue(task("a", tagIds = listOf("work")).hasDetail(emptyList()))
        assertTrue(task("a").hasDetail(listOf(task("a1", parentId = "a"))))
    }

    @Test
    fun subtaskProgressCountsTheFinishedOnes() {
        val subtasks = listOf(
            task("a1", parentId = "a", done = true),
            task("a2", parentId = "a"),
            task("a3", parentId = "a", done = true),
        )

        assertEquals("2/3", subtaskProgress(subtasks))
        assertEquals("0/0", subtaskProgress(emptyList()))
    }

    @Test
    fun isTopLevel_isTheOneRuleThatTellsATaskFromASubtask() {
        assertTrue(task("a").isTopLevel)
        assertFalse(task("a1", parentId = "a").isTopLevel)
    }

    private companion object {
        fun task(
            id: String,
            text: String = id,
            body: String? = null,
            done: Boolean = false,
            position: Int = 0,
            parentId: String? = null,
            tagIds: List<String> = emptyList(),
        ) = Task(
            id = id,
            text = text,
            body = body,
            done = done,
            position = position,
            parentId = parentId,
            tagIds = tagIds,
        )
    }
}
