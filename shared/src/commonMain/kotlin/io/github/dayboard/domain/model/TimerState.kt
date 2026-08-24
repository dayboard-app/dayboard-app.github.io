package io.github.dayboard.domain.model

/**
 * Everything about the timer that can change, as one value.
 *
 * There is no clock in here and no way to reach one. Every transition below is a
 * pure function of this state and the settings, which is what makes the whole
 * machine testable without waiting for real seconds to pass.
 */
data class TimerState(
    val mode: TimerMode = TimerMode.Default,
    val secondsLeft: Int = 0,
    val running: Boolean = false,
    val completedSessions: Int = 0,
) {

    /** True once the stretch has run out. */
    val finished: Boolean get() = secondsLeft <= 0
}

/** How long a stretch of [mode] lasts, in seconds. Settings store minutes. */
fun Settings.durationSeconds(mode: TimerMode): Int = when (mode) {
    TimerMode.Focus -> focusDuration
    TimerMode.ShortBreak -> shortBreakDuration
    TimerMode.LongBreak -> longBreakDuration
} * SECONDS_PER_MINUTE

/** A timer that has not run yet: a full focus stretch, stopped. */
fun freshTimer(settings: Settings): TimerState = TimerState(
    mode = TimerMode.Focus,
    secondsLeft = settings.durationSeconds(TimerMode.Focus),
    running = false,
    completedSessions = 0,
)

/**
 * What the timer becomes when the current stretch ends, and which stretch ended.
 *
 * One type for both because the caller needs both: the next state to show, and the
 * stretch that just finished so it can be announced. Reading the ended mode off the
 * previous state instead would work until something changed the order of operations.
 */
data class TimerCompletion(
    val next: TimerState,
    val ended: TimerMode,
)

/**
 * Ends the current stretch and moves to the next one.
 *
 * This is the whole cycle rule, and it is the same function for a timer that ran
 * out and for the Skip button - which is why **skipping a focus session still
 * counts it**. That looks like a bug and is not: the original does exactly this,
 * and it is defensible, since a session you chose to cut short is still a session
 * you sat through most of.
 *
 * A long break arrives when the finished session is the [Settings.longBreakInterval]th,
 * and the counter goes back to zero with it, so the dots restart with the cycle.
 */
fun TimerState.completed(settings: Settings): TimerCompletion {
    val nextState = if (mode == TimerMode.Focus) {
        val sessions = completedSessions + 1
        val longBreakDue = sessions >= settings.longBreakInterval

        started(
            mode = if (longBreakDue) TimerMode.LongBreak else TimerMode.ShortBreak,
            // Reset with the long break rather than after it: the count means
            // "sessions into this cycle", and the long break is the cycle ending.
            completedSessions = if (longBreakDue) 0 else sessions,
            running = settings.autoStartBreaks,
            settings = settings,
        )
    } else {
        started(
            mode = TimerMode.Focus,
            completedSessions = completedSessions,
            running = settings.autoStartFocus,
            settings = settings,
        )
    }

    return TimerCompletion(next = nextState, ended = mode)
}

/**
 * Switches to a stretch the user picked from the tabs.
 *
 * Always stopped and always full, even when switching to the mode already showing:
 * the tabs double as a reset to that stretch. Time remaining is discarded without
 * asking, matching the original - the tabs are small, deliberate targets, and a
 * confirmation on every one of them would cost more than the occasional mistake.
 */
fun TimerState.switchedTo(mode: TimerMode, settings: Settings): TimerState = started(
    mode = mode,
    completedSessions = completedSessions,
    running = false,
    settings = settings,
)

/** Starts a stopped timer, or stops a running one. Nothing else changes. */
fun TimerState.toggledRunning(): TimerState = copy(running = !running)

/**
 * Puts the current stretch back to full and stops it.
 *
 * The session count survives, because resetting a stretch is not abandoning the
 * cycle it belongs to.
 */
fun TimerState.reset(settings: Settings): TimerState = started(
    mode = mode,
    completedSessions = completedSessions,
    running = false,
    settings = settings,
)

/**
 * Applies a change to the configured durations.
 *
 * Changing how long a stretch lasts stops and refills the current one. Letting a
 * running timer keep a countdown measured against a length that no longer exists
 * would leave it showing a number with no meaning.
 */
fun TimerState.withDurationsFrom(settings: Settings): TimerState = reset(settings)

/**
 * How far through the current stretch the timer is, from 0 to 100.
 *
 * Fills as time is spent rather than draining, so a full ring is a finished stretch.
 * Guarded against a zero total, which cannot come from the settings sliders but can
 * come from a stored document written by something else.
 */
fun TimerState.progressPercent(settings: Settings): Double {
    val total = settings.durationSeconds(mode)
    if (total <= 0) return PERCENT

    return ((total - secondsLeft).toDouble() / total * PERCENT).coerceIn(0.0, PERCENT)
}

/**
 * The session dots, one per session in a cycle, filled for the ones already done.
 *
 * The list is as long as the configured interval, so shortening the interval shows
 * fewer dots immediately - including, briefly, fewer dots than there are completed
 * sessions, which simply renders as all of them filled.
 */
fun sessionDots(completedSessions: Int, longBreakInterval: Int): List<Boolean> =
    List(longBreakInterval.coerceAtLeast(0)) { index -> index < completedSessions }

/** Builds a full, fresh stretch. The one place a duration is looked up. */
private fun started(
    mode: TimerMode,
    completedSessions: Int,
    running: Boolean,
    settings: Settings,
): TimerState = TimerState(
    mode = mode,
    secondsLeft = settings.durationSeconds(mode),
    running = running,
    completedSessions = completedSessions,
)

private const val SECONDS_PER_MINUTE = 60
private const val PERCENT = 100.0
