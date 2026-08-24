package io.github.dayboard.domain.text

import io.github.dayboard.domain.text.FormattedNode.Bold
import io.github.dayboard.domain.text.FormattedNode.Code
import io.github.dayboard.domain.text.FormattedNode.Italic
import io.github.dayboard.domain.text.FormattedNode.Link
import io.github.dayboard.domain.text.FormattedNode.Plain
import io.github.dayboard.domain.text.FormattedNode.Underline
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattedTextTest {

    // ------------------------------------------------------------------ plain

    @Test
    fun textWithNoMarkers_comesBackWhole() {
        assertEquals(listOf(Plain("just some text")), parseFormattedText("just some text"))
    }

    @Test
    fun emptyText_isNoNodesAtAll() {
        assertEquals(emptyList(), parseFormattedText(""))
    }

    // --------------------------------------------------------------- markers

    @Test
    fun eachMarkerIsRead() {
        assertEquals(listOf(Bold(listOf(Plain("loud")))), parseFormattedText("**loud**"))
        assertEquals(listOf(Italic(listOf(Plain("soft")))), parseFormattedText("*soft*"))
        assertEquals(listOf(Underline(listOf(Plain("under")))), parseFormattedText("__under__"))
        assertEquals(listOf(Code("code()")), parseFormattedText("`code()`"))
    }

    @Test
    fun markersSitAmongOrdinaryText() {
        assertEquals(
            listOf(Plain("a "), Bold(listOf(Plain("b"))), Plain(" c")),
            parseFormattedText("a **b** c"),
        )
    }

    @Test
    fun markersNest() {
        assertEquals(
            listOf(Bold(listOf(Plain("loud and "), Italic(listOf(Plain("soft"))), Plain(" here")))),
            parseFormattedText("**loud and *soft* here**"),
        )
    }

    @Test
    fun threeAsterisksInARowCloseTheBoldAndLeaveOneOver() {
        // `**a *b***` is genuinely ambiguous, and this is how the original resolves
        // it: the bold run closes at the first `**` it can, which is the first two
        // of the three, so the italic never closes and the last asterisk is text.
        // Pinned because it looks like a bug and is not worth "fixing" into a
        // difference from the original.
        assertEquals(
            listOf(Bold(listOf(Plain("loud and *soft"))), Plain("*")),
            parseFormattedText("**loud and *soft***"),
        )
    }

    @Test
    fun aSingleAsteriskInsideBoldIsNotTakenForItalic() {
        // The case the original needs lookbehind for: the asterisks of `**` must
        // never be read as the start of an italic run.
        assertEquals(listOf(Bold(listOf(Plain("x")))), parseFormattedText("**x**"))
    }

    @Test
    fun codeIsNotParsedInside() {
        // The point of code: you can write about the markers themselves.
        assertEquals(listOf(Code("**not bold**")), parseFormattedText("`**not bold**`"))
        assertEquals(
            listOf(Code("https://example.com")),
            parseFormattedText("`https://example.com`"),
        )
    }

    @Test
    fun anUnclosedMarkerStaysAsTypedText() {
        // Someone halfway through typing. Guessing at a closing marker would change
        // the look of the rest of the line while they were still writing it.
        assertEquals(listOf(Plain("**not closed")), parseFormattedText("**not closed"))
        assertEquals(listOf(Plain("*")), parseFormattedText("*"))
        assertEquals(listOf(Plain("`code")), parseFormattedText("`code"))
    }

    @Test
    fun emptyMarkersAreLeftAlone() {
        // Nothing to format, so nothing is formatted; `****` is four characters.
        assertEquals(listOf(Plain("****")), parseFormattedText("****"))
        assertEquals(listOf(Plain("``")), parseFormattedText("``"))
        assertEquals(listOf(Plain("____")), parseFormattedText("____"))
    }

    @Test
    fun aMarkerRunDoesNotCrossALineBreak() {
        // Matches the original, where `.` does not match a newline. Without this one
        // stray marker in a long note would embolden everything down to the next one.
        assertEquals(listOf(Plain("**one\ntwo**")), parseFormattedText("**one\ntwo**"))
        assertEquals(listOf(Plain("*one\ntwo*")), parseFormattedText("*one\ntwo*"))
        assertEquals(listOf(Plain("__one\ntwo__")), parseFormattedText("__one\ntwo__"))
    }

    @Test
    fun codeMayCrossALineBreak() {
        // Code is the exception, and deliberately so in the original: its content is
        // a character class rather than `.`, and a snippet is usually several lines.
        assertEquals(listOf(Code("one\ntwo")), parseFormattedText("`one\ntwo`"))
    }

    @Test
    fun twoRunsOfTheSameMarkerPairSeparately() {
        assertEquals(
            listOf(Bold(listOf(Plain("a"))), Plain(" and "), Bold(listOf(Plain("b")))),
            parseFormattedText("**a** and **b**"),
        )
    }

    // ------------------------------------------------------------------ links

    @Test
    fun aWebAddressBecomesALink() {
        assertEquals(
            listOf(Link("https://example.com/path")),
            parseFormattedText("https://example.com/path"),
        )
        assertEquals(listOf(Link("http://example.com")), parseFormattedText("http://example.com"))
    }

    @Test
    fun aLinkStopsBeforeThePunctuationAroundIt() {
        // Addresses are written inside brackets and at the ends of sentences all the
        // time. Swallowing the punctuation would break the link for a character
        // nobody meant to type into it.
        assertEquals(
            listOf(Plain("see ("), Link("https://example.com"), Plain(") for more")),
            parseFormattedText("see (https://example.com) for more"),
        )
        assertEquals(
            listOf(Link("https://example.com"), Plain(", and more")),
            parseFormattedText("https://example.com, and more"),
        )
    }

    @Test
    fun aLinkStopsAtWhitespace() {
        assertEquals(
            listOf(Link("https://example.com"), Plain(" next")),
            parseFormattedText("https://example.com next"),
        )
        assertEquals(
            listOf(Link("https://example.com"), Plain("\nnext")),
            parseFormattedText("https://example.com\nnext"),
        )
    }

    @Test
    fun aSchemeWithNothingAfterItIsNotALink() {
        // Linking this would make a link to nowhere, and swallow the words after it.
        assertEquals(listOf(Plain("https:// nothing")), parseFormattedText("https:// nothing"))
    }

    @Test
    fun aLinkInsideBoldIsStillALink() {
        assertEquals(
            listOf(Bold(listOf(Link("https://example.com")))),
            parseFormattedText("**https://example.com**"),
        )
    }

    @Test
    fun aLinkWinsOverTheMarkersInsideIt() {
        // Query strings are full of characters that would otherwise be markers.
        val parsed = parseFormattedText("https://example.com/a*b*c")

        assertEquals(listOf(Link("https://example.com/a*b*c")), parsed)
    }

    // ------------------------------------------------------------ real notes

    @Test
    fun aRealisticNoteReadsAsAWhole() {
        val parsed = parseFormattedText(
            "Call **Ana** about *the invoice*, see https://example.com/inv?id=7 " +
                "and run `npm test` first",
        )

        assertEquals(
            listOf(
                Plain("Call "),
                Bold(listOf(Plain("Ana"))),
                Plain(" about "),
                Italic(listOf(Plain("the invoice"))),
                Plain(", see "),
                Link("https://example.com/inv?id=7"),
                Plain(" and run "),
                Code("npm test"),
                Plain(" first"),
            ),
            parsed,
        )
    }
}
