package io.github.dayboard.domain.model

/**
 * Moves the item at [fromIndex] to [toIndex], leaving the rest in order.
 *
 * The shared half of both reorder rules below. Out-of-range indices leave the list
 * alone rather than throwing, because they arrive from a drag whose hit test can
 * miss.
 */
fun <T> List<T>.moved(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) return this

    val remaining = toMutableList()
    val moving = remaining.removeAt(fromIndex)
    remaining.add(toIndex, moving)
    return remaining
}

/**
 * Reorders the tasks currently on screen by dealing out their own position values.
 *
 * The subtlety is that the list being dragged is not the whole list. Finished tasks
 * and tasks hidden by the tag filter are still there, still holding positions in
 * between. Numbering the visible tasks 0, 1, 2 would collide with those hidden
 * positions and shuffle the hidden tasks the moment the filter was cleared.
 *
 * So the positions themselves are pooled: take the values the visible tasks already
 * hold, sort them, and deal them back out in the new visual order. The set of
 * numbers in use does not change at all - only which task holds which - so nothing
 * hidden can be disturbed.
 *
 * Returns only the tasks whose position actually changed, so a drag that ends where
 * it started writes nothing.
 */
fun reorderVisible(visible: List<Positioned>, fromIndex: Int, toIndex: Int): Map<String, Int> {
    val reordered = visible.moved(fromIndex, toIndex)
    if (reordered === visible) return emptyMap()

    val pool = visible.map { it.position }.sorted()

    return reordered
        .mapIndexedNotNull { index, item ->
            pool[index].takeIf { it != item.position }?.let { item.id to it }
        }
        .toMap()
}

/**
 * Reorders a list and renumbers it 0, 1, 2 and so on.
 *
 * Only safe when the list being dragged is the *whole* list, which is true of
 * subtasks: nothing filters them and there is no separate finished group, so
 * compacting cannot collide with a position held by something off screen. It also
 * keeps the numbers tidy, which matters for something added and deleted as freely
 * as a subtask.
 *
 * Returns only what changed, so a drag that ends where it started writes nothing.
 */
fun reorderCompacting(items: List<Positioned>, fromIndex: Int, toIndex: Int): Map<String, Int> {
    val reordered = items.moved(fromIndex, toIndex)
    if (reordered === items) return emptyMap()

    return reordered
        .mapIndexedNotNull { index, item -> (item.id to index).takeIf { item.position != index } }
        .toMap()
}

/** Applies a map of new positions to the tasks it names. */
fun List<Task>.withPositions(positions: Map<String, Int>): List<Task> = map { task ->
    val position = positions[task.id]
    if (position == null) task else task.copy(position = position)
}
