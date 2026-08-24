package io.github.dayboard.domain.text

import kotlin.test.Test
import kotlin.test.assertEquals

class FormattingMarkersTest {

    @Test
    fun wrapsTheSelection() {
        val result = applyFormatting("hello world", TextSelection(6, 11), FormattingMarker.Bold)

        assertEquals("hello **world**", result.text)
    }

    @Test
    fun leavesTheSameWordsSelectedInsideTheMarkers() {
        // So that pressing a second button formats the same words rather than the
        // markers the first one just added.
        val result = applyFormatting("hello world", TextSelection(6, 11), FormattingMarker.Bold)

        assertEquals(TextSelection(8, 13), result.selection)
        assertEquals("world", result.text.substring(8, 13))
    }

    @Test
    fun aBareCursorLeavesTheCursorReadyToTypeInto() {
        // Press Bold, then type. The common case, and the one an implementation
        // written only for highlighted text gets wrong.
        val result = applyFormatting("ab", TextSelection(1, 1), FormattingMarker.Bold)

        assertEquals("a****b", result.text)
        assertEquals(TextSelection(3, 3), result.selection)
    }

    @Test
    fun everyMarkerWrapsWithItsOwnPair() {
        val cases = mapOf(
            FormattingMarker.Bold to "**x**",
            FormattingMarker.Italic to "*x*",
            FormattingMarker.Underline to "__x__",
            FormattingMarker.Code to "`x`",
        )

        cases.forEach { (marker, expected) ->
            val result = applyFormatting("x", TextSelection(0, 1), marker)
            assertEquals(expected, result.text, marker.label)
        }
    }

    @Test
    fun pressingTheSameButtonAgainTakesTheMarkersOff() {
        // The whole point of the toggle: Bold twice leaves the text as it was found,
        // rather than leaving `****` behind.
        val added = applyFormatting("hello world", TextSelection(6, 11), FormattingMarker.Bold)
        val removed = applyFormatting(added.text, added.selection, FormattingMarker.Bold)

        assertEquals("hello world", removed.text)
        assertEquals(TextSelection(6, 11), removed.selection)
    }

    @Test
    fun removingIsAlsoARoundTripForEveryMarker() {
        FormattingMarker.entries.forEach { marker ->
            val added = applyFormatting("some text here", TextSelection(5, 9), marker)
            val removed = applyFormatting(added.text, added.selection, marker)

            assertEquals("some text here", removed.text, marker.label)
            assertEquals(TextSelection(5, 9), removed.selection, marker.label)
        }
    }

    @Test
    fun onlyMarkersImmediatelyOutsideTheSelectionCount() {
        // `**a b**` with only `b` selected is not a bold `b`, so this must wrap
        // rather than unwrap - otherwise it would tear one marker off a pair.
        val result = applyFormatting("**a b**", TextSelection(4, 5), FormattingMarker.Bold)

        assertEquals("**a **b****", result.text)
    }

    @Test
    fun italicOnBoldTextTakesOneAsteriskFromEachSide() {
        // Surprising, and the original's behaviour exactly: the toggle only looks at
        // the characters immediately outside the selection, and for `**word**` with
        // `word` selected those are a single `*` on each side - which is the italic
        // marker. So it reads as "already italic" and removes, leaving `*word*`.
        //
        // Pinned rather than fixed. Making it wrap instead would need the toggle to
        // understand nesting, and would then disagree with the original on text
        // people already have.
        val result = applyFormatting("**word**", TextSelection(2, 6), FormattingMarker.Italic)

        assertEquals("*word*", result.text)
    }

    @Test
    fun aMarkerLongerThanTheOneAroundTheSelectionIsAdded() {
        // The reverse of the case above, where no confusion is possible: single
        // asterisks outside the selection are not a `**` pair, so Bold wraps.
        val result = applyFormatting("*word*", TextSelection(1, 5), FormattingMarker.Bold)

        assertEquals("***word***", result.text)
    }

    @Test
    fun aSelectionReachingPastTheTextIsBroughtBackInside() {
        // A field being re-rendered can report a stale selection for a frame.
        val result = applyFormatting("hi", TextSelection(0, 99), FormattingMarker.Bold)

        assertEquals("**hi**", result.text)
    }

    @Test
    fun aBackwardsSelectionIsTreatedAsACursor() {
        // Nothing sensible to wrap, and the alternative is a substring call that
        // throws in the middle of a keystroke.
        val result = applyFormatting("hello", TextSelection(4, 1), FormattingMarker.Bold)

        assertEquals("hell****o", result.text)
    }

    @Test
    fun aNegativeSelectionIsBroughtBackInside() {
        val result = applyFormatting("hi", TextSelection(-5, 1), FormattingMarker.Code)

        assertEquals("`h`i", result.text)
    }

    @Test
    fun theResultReadsBackAsTheFormattingThatWasAsked() {
        // The toolbar and the renderer only agree through the markers, so the
        // round trip through both is the contract worth pinning.
        val bolded = applyFormatting("make this loud", TextSelection(10, 14), FormattingMarker.Bold)

        assertEquals(
            listOf(
                FormattedNode.Plain("make this "),
                FormattedNode.Bold(listOf(FormattedNode.Plain("loud"))),
            ),
            parseFormattedText(bolded.text),
        )
    }

    @Test
    fun markerStringsAreTheOnesTheParserReads() {
        assertEquals("**", FormattingMarker.Bold.prefix)
        assertEquals("*", FormattingMarker.Italic.prefix)
        assertEquals("__", FormattingMarker.Underline.prefix)
        assertEquals("`", FormattingMarker.Code.prefix)
        FormattingMarker.entries.forEach {
            assertEquals(it.prefix, it.suffix, "${it.label} wraps with the same pair on both sides")
        }
    }
}
