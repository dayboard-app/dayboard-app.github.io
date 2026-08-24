package io.github.dayboard.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoredTimerTest {

    // --------------------------------------------------------- elapsed seconds

    @Test
    fun elapsedSeconds_countsWholeSecondsOnly() {
        assertEquals(0, elapsedSeconds(0L, 999L))
        assertEquals(1, elapsedSeconds(0L, 1000L))
        assertEquals(1, elapsedSeconds(0L, 1999L))
        assertEquals(90, elapsedSeconds(1_000_000L, 1_090_400L))
    }

    @Test
    fun elapsedSeconds_readsAClockThatWentBackwardsAsNoTimeAtAll() {
        // A corrected device clock, or a stored instant from a device running fast.
        // Without the floor this would hand time *back* to a countdown.
        assertEquals(0, elapsedSeconds(fromMillis = 5_000L, toMillis = 1_000L))
    }

    @Test
    fun elapsedSeconds_survivesAnAbsurdStoredInstant() {
        // A document carrying a nonsense timestamp must not overflow the conversion.
        assertEquals(Int.MAX_VALUE, elapsedSeconds(Long.MIN_VALUE / 2, Long.MAX_VALUE / 2))
    }

    // ------------------------------------------------------------ live anchor

    @Test
    fun secondsLeftAt_measuresAgainstTheClockRatherThanCounting() {
        // Ten seconds left, thirty seconds ago: the answer is zero, not ten, and it
        // does not matter how many ticks were missed in between.
        assertEquals(10, secondsLeftAt(10, anchorAtMillis = 0L, nowMillis = 0L))
        assertEquals(7, secondsLeftAt(10, anchorAtMillis = 0L, nowMillis = 3_000L))
        assertEquals(0, secondsLeftAt(10, anchorAtMillis = 0L, nowMillis = 30_000L))
    }

    @Test
    fun secondsLeftAt_neverGoesBelowZero() {
        assertEquals(0, secondsLeftAt(1, anchorAtMillis = 0L, nowMillis = 9_999_999L))
    }

    // ---------------------------------------------------------------- restore

    @Test
    fun restore_withNothingStored_isAFreshFocusStretch() {
        assertEquals(freshTimer(DEFAULTS), restoreTimer(null, DEFAULTS, NOW))
    }

    @Test
    fun restore_ofAStoppedTimer_leavesTheCountdownExactlyWhereItWas() {
        // A stopped timer is not measured against the wall clock, so an hour away
        // costs it nothing.
        val stored = stored(
            secondsLeft = 600,
            running = false,
            completedSessions = 2,
            lastTickAtMillis = NOW - 3_600_000,
        )

        val restored = restoreTimer(stored, DEFAULTS, NOW)

        assertEquals(600, restored.secondsLeft)
        assertFalse(restored.running)
        assertEquals(2, restored.completedSessions)
    }

    @Test
    fun restore_ofARunningTimer_subtractsTheTimeSpentAway() {
        val stored = stored(secondsLeft = 600, running = true, lastTickAtMillis = NOW - 60_000)

        val restored = restoreTimer(stored, DEFAULTS, NOW)

        assertEquals(540, restored.secondsLeft, "a minute away is a minute off the countdown")
        assertTrue(restored.running)
    }

    @Test
    fun restore_ofATimerThatRanOutWhileAway_stopsAtZeroInTheSameMode() {
        // The case worth being careful about. The stretch is over, but nothing is
        // announced: no chime, no session counted, no advance to the next mode.
        // Announcing a break that ended while the laptop was shut would be news
        // hours old.
        val stored = stored(
            mode = TimerMode.ShortBreak,
            secondsLeft = 30,
            running = true,
            completedSessions = 2,
            lastTickAtMillis = NOW - 600_000,
        )

        val restored = restoreTimer(stored, DEFAULTS, NOW)

        assertEquals(0, restored.secondsLeft)
        assertFalse(restored.running, "it must not tick on past zero")
        assertEquals(TimerMode.ShortBreak, restored.mode, "the mode has not advanced")
        assertEquals(2, restored.completedSessions, "no session was counted")
    }

    @Test
    fun restore_ofARunningTimerWithNoInstant_doesNotGuess() {
        // `running` with no `lastTickAt` should not happen, but a document written
        // by something else can say it. Subtracting from an unknown start would be
        // inventing a number; the stored one is at least true of some moment.
        val stored = stored(secondsLeft = 300, running = true, lastTickAtMillis = null)

        val restored = restoreTimer(stored, DEFAULTS, NOW)

        assertEquals(300, restored.secondsLeft)
        assertTrue(restored.running)
    }

    @Test
    fun restore_treatsNegativeStoredValuesAsZero() {
        val stored = stored(secondsLeft = -50, completedSessions = -3)

        val restored = restoreTimer(stored, DEFAULTS, NOW)

        assertEquals(0, restored.secondsLeft)
        assertEquals(0, restored.completedSessions)
        assertFalse(restored.running)
    }

    @Test
    fun restore_keepsTheStoredModeRatherThanTheDefault() {
        TimerMode.entries.forEach { mode ->
            val stored = stored(mode = mode, secondsLeft = 60)

            assertEquals(mode, restoreTimer(stored, DEFAULTS, NOW).mode, "mode $mode")
        }
    }

    // --------------------------------------------------------- remote updates

    @Test
    fun aRemoteUpdateThatOnlyDisagreesByDrift_isIgnored() {
        // Two devices counting the same stretch never match to the second. Applying
        // every update would make the number visibly jump.
        val local = TimerState(TimerMode.Focus, secondsLeft = 300, running = true)

        listOf(297, 299, 300, 301, 303).forEach { remoteSeconds ->
            val remote = local.copy(secondsLeft = remoteSeconds)

            assertEquals(local, local.applyingRemote(remote), "remote at $remoteSeconds")
        }
    }

    @Test
    fun aRemoteUpdateTooFarApartToBeDrift_isApplied() {
        val local = TimerState(TimerMode.Focus, secondsLeft = 300, running = true)

        listOf(296, 304, 100).forEach { remoteSeconds ->
            val remote = local.copy(secondsLeft = remoteSeconds)

            assertEquals(remote, local.applyingRemote(remote), "remote at $remoteSeconds")
        }
    }

    @Test
    fun aRemotePause_isAlwaysApplied() {
        // Pausing on a phone must pause on the laptop, however close the two counts.
        val local = TimerState(TimerMode.Focus, secondsLeft = 300, running = true)
        val paused = local.copy(running = false)

        assertEquals(paused, local.applyingRemote(paused))
    }

    @Test
    fun aRemoteModeChange_isAlwaysApplied() {
        val local = TimerState(TimerMode.Focus, secondsLeft = 300, running = true)
        val onABreak = TimerState(
            TimerMode.ShortBreak,
            secondsLeft = 300,
            running = true,
            completedSessions = 1,
        )

        assertEquals(onABreak, local.applyingRemote(onABreak))
    }

    @Test
    fun aRemoteUpdateArrivingWhileStopped_isAlwaysApplied() {
        // Nothing is drifting while this device is paused, so there is no jump to
        // protect against and every update is real news.
        val local = TimerState(TimerMode.Focus, secondsLeft = 300, running = false)
        val remote = local.copy(secondsLeft = 301, running = true)

        assertEquals(remote, local.applyingRemote(remote))
    }

    @Test
    fun aSessionCountThatChangedRemotely_isTakenWithTheRestOfTheUpdate() {
        // The count only ever moves alongside a mode change, which is applied whole.
        val local = TimerState(TimerMode.Focus, secondsLeft = 300, running = true)
        val remote = TimerState(
            TimerMode.ShortBreak,
            secondsLeft = 300,
            running = true,
            completedSessions = 1,
        )

        assertEquals(1, local.applyingRemote(remote).completedSessions)
    }

    private companion object {
        val DEFAULTS = Settings.Default
        const val NOW = 1_700_000_000_000L

        fun stored(
            mode: TimerMode = TimerMode.Focus,
            secondsLeft: Int,
            running: Boolean = false,
            completedSessions: Int = 0,
            lastTickAtMillis: Long? = null,
        ) = StoredTimer(
            mode = mode,
            secondsLeft = secondsLeft,
            running = running,
            completedSessions = completedSessions,
            lastTickAtMillis = lastTickAtMillis,
        )
    }
}
