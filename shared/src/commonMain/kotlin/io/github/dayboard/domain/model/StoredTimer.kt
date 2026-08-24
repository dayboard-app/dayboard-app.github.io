package io.github.dayboard.domain.model

/**
 * The timer as it is written down, on this device or another.
 *
 * [lastTickAtMillis] is what makes a stored countdown mean anything later: without
 * it, a timer saved with four minutes left would still say four minutes an hour
 * afterwards. It is null whenever the timer was stopped, because a stopped timer
 * is not measured against the wall clock.
 */
data class StoredTimer(
    val mode: TimerMode,
    val secondsLeft: Int,
    val running: Boolean,
    val completedSessions: Int,
    val lastTickAtMillis: Long?,
)

/**
 * Whole seconds between two instants, never negative and never overflowing.
 *
 * Both guards are about clocks that are not to be trusted. A device whose clock is
 * corrected backwards, or one restored from a stored instant in the future, would
 * otherwise produce a negative elapsed time and *add* time to a countdown. A
 * document carrying an absurd instant would overflow the conversion to `Int`.
 */
fun elapsedSeconds(fromMillis: Long, toMillis: Long): Int =
    ((toMillis - fromMillis) / MILLIS_PER_SECOND)
        .coerceIn(0L, Int.MAX_VALUE.toLong())
        .toInt()

/**
 * What a running timer has left, measured against the clock rather than counted.
 *
 * This is the difference between a timer that survives a background tab and one
 * that does not. A browser throttles a hidden tab's timers to about one a minute
 * and eventually stops them altogether, so a countdown that subtracts one per tick
 * comes back from ten minutes away reading ten minutes late. Recomputing from a
 * fixed point makes a missed tick cost nothing at all.
 */
fun secondsLeftAt(anchorSecondsLeft: Int, anchorAtMillis: Long, nowMillis: Long): Int =
    (anchorSecondsLeft - elapsedSeconds(anchorAtMillis, nowMillis)).coerceAtLeast(0)

/**
 * Rebuilds the timer from what was stored, catching it up to now.
 *
 * With nothing stored the user gets a fresh focus stretch, which is the same state
 * a new account starts in.
 *
 * The case worth naming is a timer that ran out while nobody was watching. It
 * restores as zero, stopped, and **still in the mode it was in** - it does not fire
 * the completion. That is deliberate and matches the original: a completion plays a
 * sound, counts a session and advances the cycle, and doing all of that for a break
 * that ended while the laptop was shut would be announcing news that is hours old.
 * The user sees 00:00 and decides.
 */
fun restoreTimer(stored: StoredTimer?, settings: Settings, nowMillis: Long): TimerState {
    if (stored == null) return freshTimer(settings)

    val secondsLeft = if (stored.running && stored.lastTickAtMillis != null) {
        secondsLeftAt(stored.secondsLeft, stored.lastTickAtMillis, nowMillis)
    } else {
        stored.secondsLeft.coerceAtLeast(0)
    }

    return TimerState(
        mode = stored.mode,
        secondsLeft = secondsLeft,
        // A timer that ran out while away comes back stopped. Restoring it as
        // running would have it tick straight past zero into negative numbers.
        running = stored.running && secondsLeft > 0,
        completedSessions = stored.completedSessions.coerceAtLeast(0),
    )
}

/**
 * Takes an update that arrived from another device, unless it says nothing new.
 *
 * Two devices running the same timer never agree to the second: they started their
 * countdowns a moment apart and their clocks differ. Applying every update would
 * make the number visibly jump back and forth. So an update is ignored when both
 * sides are running the same stretch and are already within [AGREEMENT_SECONDS] of
 * each other - close enough that the difference is not worth a jump.
 *
 * Anything else is applied whole. A change of mode, a pause, or a gap too wide to
 * explain by drift all mean the other device did something, and the last thing the
 * user did anywhere is what the timer should show.
 */
fun TimerState.applyingRemote(remote: TimerState): TimerState =
    if (agreesWith(remote)) this else remote

/**
 * Whether two devices are close enough to be running the same stretch.
 *
 * The session count is deliberately not compared. It can only diverge alongside a
 * mode change, which this already catches, and testing it here would mean applying
 * a whole remote state - seconds included - to correct a row of dots, which is the
 * jump the rule exists to prevent.
 */
private fun TimerState.agreesWith(remote: TimerState): Boolean =
    running &&
        remote.running &&
        mode == remote.mode &&
        (secondsLeft - remote.secondsLeft).absoluteDifference() <= AGREEMENT_SECONDS

private fun Int.absoluteDifference(): Int = if (this < 0) -this else this

/**
 * How far two devices may disagree before the difference is treated as real.
 *
 * The original's three seconds. Wide enough to absorb the round trip and a slightly
 * wrong clock, narrow enough that a genuine change is never mistaken for drift.
 */
private const val AGREEMENT_SECONDS = 3

private const val MILLIS_PER_SECOND = 1000L
