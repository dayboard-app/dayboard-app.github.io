package io.github.dayboard.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsTest {

    @Test
    fun defaults_matchTheOriginalsFirstRun() {
        // A difference here shows up as a different first run rather than an error,
        // so every default is pinned rather than spot-checked.
        with(Settings.Default) {
            assertEquals(25, focusDuration)
            assertEquals(5, shortBreakDuration)
            assertEquals(15, longBreakDuration)
            assertEquals(4, longBreakInterval)
            assertFalse(autoStartBreaks)
            assertFalse(autoStartFocus)
            assertTrue(soundEnabled)
            assertEquals(70, soundVolume)
            assertEquals(ThemeId.Coral, themeId)
            assertEquals(ColorMode.System, colorMode)
            assertEquals(DisplayMode.Pomodoro, displayMode)
            assertFalse(showSeconds)
            assertNull(weatherCity)
            assertTrue(showWeather)
            assertTrue(showPomodoro)
            assertTrue(showTasks)
            assertTrue(showNotes)
            assertEquals(CardLayout.Default, cardLayout)
        }
    }

    @Test
    fun theClockIsAlwaysOnTheBoard() {
        // It has no toggle in the settings panel, so nothing can hide it.
        val everythingOff = Settings(showPomodoro = false, showTasks = false, showNotes = false)
        assertTrue(everythingOff.isVisible(CardId.Clock))
    }

    @Test
    fun eachSwitchableCardFollowsItsOwnToggle() {
        val settings = Settings(showPomodoro = false, showTasks = true, showNotes = false)
        assertFalse(settings.isVisible(CardId.Timer))
        assertTrue(settings.isVisible(CardId.Tasks))
        assertFalse(settings.isVisible(CardId.Notes))
    }

    @Test
    fun visibilityByStoredId_agreesWithVisibilityByCard() {
        val settings = Settings(showPomodoro = false, showTasks = true, showNotes = false)
        CardId.entries.forEach { card ->
            assertEquals(settings.isVisible(card), settings.isVisible(card.id), card.id)
        }
    }

    @Test
    fun anIdThatNamesNoCardIsNotOnTheBoard() {
        // A layout saved by a newer version could name a card this one lacks; it
        // must be ignored rather than rendered as a blank slot.
        assertFalse(Settings.Default.isVisible("sidebar"))
        assertFalse(Settings.Default.isVisible(""))
    }

    @Test
    fun displayMode_fallsBackToPomodoro() {
        assertEquals(DisplayMode.Clock, DisplayMode.fromId("clock"))
        assertEquals(DisplayMode.Pomodoro, DisplayMode.fromId("pomodoro"))
        assertEquals(DisplayMode.Pomodoro, DisplayMode.fromId(null))
        assertEquals(DisplayMode.Pomodoro, DisplayMode.fromId("kanban"))
    }

    @Test
    fun storedIdsAreStable() {
        // These strings are a storage format; renaming one orphans saved settings.
        assertEquals(listOf("pomodoro", "clock"), DisplayMode.entries.map { it.id })
        assertEquals(listOf("clock", "timer", "tasks", "notes"), CardId.entries.map { it.id })
    }
}
