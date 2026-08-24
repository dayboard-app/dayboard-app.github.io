package io.github.dayboard.domain.repository

import io.github.dayboard.domain.model.Settings

/**
 * The user's settings, stored once per account.
 *
 * Reads are a subscription rather than a fetch because the same account can be
 * open in two places, and the original keeps them in step. Writes are whole-object
 * so a change made here cannot clobber a field it never read.
 */
interface SettingsRepository {

    /**
     * Reports the stored settings, now and whenever they change elsewhere.
     *
     * Never reports this device's own writes back: the caller already applied them
     * optimistically, and echoing them would fight whatever is being edited right
     * now. An account with nothing stored yet reports [Settings.Default].
     *
     * @return the function that stops listening. It must be called when the
     *   account changes, or the listener keeps reading a document the next account
     *   has no right to.
     */
    fun observe(uid: String, onChange: (Settings) -> Unit): () -> Unit

    /** Stores the whole settings object. */
    suspend fun save(uid: String, settings: Settings)
}
