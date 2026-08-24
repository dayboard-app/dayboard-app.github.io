package io.github.dayboard.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TagTest {

    @Test
    fun background_appendsTheAlphaToTheStoredColour() {
        val tag = Tag(id = "1", name = "Work", color = "#6366f1")

        assertEquals("#6366f115", tag.background(TagShade.Faint))
        assertEquals("#6366f118", tag.background(TagShade.Inline))
        assertEquals("#6366f120", tag.background(TagShade.Pill))
        assertEquals("#6366f130", tag.background(TagShade.Selected))
    }

    @Test
    fun theFourShadesAreTheOnesTheOriginalUses() {
        // These are a visual contract with the original, not free parameters.
        assertEquals(listOf("15", "18", "20", "30"), TagShade.entries.map { it.hexAlpha })
    }

    @Test
    fun findByName_ignoresCaseAndSurroundingSpace() {
        // What stops the list filling up with Work, work and "work ".
        val tags = listOf(tag("1", "Work"), tag("2", "Home"))

        assertEquals("1", tags.findByName("work")?.id)
        assertEquals("1", tags.findByName("  WORK  ")?.id)
        assertEquals("2", tags.findByName("Home")?.id)
    }

    @Test
    fun findByName_findsNothingForANameNobodyHas() {
        val tags = listOf(tag("1", "Work"))

        assertNull(tags.findByName("Errands"))
        assertNull(tags.findByName(""))
        assertNull(tags.findByName("   "))
    }

    @Test
    fun findByName_matchesATagWhoseOwnNameHasSpaceAroundIt() {
        // A tag stored before the name was trimmed must still be found, or the user
        // gets a second one they cannot tell apart from the first.
        val tags = listOf(tag("1", "  Work  "))

        assertEquals("1", tags.findByName("work")?.id)
    }

    @Test
    fun theColourPaletteIsTheOriginalsInOrder() {
        assertEquals(
            listOf(
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
            ),
            TAG_COLORS,
        )
        assertEquals("#6366f1", DEFAULT_TAG_COLOR)
    }

    @Test
    fun everySwatchIsADistinctSixDigitHex() {
        TAG_COLORS.forEach { color ->
            kotlin.test.assertTrue(Regex("^#[0-9a-f]{6}$").matches(color), "swatch \"$color\"")
        }
        assertEquals(TAG_COLORS.size, TAG_COLORS.toSet().size, "swatches must be distinguishable")
    }

    @Test
    fun normalizeTagEmoji_keepsAtMostTwoUnits() {
        assertEquals("🙂", normalizeTagEmoji("🙂"))
        assertEquals("ab", normalizeTagEmoji("abcdef"))
    }

    @Test
    fun normalizeTagEmoji_treatsNothingAsAbsent() {
        listOf(null, "", "   ").forEach { typed ->
            assertNull(normalizeTagEmoji(typed), "typed ${typed?.let { "\"$it\"" }}")
        }
    }

    private companion object {
        fun tag(id: String, name: String) = Tag(id = id, name = name, color = DEFAULT_TAG_COLOR)
    }
}
