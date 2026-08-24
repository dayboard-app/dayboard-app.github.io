package io.github.dayboard.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsRangesTest {

    @Test
    fun theBoundsAreTheOnesTheOriginalOffers() {
        assertEquals(1 to 90, SettingRange.FocusDuration.let { it.min to it.max })
        assertEquals(1 to 30, SettingRange.ShortBreakDuration.let { it.min to it.max })
        assertEquals(1 to 60, SettingRange.LongBreakDuration.let { it.min to it.max })
        assertEquals(2 to 8, SettingRange.LongBreakInterval.let { it.min to it.max })
        assertEquals(0 to 100, SettingRange.SoundVolume.let { it.min to it.max })
    }

    @Test
    fun noDurationCanBeZero() {
        // A stretch of zero seconds is a timer with nothing to count, which would
        // finish the instant it started and take the cycle with it.
        listOf(
            SettingRange.FocusDuration,
            SettingRange.ShortBreakDuration,
            SettingRange.LongBreakDuration,
        ).forEach { range ->
            assertTrue(range.min >= 1, "${range.name} must not allow zero")
        }
    }

    @Test
    fun theLongBreakIntervalStartsAtTwo() {
        // At one, every focus session would end in a long break and the short one
        // would never be reached at all.
        assertEquals(2, SettingRange.LongBreakInterval.min)
    }

    @Test
    fun clamp_bringsAStoredValueBackInside() {
        // A document written by hand, or by a newer version with wider bounds.
        assertEquals(90, SettingRange.FocusDuration.clamp(500))
        assertEquals(1, SettingRange.FocusDuration.clamp(0))
        assertEquals(1, SettingRange.FocusDuration.clamp(-5))
        assertEquals(25, SettingRange.FocusDuration.clamp(25))
    }

    @Test
    fun label_readsAsTheValueAndItsUnit() {
        assertEquals("25 min", SettingRange.FocusDuration.label(25))
        assertEquals("4 sessions", SettingRange.LongBreakInterval.label(4))
        assertEquals("70 %", SettingRange.SoundVolume.label(70))
    }

    @Test
    fun label_showsTheClampedValueRatherThanTheStoredOne() {
        // So the number beside a slider always matches where the slider actually is.
        assertEquals("90 min", SettingRange.FocusDuration.label(500))
    }

    @Test
    fun everyDefaultSettingSitsInsideItsOwnRange() {
        // A default outside its range would make the panel move the slider the first
        // time it was opened, changing a setting nobody touched.
        val defaults = Settings.Default

        assertEquals(
            defaults.focusDuration,
            SettingRange.FocusDuration.clamp(defaults.focusDuration),
        )
        assertEquals(
            defaults.shortBreakDuration,
            SettingRange.ShortBreakDuration.clamp(defaults.shortBreakDuration),
        )
        assertEquals(
            defaults.longBreakDuration,
            SettingRange.LongBreakDuration.clamp(defaults.longBreakDuration),
        )
        assertEquals(
            defaults.longBreakInterval,
            SettingRange.LongBreakInterval.clamp(defaults.longBreakInterval),
        )
        assertEquals(defaults.soundVolume, SettingRange.SoundVolume.clamp(defaults.soundVolume))
    }
}
