package io.github.dayboard.domain.model

/** What a timer-end notification says. */
data class TimerNotification(val title: String, val body: String)

/**
 * The notification for a stretch that has just ended.
 *
 * The body is the useful half: it says what to do next rather than repeating the
 * title. After work it gives permission to stop, after a break it asks for
 * attention back - which is the whole point of a pomodoro telling you anything.
 */
fun timerEndNotification(ended: TimerMode): TimerNotification = TimerNotification(
    title = "${ended.endedLabel} complete!",
    body = if (ended.isBreak) BREAK_ENDED_BODY else FOCUS_ENDED_BODY,
)

private const val FOCUS_ENDED_BODY = "Great work! Time for a break."
private const val BREAK_ENDED_BODY = "Break's over — time to focus!"

/**
 * Whether the browser will show notifications, as it reports it.
 *
 * Three states rather than two, because "not yet asked" and "said no" call for
 * different screens: one offers a button, the other must not nag.
 */
enum class NotificationPermission {
    /** Not asked yet. Asking is allowed. */
    Default,

    Granted,

    /**
     * Refused. Asking again does nothing - a browser will not re-prompt - so the
     * app must not offer a button that would silently fail.
     */
    Denied,
    ;

    companion object {
        /**
         * Reads `Notification.permission`.
         *
         * Anything unrecognised, including the absence of the API, is read as
         * [Default]: the harmless direction, since the request itself will fail
         * cleanly if there is nothing to request from.
         */
        fun fromBrowserValue(value: String?): NotificationPermission = when (value) {
            "granted" -> Granted
            "denied" -> Denied
            else -> Default
        }
    }
}
