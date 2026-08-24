package io.github.dayboard.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.pointerevents.PointerEvent

/**
 * Dragging a row up and down one vertical list.
 *
 * Separate from the board's [DragController], which moves cards between two columns
 * and answers a two-dimensional question. This one only ever answers "how far down",
 * and it is used by every list in the app: the task list, and the subtasks in both
 * task dialogs.
 *
 * One instance can serve several lists, because the list being dragged is named at
 * [begin] rather than held here. That matters: a dialog's subtasks and the task list
 * behind it are on screen at the same time, and a drag in one must not measure
 * itself against rows in the other.
 *
 * Where the row lands is [io.github.dayboard.domain.model.moved] and its callers in
 * `:shared`, which are tested. Only the gesture is here.
 */
class ListDragController {

    /** The row under the finger, or null when nothing is being dragged. */
    var draggingId: String? by mutableStateOf(null)
        private set

    /** Where it would land if released now. */
    var dropIndex: Int? by mutableStateOf(null)
        private set

    private val rows = mutableMapOf<String, HTMLElement>()
    private var order: List<String> = emptyList()
    private var sourceIndex: Int = 0
    private var onDrop: ((Int, Int) -> Unit)? = null
    private var moveHandler: ((Event) -> Unit)? = null
    private var endHandler: ((Event) -> Unit)? = null

    fun registerRow(id: String, element: HTMLElement?) {
        if (element == null) rows.remove(id) else rows[id] = element
    }

    fun isDragging(id: String): Boolean = draggingId == id

    /**
     * Begins a drag of the row [id], which currently sits at [sourceIndex] in [order].
     *
     * The listeners go on `window` and use pointer events, which cover a mouse and a
     * finger by one path. A gesture that outruns the layout, or leaves the list
     * entirely, therefore still ends properly.
     */
    fun begin(
        id: String,
        sourceIndex: Int,
        order: List<String>,
        onDrop: (fromIndex: Int, toIndex: Int) -> Unit,
    ) {
        if (draggingId != null) return

        draggingId = id
        dropIndex = sourceIndex
        this.sourceIndex = sourceIndex
        this.order = order
        this.onDrop = onDrop

        val move: (Event) -> Unit = { event ->
            (event as? PointerEvent)?.let { dropIndex = indexAt(it.clientY.toDouble()) }
        }
        val end: (Event) -> Unit = { finish() }

        moveHandler = move
        endHandler = end
        window.addEventListener("pointermove", move)
        window.addEventListener("pointerup", end)
        // A gesture the browser takes away - a context menu, a lost pointer - has to
        // end as a cancel rather than leaving a row stuck to the cursor.
        window.addEventListener("pointercancel", end)
    }

    private fun finish() {
        val from = sourceIndex
        val to = dropIndex
        detach()

        draggingId = null
        dropIndex = null
        val drop = onDrop
        onDrop = null
        order = emptyList()

        if (to != null && to != from) drop?.invoke(from, to)
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
     * How far down the list the pointer has reached.
     *
     * The number of *other* rows whose midpoint it has passed. Excluding the dragged
     * row is what makes the answer correct in both directions: with it counted, a
     * row dragged downwards would report one position too far, because it would be
     * counting itself on the way past.
     *
     * The result is an index into the list as it stands now, which is what the
     * reorder functions expect - so dragging a row to the very bottom of a list of
     * three gives 2, not 3.
     *
     * Rows that have gone from the DOM are skipped rather than assumed present: a
     * change from another device can remove one mid-gesture.
     */
    private fun indexAt(y: Double): Int = order.count { id ->
        if (id == draggingId) return@count false

        val element = rows[id] ?: return@count false
        val bounds = element.getBoundingClientRect()
        y > bounds.top + bounds.height / 2
    }
}
