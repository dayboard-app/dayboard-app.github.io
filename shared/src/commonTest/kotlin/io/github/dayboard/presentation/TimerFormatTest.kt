package io.github.dayboard.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class TimerFormatTest {

    @Test
    fun formatCountdown_padsBothHalves() {
        assertEquals("00:00", formatCountdown(0))
        assertEquals("00:09", formatCountdown(9))
        assertEquals("01:00", formatCountdown(60))
        assertEquals("25:00", formatCountdown(25 * 60))
        assertEquals("04:59", formatCountdown(299))
    }

    @Test
    fun formatCountdown_letsMinutesRunPastAnHour() {
        // The longest stretch the sliders allow. `90:00` reads as a timer;
        // `01:30:00` reads as a duration, and only after counting the colons.
        assertEquals("90:00", formatCountdown(90 * 60))
        assertEquals("60:01", formatCountdown(3601))
    }

    @Test
    fun formatCountdown_readsAnImpossibleNegativeAsZero() {
        // The timer floors at zero, but a stored document need not have.
        assertEquals("00:00", formatCountdown(-1))
        assertEquals("00:00", formatCountdown(-600))
    }
}
