package io.github.dayboard.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dayboard.domain.model.Settings
import io.github.dayboard.domain.model.TimerMode
import io.github.dayboard.domain.model.TimerState
import io.github.dayboard.domain.model.applyingRemote
import io.github.dayboard.domain.model.completed
import io.github.dayboard.domain.model.durationSeconds
import io.github.dayboard.domain.model.reset
import io.github.dayboard.domain.model.restoreTimer
import io.github.dayboard.domain.model.secondsLeftAt
import io.github.dayboard.domain.model.switchedTo
import io.github.dayboard.domain.model.toggledRunning
import io.github.dayboard.domain.model.withDurationsFrom
import io.github.dayboard.domain.repository.Chime
import io.github.dayboard.domain.repository.TimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.js.Date

/**
 * The running timer: what it shows, what it does when it runs out, and what other
 * devices are told about it.
 *
 * Every rule about *what* the timer becomes lives in `:shared` and is tested. This
 * holds the current value, owns the three things a test cannot have - a clock, a
 * database and a speaker - and decides when to consult each.
 *
 * The countdown is measured, not counted. A running timer keeps a fixed point (how
 * much was left, and when), and every refresh works out the remainder from the
 * clock. A tab whose timers were throttled to one a minute therefore shows the
 * right number the instant anyone looks at it, rather than however far behind it
 * fell.
 */
class TimerController(
    private val repository: TimerRepository,
    private val chime: Chime,
    private val scope: CoroutineScope,
) {

    var state: TimerState by mutableStateOf(TimerState())
        private set

    /** True once the stored timer has arrived, or once we know there is none. */
    var loaded: Boolean by mutableStateOf(false)
        private set

    /**
     * Called with the stretch that just ended, whether it ran out or was skipped.
     *
     * A hook rather than a direct call so this class needs to know nothing about
     * notifications, which arrive in a later phase and may not be permitted at all.
     */
    var onCompleted: ((TimerMode) -> Unit)? = null

    private var settings: Settings = Settings.Default
    private var uid: String? = null
    private var stopListening: (() -> Unit)? = null
    private var tickJob: Job? = null
    private var saveJob: Job? = null

    // The fixed point a running countdown is measured from.
    private var anchorSecondsLeft: Int = 0
    private var anchorAtMillis: Long = 0

    init {
        // Not tied to a signed-in account: the page can be hidden across a sign-in,
        // and this only ever recomputes what is already on screen.
        onPageVisible { refreshFromClock() }
    }

    /**
     * Follows one account's timer under the given settings.
     *
     * Safe to call on every settings change, which is how it is used: only a change
     * that actually matters to the timer does anything. Attaching the listener is
     * deferred until settings are known, because a timer with nothing stored starts
     * at the configured focus duration, and starting it at the default and
     * correcting a moment later would be visible.
     */
    fun follow(uid: String, settings: Settings) {
        val previous = this.settings
        this.settings = settings

        if (this.uid != uid) {
            attach(uid)
            return
        }

        // Changing how long a stretch lasts stops and refills the current one. A
        // countdown measured against a length that no longer exists is a number
        // with no meaning.
        if (loaded && settings.durationsDifferFrom(previous)) {
            commit(state.withDurationsFrom(settings))
        }
    }

    /** Detaches, and forgets the previous account's timer. */
    fun stop() {
        stopListening?.invoke()
        stopListening = null
        tickJob?.cancel()
        tickJob = null
        saveJob?.cancel()
        saveJob = null
        uid = null
        state = TimerState()
        loaded = false
    }

    /** Starts a stopped timer, or pauses a running one. */
    fun toggleRunning() = commit(state.toggledRunning())

    /** Puts the current stretch back to full, stopped. The session count survives. */
    fun reset() = commit(state.reset(settings))

    /** Ends the current stretch early. It still counts, chimes and advances. */
    fun skip() = complete()

    /** Switches to a stretch the user picked, full and stopped. */
    fun switchTo(mode: TimerMode) = commit(state.switchedTo(mode, settings))

    private fun attach(uid: String) {
        stop()
        this.uid = uid

        stopListening = repository.observe(uid) { stored ->
            val fromStore = restoreTimer(stored, settings, nowMillis())

            // The first snapshot is what this device is starting from, so it is
            // taken whole. Later ones are another device's news, and are weighed
            // against what is on screen so a running countdown does not jump.
            state = if (loaded) state.applyingRemote(fromStore) else fromStore
            anchor()
            loaded = true
            startTicking()
        }
    }

    /**
     * Applies a state the user asked for, and writes it down.
     *
     * Saving happens here and nowhere else, which is what keeps writes to the few
     * moments that matter - starting, pausing, resetting, skipping, switching -
     * rather than one per second.
     */
    private fun commit(next: TimerState) {
        state = next
        anchor()
        startTicking()
        scheduleSave()
    }

    /**
     * Ends the current stretch: chimes, announces, and moves on.
     *
     * The same path for a timer that ran out and for the Skip button, which is why
     * a skipped focus session still counts. See `completed` in `:shared`.
     */
    private fun complete() {
        val completion = state.completed(settings)

        if (settings.soundEnabled) chime.play(settings.soundVolume)
        onCompleted?.invoke(completion.ended)

        commit(completion.next)
    }

    /**
     * Works out what is left from the clock, and ends the stretch if nothing is.
     *
     * Does nothing for a stopped timer: there is no elapsed time to account for,
     * and a paused countdown that quietly drained itself would be a bad surprise.
     */
    private fun refreshFromClock() {
        if (!loaded || !state.running) return

        val secondsLeft = secondsLeftAt(anchorSecondsLeft, anchorAtMillis, nowMillis())
        if (secondsLeft == state.secondsLeft) return

        state = state.copy(secondsLeft = secondsLeft)
        if (secondsLeft <= 0) complete()
    }

    private fun startTicking() {
        tickJob?.cancel()
        if (!state.running) {
            tickJob = null
            return
        }

        tickJob = scope.launch {
            while (isActive) {
                delay(millisUntilNextSecond())
                refreshFromClock()
            }
        }
    }

    /** Fixes the point the countdown is measured from at the current value. */
    private fun anchor() {
        anchorSecondsLeft = state.secondsLeft
        anchorAtMillis = nowMillis()
    }

    private fun scheduleSave() {
        val account = uid ?: return
        val lastTickAt = if (state.running) anchorAtMillis else null
        val saved = state

        saveJob?.cancel()
        saveJob = scope.launch {
            // Long enough to collapse a double-tap on the mode tabs into one write,
            // short enough to be gone before anyone could close the tab.
            delay(SAVE_DEBOUNCE_MILLIS)
            repository.save(account, saved, lastTickAt)
        }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MILLIS = 50L
    }
}

/** Whether a settings change touched anything the current countdown is measured in. */
private fun Settings.durationsDifferFrom(previous: Settings): Boolean =
    TimerMode.entries.any { durationSeconds(it) != previous.durationSeconds(it) }

private fun nowMillis(): Long = Date.now().toLong()

/**
 * How long until the clock rolls over to the next whole second.
 *
 * Waiting for the boundary rather than sleeping a flat second means the digits
 * change when the second does, instead of at whatever fraction the timer happened
 * to be started at.
 */
private fun millisUntilNextSecond(): Long =
    (MILLIS_PER_SECOND - Date().getMilliseconds()).toLong()

private const val MILLIS_PER_SECOND = 1000
