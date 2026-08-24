package io.github.dayboard.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardLayoutTest {

    private val all: (String) -> Boolean = { true }

    /** A column where the middle card is hidden by its settings toggle. */
    private val withHidden = CardLayout.Default.copy(left = listOf("a", "hidden", "b", "c"))
    private val hiddenNotVisible: (String) -> Boolean = { it != "hidden" }

    // ------------------------------------------------------------ defaults

    @Test
    fun default_putsTheTimerLeftAndTheListsRight() {
        assertEquals(listOf("timer"), CardLayout.Default.left)
        assertEquals(listOf("tasks", "notes"), CardLayout.Default.right)
        assertEquals(emptyList(), CardLayout.Default.collapsed)
    }

    @Test
    fun default_neverPlacesTheClockInAColumn() {
        // It renders across the top and cannot be dragged; a column would let it be.
        assertFalse(CardId.Clock.id in CardLayout.Default.left + CardLayout.Default.right)
    }

    // ------------------------------------------------------------- collapse

    @Test
    fun toggleCollapsed_rollsUpAndOpensAgain() {
        val collapsed = CardLayout.Default.toggleCollapsed(CardId.Timer)
        assertTrue(collapsed.isCollapsed(CardId.Timer))
        assertFalse(collapsed.toggleCollapsed(CardId.Timer).isCollapsed(CardId.Timer))
    }

    // ------------------------------------------- reorder inside one column

    @Test
    fun movingDown_landsAfterTheCardItWasDroppedOn() {
        val layout = CardLayout.Default.copy(left = listOf("a", "b", "c"))
        val moved = layout.moveCard(BoardColumn.Left, BoardColumn.Left, 0, 2, all)
        assertEquals(listOf("b", "c", "a"), moved.left)
    }

    @Test
    fun movingUp_landsBeforeTheCardItWasDroppedOn() {
        val layout = CardLayout.Default.copy(left = listOf("a", "b", "c"))
        val moved = layout.moveCard(BoardColumn.Left, BoardColumn.Left, 2, 0, all)
        assertEquals(listOf("c", "a", "b"), moved.left)
    }

    @Test
    fun droppingBelowEverythingInTheSameColumn_movesTheCardLast() {
        // The pointer passing the last card's midpoint yields an index of `size`,
        // which is one past the last valid one. That is an ordinary gesture - drag
        // a card to the bottom of its own column - and must not be ignored.
        val layout = CardLayout.Default.copy(left = listOf("a", "b", "c"))
        val moved = layout.moveCard(BoardColumn.Left, BoardColumn.Left, 0, 3, all)
        assertEquals(listOf("b", "c", "a"), moved.left)
    }

    @Test
    fun droppingBelowEverything_keepsHiddenCardsAndTheDraggedCardOnce() {
        val moved = withHidden.moveCard(BoardColumn.Left, BoardColumn.Left, 0, 3, hiddenNotVisible)
        assertEquals(listOf("b", "c", "a"), moved.left.filter(hiddenNotVisible))
        assertTrue("hidden" in moved.left)
        assertEquals(moved.left.size, moved.left.distinct().size, "no card may be duplicated")
    }

    @Test
    fun droppingACardOnItself_changesNothing() {
        val layout = CardLayout.Default.copy(left = listOf("a", "b", "c"))
        assertEquals(layout, layout.moveCard(BoardColumn.Left, BoardColumn.Left, 1, 1, all))
    }

    @Test
    fun reordering_leavesHiddenCardsWhereTheyWere() {
        // The user sees a, b, c and drags a to the end. `hidden` is invisible to
        // them, so it must not be shuffled by their gesture.
        val moved = withHidden.moveCard(BoardColumn.Left, BoardColumn.Left, 0, 2, hiddenNotVisible)
        assertEquals(listOf("b", "c", "a"), moved.left.filter(hiddenNotVisible))
        assertTrue("hidden" in moved.left, "a hidden card must survive a reorder")
    }

    @Test
    fun reordering_isDrivenByVisiblePositionsNotStoredOnes() {
        // Visible list is a, b, c: dropping at visible index 1 must land beside `b`,
        // which sits at stored index 2. Counting stored slots would land beside
        // `hidden` instead.
        val moved = withHidden.moveCard(BoardColumn.Left, BoardColumn.Left, 2, 1, hiddenNotVisible)
        assertEquals(listOf("a", "c", "b"), moved.left.filter(hiddenNotVisible))
    }

    // ------------------------------------------------- move between columns

    @Test
    fun movingToAnotherColumn_insertsBeforeTheCardAtThatPosition() {
        val layout = CardLayout.Default.copy(left = listOf("a"), right = listOf("x", "y"))
        val moved = layout.moveCard(BoardColumn.Left, BoardColumn.Right, 0, 1, all)
        assertEquals(emptyList(), moved.left)
        assertEquals(listOf("x", "a", "y"), moved.right)
    }

    @Test
    fun droppingPastTheLastCard_appends() {
        val layout = CardLayout.Default.copy(left = listOf("a"), right = listOf("x", "y"))
        val moved = layout.moveCard(BoardColumn.Left, BoardColumn.Right, 0, 5, all)
        assertEquals(listOf("x", "y", "a"), moved.right)
    }

    @Test
    fun anEmptyColumnIsAValidTarget() {
        val layout = CardLayout.Default.copy(left = listOf("a", "b"), right = emptyList())
        val moved = layout.moveCard(BoardColumn.Left, BoardColumn.Right, 0, 0, all)
        assertEquals(listOf("b"), moved.left)
        assertEquals(listOf("a"), moved.right)
    }

    @Test
    fun aCardNeverEndsUpInBothColumns() {
        val layout = CardLayout.Default.copy(left = listOf("a", "b"), right = listOf("x"))
        val moved = layout.moveCard(BoardColumn.Left, BoardColumn.Right, 1, 0, all)
        assertFalse("b" in moved.left)
        assertTrue("b" in moved.right)
        assertEquals((moved.left + moved.right).distinct().size, (moved.left + moved.right).size)
    }

    @Test
    fun anIndexThatNamesNoCard_changesNothing() {
        val layout = CardLayout.Default.copy(left = listOf("a"), right = listOf("x"))
        assertEquals(layout, layout.moveCard(BoardColumn.Left, BoardColumn.Right, 9, 0, all))
    }

    // -------------------------------------------------------------- parsing

    @Test
    fun parse_fallsBackWhenThereIsNothingStored() {
        assertEquals(CardLayout.Default, parseCardLayout(null))
    }

    @Test
    fun parse_readsAStoredLayout() {
        val parsed = parseCardLayout(
            mapOf(
                "left" to listOf("tasks"),
                "right" to listOf("timer", "notes"),
                "widths" to mapOf("tasks" to "full"),
                "collapsed" to listOf("notes"),
            ),
        )
        assertEquals(listOf("tasks"), parsed.left)
        assertEquals(listOf("timer", "notes"), parsed.right)
        assertEquals(CardWidth.Full, parsed.widths["tasks"])
        assertEquals(listOf("notes"), parsed.collapsed)
    }

    @Test
    fun parse_fallsBackFieldByFieldRatherThanRejectingTheWholeDocument() {
        // A document half-written by an older version should cost only the part
        // that cannot be read.
        val parsed = parseCardLayout(mapOf("left" to listOf("tasks"), "collapsed" to "not-a-list"))
        assertEquals(listOf("tasks"), parsed.left)
        assertEquals(CardLayout.Default.right, parsed.right)
        assertEquals(emptyList(), parsed.collapsed)
    }

    @Test
    fun parse_ignoresEntriesOfTheWrongType() {
        val parsed = parseCardLayout(mapOf("left" to listOf("tasks", 7, null), "right" to listOf<Any>()))
        assertEquals(listOf("tasks"), parsed.left)
        assertEquals(emptyList(), parsed.right)
    }

    @Test
    fun parse_migratesTheOlderSingleOrderShape() {
        // What the original's database default looks like, so anything imported
        // from it arrives this way.
        val parsed = parseCardLayout(
            mapOf(
                "order" to listOf("clock", "timer", "tasks", "notes"),
                "collapsed" to listOf("tasks"),
            ),
        )
        // The clock is dropped, and the rest alternate across the two columns.
        assertEquals(listOf("timer", "notes"), parsed.left)
        assertEquals(listOf("tasks"), parsed.right)
        assertEquals(listOf("tasks"), parsed.collapsed)
    }

    @Test
    fun parse_prefersTheColumnsWhenBothShapesArePresent() {
        // Both present means a newer version wrote it; the columns are the authority.
        val parsed = parseCardLayout(
            mapOf("order" to listOf("timer", "tasks"), "left" to listOf("notes"), "right" to listOf<Any>()),
        )
        assertEquals(listOf("notes"), parsed.left)
        assertEquals(emptyList(), parsed.right)
    }

    @Test
    fun parse_neverLetsTheClockIntoAColumnViaMigration() {
        val parsed = parseCardLayout(mapOf("order" to listOf("clock", "timer")))
        assertFalse(CardId.Clock.id in parsed.left + parsed.right)
    }

    @Test
    fun cardWidth_fallsBackToHalf() {
        assertEquals(CardWidth.Full, CardWidth.fromId("full"))
        assertEquals(CardWidth.Half, CardWidth.fromId("half"))
        assertEquals(CardWidth.Half, CardWidth.fromId(null))
        assertEquals(CardWidth.Half, CardWidth.fromId("enormous"))
    }

    @Test
    fun cardId_resolvesStoredIdsAndRejectsUnknownOnes() {
        CardId.entries.forEach { assertEquals(it, CardId.fromId(it.id)) }
        assertEquals(null, CardId.fromId("sidebar"))
        assertEquals(null, CardId.fromId(null))
    }
}
