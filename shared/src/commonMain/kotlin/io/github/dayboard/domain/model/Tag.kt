package io.github.dayboard.domain.model

/**
 * A label that can be put on tasks and filtered by.
 *
 * The colour is a six-digit hex string rather than a token, because the user picks
 * it from a fixed palette and it has to survive in the database exactly as chosen.
 * It is the one colour in the app that does not come from the theme.
 */
data class Tag(
    val id: String,
    val name: String,
    val color: String,
    val emoji: String? = null,
)

/**
 * How strongly a tag's colour is used behind text.
 *
 * The original writes these as a two-digit hex alpha appended to the colour, and
 * uses exactly four levels. Naming them keeps the numbers out of the markup and
 * makes it obvious when a fifth one is being invented.
 */
enum class TagShade(val hexAlpha: String) {
    /** An unselected filter chip, or a tag not yet on this task. */
    Faint("15"),

    /** A pill inside a collapsed task row, competing with the title next to it. */
    Inline("18"),

    /** A pill with room around it: an expanded row, or a dialog. */
    Pill("20"),

    /** The selected filter chip. */
    Selected("30"),
}

/**
 * The tag's colour at a given strength, as a CSS colour.
 *
 * Eight-digit hex rather than `rgba(...)` so the stored six digits pass through
 * untouched, which makes a wrong colour easy to trace back to what was stored.
 */
fun Tag.background(shade: TagShade): String = color + shade.hexAlpha

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
 * The colours the tag creator offers, in the order they are shown.
 *
 * Ten fixed swatches rather than a colour picker: every tag stays legible against
 * both themes, and no two tags end up indistinguishable.
 */
val TAG_COLORS: List<String> = listOf(
    "#6366f1",
    "#ec4899",
    "#f59e0b",
    "#10b981",
    "#3b82f6",
    "#8b5cf6",
    "#ef4444",
    "#14b8a6",
    "#f97316",
    "#64748b",
)

/** The swatch a new tag starts on. */
val DEFAULT_TAG_COLOR: String = TAG_COLORS.first()

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
