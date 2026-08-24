package io.github.dayboard.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dayboard.presentation.ClockTime
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.w3c.dom.Document
import kotlin.js.Date

/**
 * The wall clock, re-read once a second.
 *
 * Every tick reads the device clock again instead of adding a second to the last
 * one. That matters more than it looks: a browser throttles timers in a background
 * tab and eventually all but freezes them, so a counted clock would come back from
 * an hour in another tab an hour slow, with nothing to correct it. Reading the real
 * clock makes a missed tick cost a skipped frame and nothing else.
 */
class ClockController(private val scope: CoroutineScope) {

    var time: ClockTime by mutableStateOf(readClock())
        private set

    private var job: Job? = null

    /**
     * Begins ticking, and keeps ticking for as long as the scope lives.
     *
     * Safe to call twice; the second call is ignored rather than starting a second
     * loop that writes the same state at a different phase.
     */
    fun start() {
        if (job != null) return

        // The loop below cannot cover the moment the page comes back. A browser
        // throttles a hidden tab's timers to about one a minute, so the tick that
        // would correct the display can be most of a minute away - long enough to
        // read a wrong clock and believe it. Correcting on the way back costs one
        // read and removes that window entirely.
        document.addEventListener("visibilitychange", {
            if (!document.isHidden) time = readClock()
        })

        job = scope.launch {
            while (isActive) {
                delay(millisUntilNextSecond())
                time = readClock()
            }
        }
    }
}

/**
 * `document.hidden`, which Kotlin's `Document` does not declare.
 *
 * A browser too old to have the Page Visibility API reports `undefined`, which is
 * read as visible. That is the harmless direction: the clock is re-read when it did
 * not strictly need to be.
 */
private val Document.isHidden: Boolean get() = asDynamic().hidden == true

/** Reads the device clock, in the browser's own field conventions. */
private fun readClock(): ClockTime = Date().let { now ->
    ClockTime(
        hour = now.getHours(),
        minute = now.getMinutes(),
        second = now.getSeconds(),
        dayOfWeek = now.getDay(),
        month = now.getMonth(),
        dayOfMonth = now.getDate(),
    )
}

/**
 * How long until the clock rolls over to the next whole second.
 *
 * Sleeping a flat second instead would leave the display changing at whatever
 * fraction of a second the page happened to load at, and drifting further from the
 * real rollover with every late wake-up. Waiting for the boundary means the digits
 * change when the second does.
 */
private fun millisUntilNextSecond(): Long =
    (MILLIS_PER_SECOND - Date().getMilliseconds()).toLong()

private const val MILLIS_PER_SECOND = 1000
