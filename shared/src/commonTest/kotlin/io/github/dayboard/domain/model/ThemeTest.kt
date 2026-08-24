package io.github.dayboard.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeTest {

    @Test
    fun themeId_fromId_acceptsEveryStoredId() {
        ThemeId.entries.forEach { theme ->
            assertEquals(theme, ThemeId.fromId(theme.id), "id \"${theme.id}\"")
        }
    }

    @Test
    fun themeId_fromId_fallsBackToCoralForAnythingUnrecognised() {
        // A first visit, a cleared value, a retired theme, and the wrong case.
        listOf(null, "", " ", "purple", "Coral", "CORAL").forEach { stored ->
            assertEquals(ThemeId.Coral, ThemeId.fromId(stored), "stored \"$stored\"")
        }
    }

    @Test
    fun colorMode_fromId_acceptsEveryStoredId() {
        ColorMode.entries.forEach { mode ->
            assertEquals(mode, ColorMode.fromId(mode.id), "id \"${mode.id}\"")
        }
    }

    @Test
    fun colorMode_fromId_fallsBackToSystemForAnythingUnrecognised() {
        listOf(null, "", "auto", "Dark").forEach { stored ->
            assertEquals(ColorMode.System, ColorMode.fromId(stored), "stored \"$stored\"")
        }
    }

    @Test
    fun resolvesToDark_followsTheDeviceOnlyUnderSystem() {
        // An explicit choice wins whatever the device reports; System defers to it.
        assertFalse(ColorMode.Light.resolvesToDark(systemPrefersDark = true))
        assertFalse(ColorMode.Light.resolvesToDark(systemPrefersDark = false))
        assertTrue(ColorMode.Dark.resolvesToDark(systemPrefersDark = true))
        assertTrue(ColorMode.Dark.resolvesToDark(systemPrefersDark = false))
        assertTrue(ColorMode.System.resolvesToDark(systemPrefersDark = true))
        assertFalse(ColorMode.System.resolvesToDark(systemPrefersDark = false))
    }

    @Test
    fun themeId_defaultsAreTheOnesTokensCssFallsBackTo() {
        // `tokens.css` declares coral on bare `:root`, so an unknown theme still
        // paints coral. These two constants encode that contract.
        assertEquals(ThemeId.Coral, ThemeId.Default)
        assertEquals(ColorMode.System, ColorMode.Default)
    }

    @Test
    fun themeId_swatchesAreDistinctSixDigitHex() {
        val hexes = ThemeId.entries.map { it.accentHex }
        hexes.forEach { hex ->
            assertTrue(Regex("^#[0-9a-f]{6}$").matches(hex), "swatch \"$hex\"")
        }
        assertEquals(hexes.size, hexes.toSet().size, "swatches must be distinguishable")
    }

    @Test
    fun ids_areStableAndUnique() {
        // These strings are a storage format: a duplicate or a rename orphans
        // every preference already saved under the old value.
        assertEquals(
            listOf("coral", "ocean", "forest", "lavender", "ember", "slate"),
            ThemeId.entries.map { it.id },
        )
        assertEquals(listOf("light", "dark", "system"), ColorMode.entries.map { it.id })
    }
}
