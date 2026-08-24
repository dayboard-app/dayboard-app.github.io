package io.github.dayboard.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteTest {

    @Test
    fun aClearedTitleBecomesAPlaceholder() {
        listOf(null, "", "   ", "\n").forEach { typed ->
            assertEquals(UNTITLED_NOTE, noteTitleOrFallback(typed), "typed \"$typed\"")
        }
        assertEquals("Shopping", noteTitleOrFallback("  Shopping  "))
    }

    @Test
    fun emptyContentIsStoredAsAbsentRatherThanBlank() {
        listOf(null, "", "   ").forEach { typed ->
            assertEquals(null, normalizeNoteBody(typed), "typed \"$typed\"")
        }
        assertEquals("milk\neggs", normalizeNoteBody("  milk\neggs  "))
    }

    @Test
    fun visibleNotes_comeBackInPositionOrder() {
        val notes = listOf(
            note("c", position = 2),
            note("a", position = 0),
            note("b", position = 1),
        )

        assertEquals(listOf("a", "b", "c"), notes.visibleNotes(null).map { it.id })
    }

    @Test
    fun visibleNotes_hideWhatTheFilterExcludes() {
        val notes = listOf(
            note("a", position = 0, tagIds = listOf("work")),
            note("b", position = 1),
            note("c", position = 2, tagIds = listOf("home", "work")),
        )

        assertEquals(listOf("a", "c"), notes.visibleNotes("work").map { it.id })
        assertEquals(listOf("c"), notes.visibleNotes("home").map { it.id })
        assertEquals(emptyList(), notes.visibleNotes("nobody").map { it.id })
    }

    @Test
    fun aNoteWithNoTagsSurvivesOnlyTheEmptyFilter() {
        assertTrue(note("a").matchesTagFilter(null))
        assertFalse(note("a").matchesTagFilter("work"))
    }

    @Test
    fun aBareNoteHasNothingWorthExpanding() {
        assertFalse(note("a").hasDetail())
    }

    @Test
    fun contentOrATagMakesANoteWorthExpanding() {
        assertTrue(note("a", body = "something").hasDetail())
        assertTrue(note("a", tagIds = listOf("work")).hasDetail())
    }

    @Test
    fun aNewNoteGoesAfterEverythingAlreadyThere() {
        assertEquals(0, nextNotePosition(emptyList()))
        assertEquals(6, nextNotePosition(listOf(note("a", position = 5), note("b", position = 1))))
    }

    @Test
    fun aNewNoteCountsNotesTheFilterIsHiding() {
        // Otherwise a note added under a filter would land in the middle of the list
        // as soon as the filter was cleared.
        val notes = listOf(note("a", position = 0), note("b", position = 9, tagIds = listOf("x")))

        assertEquals(10, nextNotePosition(notes))
    }

    @Test
    fun removingANoteLeavesTheRestWhereTheyWere() {
        // Positions are deliberately not compacted on delete: renumbering would
        // rewrite every remaining note to tidy numbers nobody sees.
        val notes =
            listOf(note("a", position = 0), note("b", position = 1), note("c", position = 2))

        val remaining = notes.withNoteRemoved("b")

        assertEquals(listOf("a", "c"), remaining.map { it.id })
        assertEquals(listOf(0, 2), remaining.map { it.position })
    }

    @Test
    fun updatingOneNoteLeavesTheRestAlone() {
        val notes = listOf(note("a", title = "old"), note("b", title = "b"))

        val updated = notes.withNoteUpdated("a") { it.copy(title = "new") }

        assertEquals("new", updated.single { it.id == "a" }.title)
        assertEquals("b", updated.single { it.id == "b" }.title)
    }

    @Test
    fun updatingANoteThatIsGoneChangesNothing() {
        val notes = listOf(note("a"))

        assertEquals(notes, notes.withNoteUpdated("gone") { it.copy(title = "x") })
    }

    @Test
    fun withPositions_onlyTouchesTheNotesItNames() {
        val notes = listOf(note("a", position = 0), note("b", position = 1))

        val updated = notes.withPositions(mapOf("b" to 9))

        assertEquals(0, updated.single { it.id == "a" }.position)
        assertEquals(9, updated.single { it.id == "b" }.position)
    }

    @Test
    fun withPositions_ignoresNamesItDoesNotHave() {
        val notes = listOf(note("a", position = 0))

        assertEquals(notes, notes.withPositions(mapOf("gone" to 4)))
    }

    @Test
    fun notesReorderThroughTheSamePositionPoolAsTasks() {
        // Notes share the reorder rule with tasks, which is why both are `Positioned`.
        // The pool is what keeps a drag under a tag filter from disturbing the notes
        // that filter is hiding.
        val visible = listOf(note("a", position = 1), note("c", position = 7))

        val after = visible.withPositions(reorderVisible(visible, 0, 1))

        assertEquals(listOf(1, 7), after.map { it.position }.sorted())
        assertEquals(listOf("c", "a"), after.sortedBy { it.position }.map { it.id })
    }

    private companion object {
        fun note(
            id: String,
            title: String = id,
            body: String? = null,
            position: Int = 0,
            tagIds: List<String> = emptyList(),
        ) = Note(id = id, title = title, body = body, position = position, tagIds = tagIds)
    }
}
