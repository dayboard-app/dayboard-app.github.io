package io.github.dayboard.domain.model

/**
 * A label that can be put on tasks and filtered by.
 *
 * The colour is a six-digit hex string rather than a token, because the user picks
 * it from a fixed palette and it has to survive in the database exactly as chosen.
 * It is the one colour in the app that does not come from the theme, which is why
 * nothing here turns it into a CSS colour: keel's pill takes the stored six digits
 * and a named strength, and works out the fill itself.
 */
data class Tag(
    val id: String,
    val name: String,
    val color: String,
    val emoji: String? = null,
)

/* `Tag.background` used to be here - the tag's colour at a given strength, as a CSS
   colour. Every caller was building a pill by hand and setting the fill inline; keel's
   `Pill` and `PillButton` take the colour and the shade and work it out themselves, so
   there was nothing left calling it. */

/**
 * Finds a tag already called [name], ignoring case and surrounding space.
 *
 * This is what stops the tag list filling up with "Work", "work" and "work ". The
 * creator uses it to attach the existing tag instead of making a second one, which
 * is friendlier than refusing: the user asked for a tag with that name, and they
 * end up with one.
 */
fun List<Tag>.findByName(name: String): Tag? {
    val wanted = name.trim()
    if (wanted.isEmpty()) return null

    return firstOrNull { it.name.trim().equals(wanted, ignoreCase = true) }
}

/**
 * How much of an emoji a tag keeps.
 *
 * Two UTF-16 units, matching the original's `maxLength`. That is one emoji for most
 * of them, and the field is a nicety rather than a data type worth policing.
 */
const val TAG_EMOJI_MAX_LENGTH: Int = 2

/** Trims an entered emoji to what a tag will store, or null if there is none. */
fun normalizeTagEmoji(emoji: String?): String? =
    emoji.orEmpty().trim().take(TAG_EMOJI_MAX_LENGTH).ifEmpty { null }
