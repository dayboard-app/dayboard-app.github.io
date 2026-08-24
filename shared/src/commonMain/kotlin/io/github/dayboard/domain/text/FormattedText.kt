package io.github.dayboard.domain.text

/**
 * A piece of text after the inline markers have been read.
 *
 * The result is a tree rather than a string of HTML, so the domain never decides
 * what a bold run looks like - only that it is bold. It also means nothing here can
 * produce markup, which removes the question of escaping entirely.
 */
sealed interface FormattedNode {

    /** Text with no markers in it. */
    data class Plain(val text: String) : FormattedNode

    /**
     * A web address that was written out in full.
     *
     * The address is both the target and the label, exactly as typed: this is
     * detection rather than link editing, so there is nowhere a separate label
     * could have come from.
     */
    data class Link(val url: String) : FormattedNode

    data class Bold(val children: List<FormattedNode>) : FormattedNode

    data class Italic(val children: List<FormattedNode>) : FormattedNode

    data class Underline(val children: List<FormattedNode>) : FormattedNode

    /**
     * Text to be shown exactly as written.
     *
     * Holds a string rather than children, and that is the point: markers inside
     * code are content. Parsing them would make it impossible to write about the
     * markers themselves.
     */
    data class Code(val text: String) : FormattedNode
}

/**
 * Reads the inline markers in [text] into a tree.
 *
 * Written as a scanner rather than the original's single regular expression, for
 * two reasons. That expression needs lookbehind to tell `*` from `**`, which older
 * Safari cannot compile at all - not "fails to match" but throws while building the
 * pattern, taking the page with it. And a scanner behaves identically on both
 * platforms the tests run on, where two regex engines need not.
 *
 * Markers only pair within one call, and an unclosed marker is left as the character
 * that was typed. That is not leniency for its own sake: someone halfway through
 * typing `**bold` should see what they typed, not have the rest of the line change
 * appearance under them.
 */
fun parseFormattedText(text: String): List<FormattedNode> {
    val nodes = mutableListOf<FormattedNode>()
    val plain = StringBuilder()
    var index = 0

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            nodes += FormattedNode.Plain(plain.toString())
            plain.clear()
        }
    }

    while (index < text.length) {
        val scanned = text.readNodeAt(index)

        if (scanned == null) {
            plain.append(text[index])
            index++
        } else {
            flushPlain()
            nodes += scanned.node
            index = scanned.end
        }
    }

    flushPlain()
    return nodes
}

/** A node and where it ended, so the scanner knows where to carry on. */
private class Scanned(val node: FormattedNode, val end: Int)

/**
 * Tries to read one marked-up run starting exactly at [index].
 *
 * Null means there is nothing special here and the character is ordinary text. The
 * order is the original's: a web address wins over everything, then the
 * two-character markers, then the single ones.
 */
private fun String.readNodeAt(index: Int): Scanned? =
    readLinkAt(index)
        ?: readWrappedAt(index, BOLD_MARKER) { FormattedNode.Bold(parseFormattedText(it)) }
        ?: readWrappedAt(index, UNDERLINE_MARKER) {
            FormattedNode.Underline(parseFormattedText(it))
        }
        ?: readItalicAt(index)
        // Code may run over several lines where the others may not, and its content
        // is taken literally rather than parsed.
        ?: readWrappedAt(index, CODE_MARKER, allowNewlines = true) { FormattedNode.Code(it) }

/**
 * Reads a bare web address.
 *
 * It ends at the first character that could not plausibly belong to it. Closing
 * brackets and a trailing comma are excluded because addresses are so often written
 * inside parentheses or in a list, and swallowing that punctuation would break the
 * link for the sake of a character nobody meant to include.
 */
private fun String.readLinkAt(index: Int): Scanned? {
    val scheme = LINK_SCHEMES.firstOrNull { startsWith(it, index) } ?: return null

    var end = index + scheme.length
    while (end < length && !this[end].endsALink()) end++

    // A scheme with nothing after it is not an address, and linking it would make a
    // link to nowhere out of the text that follows.
    if (end == index + scheme.length) return null

    return Scanned(FormattedNode.Link(substring(index, end)), end)
}

/**
 * Reads `marker`…`marker`, with at least one character between them.
 *
 * Unless [allowNewlines], the run has to close on the line it opened on. That is the
 * original's behaviour, which comes from `.` not matching a newline, and it is worth
 * keeping: in a long note a single stray `**` would otherwise turn everything up to
 * the next one bold.
 */
private fun String.readWrappedAt(
    index: Int,
    marker: String,
    allowNewlines: Boolean = false,
    build: (String) -> FormattedNode,
): Scanned? {
    if (!startsWith(marker, index)) return null

    val contentStart = index + marker.length
    val contentEnd = indexOf(marker, startIndex = contentStart + 1)
    if (contentEnd < 0) return null

    val content = substring(contentStart, contentEnd)
    if (!allowNewlines && content.any { it == '\n' }) return null

    return Scanned(build(content), contentEnd + marker.length)
}

/**
 * Reads `*`…`*`, ignoring asterisks that belong to a `**`.
 *
 * This is the case the original needs lookbehind for. Here it is a question about
 * the neighbouring characters, asked directly.
 */
private fun String.readItalicAt(index: Int): Scanned? {
    if (!isLoneAsteriskAt(index)) return null

    var contentEnd = index + 1
    while (contentEnd < length && this[contentEnd] != '\n') {
        if (isLoneAsteriskAt(contentEnd)) break
        contentEnd++
    }

    // Unclosed, or ran into a line break. There is no "empty run" case to check:
    // the opening asterisk is only a lone one if the next character is not an
    // asterisk, so the loop can never stop on the very next character.
    if (contentEnd >= length || this[contentEnd] != ASTERISK) return null

    return Scanned(
        FormattedNode.Italic(parseFormattedText(substring(index + 1, contentEnd))),
        contentEnd + 1,
    )
}

/** Whether the character at [index] is an asterisk with no asterisk either side. */
private fun String.isLoneAsteriskAt(index: Int): Boolean =
    this[index] == ASTERISK &&
        getOrNull(index - 1) != ASTERISK &&
        getOrNull(index + 1) != ASTERISK

private fun Char.endsALink(): Boolean = isWhitespace() || this in LINK_TERMINATORS

private const val ASTERISK = '*'
private const val BOLD_MARKER = "**"
private const val UNDERLINE_MARKER = "__"
private const val CODE_MARKER = "`"

private val LINK_SCHEMES = listOf("https://", "http://")

/** The punctuation an address is commonly written among, and so stops at. */
private const val LINK_TERMINATORS = "<>\"')]},"
