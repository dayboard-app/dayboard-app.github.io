package io.github.dayboard.domain.model

/**
 * The cards the board can show.
 *
 * [id] is the stored value: it appears in the saved layout and in the settings
 * document, so renaming one would orphan every arrangement already saved.
 */
enum class CardId(val id: String, val title: String) {
    Clock("clock", "Clock"),
    Timer("timer", "Pomodoro"),
    Tasks("tasks", "Tasks"),
    Notes("notes", "Notes"),
    ;

    companion object {
        fun fromId(value: String?): CardId? = entries.firstOrNull { it.id == value }
    }
}

/** Which of the two columns a card sits in. */
enum class BoardColumn { Left, Right }

/**
 * How wide a card is drawn.
 *
 * Stored and round-tripped but never read: the original carries it too and always
 * renders both columns at equal width. Kept so an arrangement saved by one version
 * is not silently rewritten by another.
 */
enum class CardWidth(val id: String) {
    Half("half"),
    Full("full"),
    ;

    companion object {
        fun fromId(value: String?): CardWidth = entries.firstOrNull { it.id == value } ?: Half
    }
}

/**
 * Where every card sits, and which of them are rolled up.
 *
 * The clock is deliberately absent from both columns: it always renders across the
 * top and cannot be dragged, so putting it in a column would let it be moved.
 */
data class CardLayout(
    val left: List<String>,
    val right: List<String>,
    val widths: Map<String, CardWidth>,
    val collapsed: List<String>,
) {

    fun column(which: BoardColumn): List<String> =
        if (which == BoardColumn.Left) left else right

    fun withColumn(which: BoardColumn, cards: List<String>): CardLayout =
        if (which == BoardColumn.Left) copy(left = cards) else copy(right = cards)

    fun isCollapsed(card: CardId): Boolean = card.id in collapsed

    /** Rolls a card up, or opens it again. */
    fun toggleCollapsed(card: CardId): CardLayout = copy(
        collapsed = if (card.id in collapsed) collapsed - card.id else collapsed + card.id,
    )

    companion object {
        val Default: CardLayout = CardLayout(
            left = listOf(CardId.Timer.id),
            right = listOf(CardId.Tasks.id, CardId.Notes.id),
            widths = mapOf(
                CardId.Timer.id to CardWidth.Half,
                CardId.Tasks.id to CardWidth.Half,
                CardId.Notes.id to CardWidth.Half,
            ),
            collapsed = emptyList(),
        )
    }
}

/**
 * Moves a card that was dragged from one position to another.
 *
 * The indices are positions in what the user could actually *see*, which is not
 * the same as positions in the stored column: a card hidden by its settings toggle
 * still occupies a slot in [CardLayout.left] or [CardLayout.right]. Dropping onto
 * the second visible card must therefore land beside that card, not at stored
 * index 1. [isVisible] is what bridges the two, and getting this wrong silently
 * reorders cards the user cannot see.
 *
 * Returns the layout unchanged when the drag was a no-op or the indices do not
 * name a card.
 */
fun CardLayout.moveCard(
    from: BoardColumn,
    to: BoardColumn,
    sourceIndex: Int,
    destinationIndex: Int,
    isVisible: (String) -> Boolean,
): CardLayout {
    val dragged = column(from).filter(isVisible).getOrNull(sourceIndex) ?: return this

    return if (from == to) {
        withColumn(from, column(from).reorderedWithin(dragged, sourceIndex, destinationIndex, isVisible))
    } else {
        val target = column(to).filter(isVisible).getOrNull(destinationIndex)
        val destination = column(to).let { cards ->
            // A drop past the last visible card appends; the original does the same,
            // which is what makes an empty column a valid target.
            if (target == null) cards + dragged else cards.toMutableList().apply {
                add(indexOf(target), dragged)
            }
        }
        withColumn(from, column(from) - dragged).withColumn(to, destination)
    }
}

/**
 * Reorders one column around the card that moved.
 *
 * Anchors on the card currently occupying the destination slot rather than
 * counting positions, then places the dragged card after it when moving down and
 * before it when moving up. Anchoring is what keeps hidden cards where they were:
 * counting would shift them.
 */
private fun List<String>.reorderedWithin(
    dragged: String,
    sourceIndex: Int,
    destinationIndex: Int,
    isVisible: (String) -> Boolean,
): List<String> {
    val target = filter(isVisible).getOrNull(destinationIndex)
    if (target == null || target == dragged) return this

    val remaining = this - dragged
    val anchor = remaining.indexOf(target)
    if (anchor < 0) return this

    val insertAt = if (destinationIndex > sourceIndex) anchor + 1 else anchor
    return remaining.toMutableList().apply { add(insertAt, dragged) }
}

/**
 * Reads a stored layout, falling back field by field.
 *
 * Every field is validated on its own rather than rejecting the whole object,
 * because a document half-written by an older version should cost the user only
 * the part that is unreadable.
 *
 * Also accepts the older single-`order` shape. Dayboard never writes it, but the
 * original's database default is exactly that, so anything imported from there
 * arrives in it.
 */
fun parseCardLayout(raw: Map<String, Any?>?): CardLayout {
    if (raw == null) return CardLayout.Default

    val left = raw["left"] as? List<*>
    val right = raw["right"] as? List<*>
    val order = raw["order"] as? List<*>

    val widths = (raw["widths"] as? Map<*, *>)
        ?.entries
        ?.mapNotNull { (key, value) ->
            (key as? String)?.let { it to CardWidth.fromId(value as? String) }
        }
        ?.toMap()
        ?: CardLayout.Default.widths

    val collapsed = (raw["collapsed"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    // The legacy shape, recognised only when neither column is present: a document
    // carrying both `order` and `left` was written by a newer version, and the
    // columns are the authority.
    if (order != null && left == null && right == null) {
        // The clock is dropped rather than placed: it is not a column card.
        val cards = order.filterIsInstance<String>().filter { it != CardId.Clock.id }
        return CardLayout(
            left = cards.filterIndexed { index, _ -> index % 2 == 0 },
            right = cards.filterIndexed { index, _ -> index % 2 == 1 },
            widths = widths,
            collapsed = collapsed,
        )
    }

    return CardLayout(
        left = left?.filterIsInstance<String>() ?: CardLayout.Default.left,
        right = right?.filterIsInstance<String>() ?: CardLayout.Default.right,
        widths = widths,
        collapsed = collapsed,
    )
}
