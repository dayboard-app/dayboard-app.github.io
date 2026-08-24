package io.github.dayboard.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dayboard.domain.model.BoardColumn
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.pointerevents.PointerEvent

/**
 * Dragging a card from one place on the board to another.
 *
 * Hand-rolled because the original's drag-and-drop library is React-only. It
 * tracks the gesture on `window` rather than on the card, so a finger that moves
 * faster than the layout - or leaves the board entirely - does not silently end
 * the drag.
 *
 * Only the gesture lives here. Where a card actually lands is `CardLayout.moveCard`
 * in `:shared`, which is unit-tested; this decides nothing that a test could not
 * otherwise reach.
 */
class DragController {

    /** The card under the finger, and where it came from. */
    data class Drag(val card: String, val from: BoardColumn, val sourceIndex: Int)

    /** Where it would land if released now. */
    data class Target(val column: BoardColumn, val index: Int)

    var drag: Drag? by mutableStateOf(null)
        private set

    var target: Target? by mutableStateOf(null)
        private set

    private val columnElements = mutableMapOf<BoardColumn, HTMLElement>()
    private val cardElements = mutableMapOf<String, HTMLElement>()

    /**
     * What the user can currently see in each column, in order.
     *
     * Kept up to date by the board on every composition, because a hidden card
     * occupies a slot in storage but not on screen, and the drop index is a
     * position among the visible ones.
     */
    private var visibleByColumn: Map<BoardColumn, List<String>> = emptyMap()

    private var onDrop: ((Drag, Target) -> Unit)? = null
    private var moveHandler: ((Event) -> Unit)? = null
    private var endHandler: ((Event) -> Unit)? = null

    fun registerColumn(column: BoardColumn, element: HTMLElement?) {
        if (element == null) columnElements.remove(column) else columnElements[column] = element
    }

    fun registerCard(card: String, element: HTMLElement?) {
        if (element == null) cardElements.remove(card) else cardElements[card] = element
    }

    fun setVisible(left: List<String>, right: List<String>) {
        visibleByColumn = mapOf(BoardColumn.Left to left, BoardColumn.Right to right)
    }

    fun isDragging(card: String): Boolean = drag?.card == card

    /**
     * Begins a drag.
     *
     * The listeners go on `window` and use pointer events, which cover a mouse and
     * a finger with one path. They are removed again the moment the gesture ends,
     * so nothing is left watching the document between drags.
     */
    fun begin(card: String, from: BoardColumn, sourceIndex: Int, onDrop: (Drag, Target) -> Unit) {
        if (drag != null) return

        drag = Drag(card, from, sourceIndex)
        target = Target(from, sourceIndex)
        this.onDrop = onDrop

        val move: (Event) -> Unit = { event ->
            (event as? PointerEvent)?.let { target = hitTest(it.clientX.toDouble(), it.clientY.toDouble()) }
        }
        val end: (Event) -> Unit = { finish() }

        moveHandler = move
        endHandler = end
        window.addEventListener("pointermove", move)
        window.addEventListener("pointerup", end)
        // A drag interrupted by the browser - a context menu, a lost pointer - must
        // end as a cancel rather than leaving the board stuck mid-gesture.
        window.addEventListener("pointercancel", end)
    }

    private fun finish() {
        val finished = drag
        val destination = target
        detach()

        drag = null
        target = null
        val drop = onDrop
        onDrop = null

        if (finished != null && destination != null) drop?.invoke(finished, destination)
    }

    private fun detach() {
        moveHandler?.let { window.removeEventListener("pointermove", it) }
        endHandler?.let {
            window.removeEventListener("pointerup", it)
            window.removeEventListener("pointercancel", it)
        }
        moveHandler = null
        endHandler = null
    }

    /**
     * Works out where the pointer is pointing.
     *
     * The column is chosen horizontally, so dragging above or below the board still
     * targets a column rather than nothing. Within it, the index is the number of
     * cards whose midpoint the pointer has passed - the same rule a list uses to
     * decide whether you are dropping above or below a row.
     *
     * The dragged card is deliberately counted along with the rest: the index is a
     * position in the list as it stands now, which is what `moveCard` expects.
     */
    private fun hitTest(x: Double, y: Double): Target? {
        val column = columnElements.entries
            .firstOrNull { (_, element) ->
                val bounds = element.getBoundingClientRect()
                x >= bounds.left && x <= bounds.right
            }
            ?.key
            ?: return target

        val index = visibleByColumn[column].orEmpty().count { card ->
            val element = cardElements[card] ?: return@count false
            val bounds = element.getBoundingClientRect()
            y > bounds.top + bounds.height / 2
        }

        return Target(column, index)
    }
}
