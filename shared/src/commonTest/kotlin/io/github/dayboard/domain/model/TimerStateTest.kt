package io.github.dayboard.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimerStateTest {

    // ---------------------------------------------------------------- durations

    @Test
    fun durationSeconds_readsEachModeFromItsOwnSetting() {
        val settings = Settings(focusDuration = 25, shortBreakDuration = 5, longBreakDuration = 15)

        assertEquals(25 * 60, settings.durationSeconds(TimerMode.Focus))
        assertEquals(5 * 60, settings.durationSeconds(TimerMode.ShortBreak))
        assertEquals(15 * 60, settings.durationSeconds(TimerMode.LongBreak))
    }

    @Test
    fun freshTimer_isAFullFocusStretchThatIsNotRunning() {
        val timer = freshTimer(DEFAULTS)

        assertEquals(TimerMode.Focus, timer.mode)
        assertEquals(25 * 60, timer.secondsLeft)
        assertFalse(timer.running)
        assertEquals(0, timer.completedSessions)
    }

    // --------------------------------------------------------------- completion

    @Test
    fun finishingFocus_countsTheSessionAndStartsAShortBreak() {
        val completion = running(TimerMode.Focus, secondsLeft = 0).completed(DEFAULTS)

        assertEquals(TimerMode.Focus, completion.ended)
        assertEquals(TimerMode.ShortBreak, completion.next.mode)
        assertEquals(1, completion.next.completedSessions)
        assertEquals(5 * 60, completion.next.secondsLeft)
    }

    @Test
    fun finishingTheLastFocusOfACycle_startsALongBreakAndResetsTheCount() {
        // Three sessions already done, interval of four: this one closes the cycle.
        val state = running(TimerMode.Focus, secondsLeft = 0, completedSessions = 3)

        val completion = state.completed(DEFAULTS)

        assertEquals(TimerMode.LongBreak, completion.next.mode)
        assertEquals(0, completion.next.completedSessions, "the cycle starts over")
        assertEquals(15 * 60, completion.next.secondsLeft)
    }

    @Test
    fun finishingABreak_returnsToFocusAndLeavesTheCountAlone() {
        listOf(TimerMode.ShortBreak, TimerMode.LongBreak).forEach { mode ->
            val ending = running(mode, secondsLeft = 0, completedSessions = 2)
            val completion = ending.completed(DEFAULTS)

            assertEquals(mode, completion.ended, "ended mode for $mode")
            assertEquals(TimerMode.Focus, completion.next.mode, "next mode after $mode")
            assertEquals(2, completion.next.completedSessions, "count after $mode")
            assertEquals(25 * 60, completion.next.secondsLeft, "duration after $mode")
        }
    }

    @Test
    fun autoStartBreaks_decidesWhetherTheBreakBeginsOnItsOwn() {
        val ended = running(TimerMode.Focus, secondsLeft = 0)

        assertFalse(ended.completed(DEFAULTS).next.running)
        assertTrue(ended.completed(DEFAULTS.copy(autoStartBreaks = true)).next.running)
    }

    @Test
    fun autoStartFocus_isASeparateSwitchFromAutoStartBreaks() {
        // Turning breaks on must not start focus, and the other way round. Sharing
        // one switch would take away the useful half of the setting: breaks that
        // begin by themselves, work that waits for you.
        val breakEnded = running(TimerMode.ShortBreak, secondsLeft = 0)

        assertFalse(breakEnded.completed(DEFAULTS.copy(autoStartBreaks = true)).next.running)
        assertTrue(breakEnded.completed(DEFAULTS.copy(autoStartFocus = true)).next.running)

        val focusEnded = running(TimerMode.Focus, secondsLeft = 0)
        assertFalse(focusEnded.completed(DEFAULTS.copy(autoStartFocus = true)).next.running)
    }

    @Test
    fun skippingAFocusSessionStillCountsIt() {
        // The behaviour most likely to be "fixed" by mistake. Skip and running out
        // are the same call, so a skipped session counts, chimes and advances the
        // cycle exactly like a finished one.
        val skipped = running(TimerMode.Focus, secondsLeft = 20 * 60).completed(DEFAULTS)

        assertEquals(1, skipped.next.completedSessions)
        assertEquals(TimerMode.Focus, skipped.ended, "the chime still names the focus session")
        assertEquals(TimerMode.ShortBreak, skipped.next.mode)
    }

    @Test
    fun aLongBreakIntervalOfOne_sendsEveryFocusSessionToALongBreak() {
        val completion = running(TimerMode.Focus, secondsLeft = 0)
            .completed(DEFAULTS.copy(longBreakInterval = 1))

        assertEquals(TimerMode.LongBreak, completion.next.mode)
        assertEquals(0, completion.next.completedSessions)
    }

    @Test
    fun aCountAlreadyPastTheInterval_stillTriggersTheLongBreak() {
        // Reachable by shortening the interval mid-cycle: the count does not shrink
        // with it, so the test is `>=` rather than `==`. With `==` the cycle would
        // never close and the user would never get a long break again.
        val completion = running(TimerMode.Focus, secondsLeft = 0, completedSessions = 9)
            .completed(DEFAULTS.copy(longBreakInterval = 4))

        assertEquals(TimerMode.LongBreak, completion.next.mode)
        assertEquals(0, completion.next.completedSessions)
    }

    @Test
    fun aWholeCycleRunsFocusBreakFocusBreakThenLongBreak() {
        // The machine end to end, rather than one transition at a time.
        var state = freshTimer(DEFAULTS)
        val visited = mutableListOf<TimerMode>()

        repeat(8) {
            visited += state.mode
            state = state.completed(DEFAULTS).next
        }

        assertEquals(
            listOf(
                TimerMode.Focus,
                TimerMode.ShortBreak,
                TimerMode.Focus,
                TimerMode.ShortBreak,
                TimerMode.Focus,
                TimerMode.ShortBreak,
                TimerMode.Focus,
                TimerMode.LongBreak,
            ),
            visited,
        )
    }

    // ------------------------------------------------------ manual transitions

    @Test
    fun switchingTabs_refillsThatStretchStoppedAndKeepsTheCount() {
        val state = running(TimerMode.Focus, secondsLeft = 30, completedSessions = 2)

        val switched = state.switchedTo(TimerMode.LongBreak, DEFAULTS)

        assertEquals(TimerMode.LongBreak, switched.mode)
        assertEquals(15 * 60, switched.secondsLeft)
        assertFalse(switched.running)
        assertEquals(2, switched.completedSessions)
    }

    @Test
    fun switchingToTheModeAlreadyShowing_actsAsAReset() {
        val state = running(TimerMode.Focus, secondsLeft = 30)

        val switched = state.switchedTo(TimerMode.Focus, DEFAULTS)

        assertEquals(25 * 60, switched.secondsLeft)
        assertFalse(switched.running)
    }

    @Test
    fun toggleRunning_changesNothingElse() {
        val stopped = TimerState(TimerMode.ShortBreak, secondsLeft = 42, completedSessions = 3)

        val started = stopped.toggledRunning()

        assertTrue(started.running)
        assertEquals(stopped.copy(running = true), started)
        assertEquals(stopped, started.toggledRunning())
    }

    @Test
    fun reset_refillsTheCurrentStretchAndKeepsTheCount() {
        val state = running(TimerMode.ShortBreak, secondsLeft = 9, completedSessions = 2)

        val reset = state.reset(DEFAULTS)

        assertEquals(TimerMode.ShortBreak, reset.mode)
        assertEquals(5 * 60, reset.secondsLeft)
        assertFalse(reset.running)
        assertEquals(2, reset.completedSessions, "a reset stretch is not an abandoned cycle")
    }

    @Test
    fun changingADuration_stopsAndRefillsTheCurrentStretch() {
        val state = running(TimerMode.Focus, secondsLeft = 100, completedSessions = 1)

        val adjusted = state.withDurationsFrom(DEFAULTS.copy(focusDuration = 50))

        assertEquals(50 * 60, adjusted.secondsLeft)
        assertFalse(adjusted.running)
        assertEquals(1, adjusted.completedSessions)
    }

    // ---------------------------------------------------------------- progress

    @Test
    fun progress_fillsAsTimeIsSpent() {
        val full = TimerState(TimerMode.Focus, secondsLeft = 25 * 60)
        val half = TimerState(TimerMode.Focus, secondsLeft = 25 * 60 / 2)
        val done = TimerState(TimerMode.Focus, secondsLeft = 0)

        assertEquals(0.0, full.progressPercent(DEFAULTS))
        assertEquals(50.0, half.progressPercent(DEFAULTS))
        assertEquals(100.0, done.progressPercent(DEFAULTS))
    }

    @Test
    fun progress_staysWithinItsRangeForStatesThatShouldNotExist() {
        // A stored document can hold more time left than the stretch is long, and a
        // duration of zero. Neither should send the ring round twice or backwards.
        val overfull = TimerState(TimerMode.Focus, secondsLeft = 99 * 60)
        assertEquals(0.0, overfull.progressPercent(DEFAULTS))

        val negative = TimerState(TimerMode.Focus, secondsLeft = -30)
        assertEquals(100.0, negative.progressPercent(DEFAULTS))

        val noDuration = TimerState(TimerMode.Focus, secondsLeft = 10)
        assertEquals(100.0, noDuration.progressPercent(DEFAULTS.copy(focusDuration = 0)))
    }

    @Test
    fun finished_isTrueOnlyOnceTheTimeIsGone() {
        assertFalse(TimerState(secondsLeft = 1).finished)
        assertTrue(TimerState(secondsLeft = 0).finished)
        assertTrue(TimerState(secondsLeft = -1).finished)
    }

    // -------------------------------------------------------------------- dots

    @Test
    fun sessionDots_showOnePerSessionInTheCycle() {
        assertEquals(
            listOf(true, true, false, false),
            sessionDots(completedSessions = 2, longBreakInterval = 4),
        )
        assertEquals(listOf(false, false), sessionDots(0, longBreakInterval = 2))
        assertEquals(listOf(true, true), sessionDots(completedSessions = 2, longBreakInterval = 2))
    }

    @Test
    fun sessionDots_neverRenderMoreThanTheIntervalAllows() {
        // Shortening the interval mid-cycle leaves the count higher than the number
        // of dots. Every dot is filled; none is invented.
        assertEquals(listOf(true, true), sessionDots(completedSessions = 5, longBreakInterval = 2))
        assertEquals(emptyList(), sessionDots(completedSessions = 3, longBreakInterval = 0))
        assertEquals(emptyList(), sessionDots(completedSessions = 3, longBreakInterval = -1))
    }

    // ------------------------------------------------------------------ modes

    @Test
    fun timerMode_idsAreStableAndUnique() {
        // These strings are a storage format shared with every other device.
        assertEquals(listOf("focus", "shortBreak", "longBreak"), TimerMode.entries.map { it.id })
    }

    @Test
    fun timerMode_fromId_fallsBackToFocus() {
        TimerMode.entries.forEach { assertEquals(it, TimerMode.fromId(it.id)) }
        listOf(null, "", "break", "Focus").forEach {
            assertEquals(TimerMode.Focus, TimerMode.fromId(it), "stored ${quoted(it)}")
        }
    }

    @Test
    fun timerMode_namesAStretchDifferentlyDependingOnWhetherItIsAChoiceOrAnEvent() {
        assertEquals("Short Break", TimerMode.ShortBreak.label)
        assertEquals("Short break", TimerMode.ShortBreak.endedLabel)
        assertEquals("Focus", TimerMode.Focus.label)
        assertEquals("Focus session", TimerMode.Focus.endedLabel)
        assertEquals("Long Break", TimerMode.LongBreak.label)
        assertEquals("Long break", TimerMode.LongBreak.endedLabel)
    }

    @Test
    fun timerMode_isBreak_isTrueForBothBreaks() {
        assertFalse(TimerMode.Focus.isBreak)
        assertTrue(TimerMode.ShortBreak.isBreak)
        assertTrue(TimerMode.LongBreak.isBreak)
    }

    private companion object {
        val DEFAULTS = Settings.Default

        fun quoted(value: String?): String = value?.let { "\"$it\"" } ?: "null"

        fun running(
            mode: TimerMode,
            secondsLeft: Int,
            completedSessions: Int = 0,
        ) = TimerState(
            mode = mode,
            secondsLeft = secondsLeft,
            running = true,
            completedSessions = completedSessions,
        )
    }
}
