package io.github.dayboard.presentation

/**
 * A moment, in the fields a browser clock reports them.
 *
 * The conventions are JS `Date`'s on purpose - [dayOfWeek] is 0 for Sunday and
 * [month] is 0 for January - because this is built from one. Choosing friendlier
 * numbering here would not remove the off-by-one, it would move it into the
 * conversion, where it would be written once and read never.
 */
data class ClockTime(
    val hour: Int,
    val minute: Int,
    val second: Int,
    val dayOfWeek: Int,
    val month: Int,
    val dayOfMonth: Int,
)

/**
 * The time, always 24-hour and zero-padded.
 *
 * There is no 12-hour setting in the original and none here, so there is no AM/PM
 * to get wrong and no midnight-is-12 special case.
 */
fun ClockTime.formatTime(): String = "${twoDigits(hour)}:${twoDigits(minute)}"

/**
 * The seconds, separator included, for the smaller span they are drawn in.
 *
 * The colon belongs to the seconds rather than to the time before it, because it
 * only appears when the seconds do.
 */
fun ClockTime.formatSecondsSuffix(): String = ":${twoDigits(second)}"

/**
 * The date in the original's long form: "Monday, August 24". No year.
 *
 * The names are written out rather than read from the platform's locale data. The
 * original asks for "en-US" whatever the browser's language is, and a shared module
 * with no locale support cannot ask for a locale at all - so spelling them out is
 * both the faithful answer and the only one available.
 */
fun ClockTime.formatDate(): String =
    "${WEEKDAY_NAMES[dayOfWeek.mod(WEEKDAY_NAMES.size)]}, " +
        "${MONTH_NAMES[month.mod(MONTH_NAMES.size)]} $dayOfMonth"

private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

/**
 * Indexed by `Date.getDay()`, so Sunday first.
 *
 * Both lists are read with `mod`, which keeps the lookup total. The values come
 * from a browser clock and are always in range; wrapping is only so that an
 * impossible one renders something odd instead of throwing in the middle of a frame.
 */
private val WEEKDAY_NAMES = listOf(
    "Sunday",
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
)

/** Indexed by `Date.getMonth()`, so January is 0. */
private val MONTH_NAMES = listOf(
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
