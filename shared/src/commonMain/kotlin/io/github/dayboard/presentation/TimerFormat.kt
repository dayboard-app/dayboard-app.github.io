package io.github.dayboard.presentation

/**
 * A countdown as the ring shows it: `MM:SS`, both padded to two digits.
 *
 * Minutes are not wrapped into hours. The longest stretch the settings allow is 90
 * minutes, and `90:00` is what the original shows for it - clearer on a timer than
 * `01:30:00`, which reads as an hour and a half only after you have counted the
 * colons.
 *
 * A negative value cannot arrive from the timer, which floors at zero, but it can
 * arrive from a stored document, and `-1:-1` would be a strange way to find out.
 */
fun formatCountdown(secondsLeft: Int): String {
    val total = secondsLeft.coerceAtLeast(0)

    return "${twoDigits(total / SECONDS_PER_MINUTE)}:${twoDigits(total % SECONDS_PER_MINUTE)}"
}

private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

private const val SECONDS_PER_MINUTE = 60
