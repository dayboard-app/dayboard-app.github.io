package io.github.dayboard.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class ClockTimeTest {

    @Test
    fun formatTime_padsBothHalvesToTwoDigits() {
        assertEquals("00:00", clockAt(hour = 0, minute = 0).formatTime())
        assertEquals("09:05", clockAt(hour = 9, minute = 5).formatTime())
        assertEquals("23:59", clockAt(hour = 23, minute = 59).formatTime())
    }

    @Test
    fun formatTime_staysOnTheTwentyFourHourClockPastNoon() {
        // The case a 12-hour formatter would render as "1:30" or "01:30 PM". There
        // is no setting for that here, so there is nothing to switch it back.
        assertEquals("13:30", clockAt(hour = 13, minute = 30).formatTime())
        assertEquals("12:00", clockAt(hour = 12, minute = 0).formatTime())
    }

    @Test
    fun formatSecondsSuffix_carriesItsOwnSeparator() {
        // The colon belongs to the seconds: it is drawn in the same muted span, and
        // it appears only when they do.
        assertEquals(":00", clockAt(second = 0).formatSecondsSuffix())
        assertEquals(":07", clockAt(second = 7).formatSecondsSuffix())
        assertEquals(":59", clockAt(second = 59).formatSecondsSuffix())
    }

    @Test
    fun formatDate_readsAsTheOriginalWritesIt() {
        // 2026-08-24 was a Monday.
        assertEquals(
            "Monday, August 24",
            clockAt(dayOfWeek = 1, month = 7, dayOfMonth = 24).formatDate(),
        )
    }

    @Test
    fun formatDate_leavesTheDayNumberUnpadded() {
        // `day: "numeric"`, not `"2-digit"` - "August 4", never "August 04".
        assertEquals(
            "Sunday, August 4",
            clockAt(dayOfWeek = 0, month = 7, dayOfMonth = 4).formatDate(),
        )
    }

    @Test
    fun formatDate_omitsTheYear() {
        val date = clockAt(dayOfWeek = 4, month = 0, dayOfMonth = 1).formatDate()

        assertEquals("Thursday, January 1", date)
    }

    @Test
    fun formatDate_namesEveryWeekdayInTheBrowsersOrder() {
        // `Date.getDay()` counts from Sunday. Getting this order wrong shifts every
        // date by a day, and every individual day still looks plausible.
        val expected = listOf(
            "Sunday",
            "Monday",
            "Tuesday",
            "Wednesday",
            "Thursday",
            "Friday",
            "Saturday",
        )

        expected.forEachIndexed { index, name ->
            assertEquals(
                "$name, January 1",
                clockAt(dayOfWeek = index, month = 0, dayOfMonth = 1).formatDate(),
                "getDay() == $index",
            )
        }
    }

    @Test
    fun formatDate_namesEveryMonthFromZero() {
        // `Date.getMonth()` counts from zero, which is the off-by-one this pins.
        val expected = listOf(
            "January",
            "February",
            "March",
            "April",
            "May",
            "June",
            "July",
            "August",
            "September",
            "October",
            "November",
            "December",
        )

        expected.forEachIndexed { index, name ->
            assertEquals(
                "Sunday, $name 1",
                clockAt(dayOfWeek = 0, month = index, dayOfMonth = 1).formatDate(),
                "getMonth() == $index",
            )
        }
    }

    @Test
    fun formatDate_wrapsRatherThanThrowsOnAnImpossibleField() {
        // Unreachable from a browser clock. It is pinned because the alternative to
        // wrapping is an index out of bounds thrown while the page is painting.
        assertEquals(
            "Sunday, January 1",
            clockAt(dayOfWeek = 7, month = 12, dayOfMonth = 1).formatDate(),
        )
        assertEquals(
            "Saturday, December 1",
            clockAt(dayOfWeek = -1, month = -1, dayOfMonth = 1).formatDate(),
        )
    }

    private fun clockAt(
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0,
        dayOfWeek: Int = 0,
        month: Int = 0,
        dayOfMonth: Int = 1,
    ) = ClockTime(
        hour = hour,
        minute = minute,
        second = second,
        dayOfWeek = dayOfWeek,
        month = month,
        dayOfMonth = dayOfMonth,
    )
}
