package io.github.dayboard.ui.cards

/**
 * Adds the `--expanded` modifier to a block's class, for the full-screen card.
 *
 * Every card renders the same markup at both sizes and lets CSS decide how big it
 * is, so this is the whole of the difference between the two.
 */
internal fun sized(block: String, expanded: Boolean): Array<String> =
    if (expanded) arrayOf(block, "$block--expanded") else arrayOf(block)
