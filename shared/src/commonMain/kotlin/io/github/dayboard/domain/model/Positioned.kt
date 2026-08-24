package io.github.dayboard.domain.model

/**
 * Something that sits in a list the user can rearrange.
 *
 * Exists so the reorder rules can be written once. Tasks and notes are otherwise
 * unrelated - different fields, different storage, different screens - but they are
 * dragged in exactly the same way, and an algorithm this fiddly is not worth having
 * two copies of.
 */
interface Positioned {

    val id: String

    /**
     * Where this sits among its siblings.
     *
     * Not necessarily 0, 1, 2. Top-level positions are never compacted, so they can
     * be sparse; nothing may assume otherwise.
     */
    val position: Int
}
