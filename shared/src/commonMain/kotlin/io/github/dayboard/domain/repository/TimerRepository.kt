package io.github.dayboard.domain.repository

import io.github.dayboard.domain.model.StoredTimer
import io.github.dayboard.domain.model.TimerState

/**
 * Where the timer is written down so it survives a reload and reaches other devices.
 *
 * Saving takes the instant explicitly rather than reading a clock, so the rule for
 * *when* a timer was last measured stays with the timer rather than being decided
 * by whichever implementation is storing it.
 */
interface TimerRepository {

    /**
     * Follows one account's timer, reporting changes made anywhere else.
     *
     * Changes this device made are not reported back. Every caller has already
     * applied its own change, so echoing it would undo whatever came after.
     *
     * The callback receives null when nothing is stored yet.
     *
     * Returns a function that detaches the listener.
     */
    fun observe(uid: String, onChange: (StoredTimer?) -> Unit): () -> Unit

    /** Writes the timer down. [lastTickAtMillis] is null when it is not running. */
    suspend fun save(uid: String, state: TimerState, lastTickAtMillis: Long?)
}
