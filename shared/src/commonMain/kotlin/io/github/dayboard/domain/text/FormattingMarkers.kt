package io.github.dayboard.domain.text

/**
 * The four things the formatting toolbar can do.
 *
 * Each is a pair of markers written around the selection. They are the same markers
 * [parseFormattedText] reads back, which is the whole contract between the toolbar
 * and the renderer.
 */
enum class FormattingMarker(val label: String, val prefix: String, val suffix: String) {
    Bold("Bold", "**", "**"),
    Italic("Italic", "*", "*"),
    Underline("Underline", "__", "__"),
    Code("Code", "`", "`"),
}

/** Where the cursor is, or what is highlighted. Equal ends mean a bare cursor. */
data class TextSelection(val start: Int, val end: Int)

/** Text with a selection in it, as a field would report and then be set back to. */
data class FormattedField(val text: String, val selection: TextSelection)

/**
 * Adds a marker pair around the selection, or takes an existing one away.
 *
 * The toggle is what makes the toolbar usable: pressing Bold twice has to leave the
 * text as it was found, not leave `****` behind. A pair counts as "already there"
 * when it sits immediately outside the selection, which is exactly where this put it
 * the first time - so a selection made by clicking the same button again matches.
 *
 * A bare cursor is allowed and wraps nothing, leaving the cursor between the new
 * markers ready to type into. That is how a formatting button behaves everywhere
 * else, and it is the common case: press Bold, then type.
 *
 * A selection reaching outside the text, or backwards, is clamped rather than
 * rejected. This is called with whatever a DOM field reports, and a field being
 * re-rendered can report a stale selection for a frame.
 */
fun applyFormatting(
    text: String,
    selection: TextSelection,
    marker: FormattingMarker,
): FormattedField {
    val start = selection.start.coerceIn(0, text.length)
    val end = selection.end.coerceIn(start, text.length)

    val alreadyMarked = text.hasMarkerAround(start, end, marker)

    return if (alreadyMarked) {
        text.removeMarker(start, end, marker)
    } else {
        text.addMarker(start, end, marker)
    }
}

/** Whether the marker pair sits immediately outside the selection already. */
private fun String.hasMarkerAround(start: Int, end: Int, marker: FormattingMarker): Boolean {
    val before = start - marker.prefix.length
    if (before < 0 || end + marker.suffix.length > length) return false

    return substring(before, start) == marker.prefix &&
        substring(end, end + marker.suffix.length) == marker.suffix
}

private fun String.removeMarker(start: Int, end: Int, marker: FormattingMarker): FormattedField {
    val before = start - marker.prefix.length

    return FormattedField(
        text = substring(0, before) + substring(start, end) + substring(end + marker.suffix.length),
        // The same characters stay selected; they have just moved left by the
        // markers that were taken from in front of them.
        selection = TextSelection(before, end - marker.prefix.length),
    )
}

private fun String.addMarker(start: Int, end: Int, marker: FormattingMarker): FormattedField =
    FormattedField(
        text = substring(0, start) + marker.prefix + substring(start, end) +
            marker.suffix + substring(end),
        // Selects what was selected before, now inside the markers rather than the
        // markers themselves - so pressing another button formats the same words.
        selection = TextSelection(start + marker.prefix.length, end + marker.prefix.length),
    )
