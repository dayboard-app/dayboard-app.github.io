package io.github.dayboard.domain.model

import io.github.bchmsl.keel.color.SwatchShade
import io.github.bchmsl.keel.color.Swatches
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TagTest {

    @Test
    fun background_delegatesToKeelsSwatchBackground() {
        // The shades themselves are keel's own contract, tested in `keel`; this only
        // proves the delegate wires a tag's stored colour through to it.
        val tag = Tag(id = "1", name = "Work", color = "#6366f1")

        assertEquals("#6366f120", tag.background(SwatchShade.Pill))
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
        fun tag(id: String, name: String) = Tag(id = id, name = name, color = Swatches.Default)
    }
}
