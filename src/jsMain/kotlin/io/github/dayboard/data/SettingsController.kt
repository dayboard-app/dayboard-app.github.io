package io.github.dayboard.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dayboard.domain.model.BoardColumn
import io.github.dayboard.domain.model.CardId
import io.github.dayboard.domain.model.Settings
import io.github.dayboard.domain.model.moveCard
import io.github.dayboard.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The user's settings, in memory and on the way to Firestore.
 *
 * Every change is applied locally first and written afterwards. That ordering is
 * what makes dragging a card feel instant: waiting for a round trip before moving
 * it would make the board lag behind the finger.
 */
class SettingsController(
    private val repository: SettingsRepository,
    private val scope: CoroutineScope,
) {

    var settings: Settings by mutableStateOf(Settings.Default)
        private set

    /** True once the stored settings have arrived, or once we know there are none. */
    var loaded: Boolean by mutableStateOf(false)
        private set

    private var uid: String? = null
    private var stopListening: (() -> Unit)? = null
    private var saveJob: Job? = null

    /**
     * Follows one account's settings.
     *
     * Safe to call repeatedly; a second call for a different account detaches the
     * first listener, which otherwise keeps reading a document the new account has
     * no right to.
     */
    fun start(uid: String) {
        if (this.uid == uid) return

        stop()
        this.uid = uid
        stopListening = repository.observe(uid) { stored ->
            settings = stored
            loaded = true
        }
    }

    /** Detaches, and forgets the previous account's settings. */
    fun stop() {
        stopListening?.invoke()
        stopListening = null
        saveJob?.cancel()
        saveJob = null
        uid = null
        settings = Settings.Default
        loaded = false
    }

    /**
     * Applies a change and saves it.
     *
     * Saving is debounced, because the changes that arrive fastest - dragging a
     * card, sliding a duration - would otherwise be one write per frame. The delay
     * is short enough that a user who changes something and immediately closes the
     * tab still keeps it.
     */
    fun update(transform: (Settings) -> Settings) {
        settings = transform(settings)
        scheduleSave()
    }

    fun toggleCollapsed(card: CardId) = update {
        it.copy(cardLayout = it.cardLayout.toggleCollapsed(card))
    }

    /** Commits a finished drag. See `moveCard` for why the indices are visible ones. */
    fun moveCard(from: BoardColumn, to: BoardColumn, sourceIndex: Int, destinationIndex: Int) =
        update { current ->
            current.copy(
                cardLayout = current.cardLayout.moveCard(
                    from = from,
                    to = to,
                    sourceIndex = sourceIndex,
                    destinationIndex = destinationIndex,
                    isVisible = current::isVisible,
                ),
            )
        }

    private fun scheduleSave() {
        val account = uid ?: return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            repository.save(account, settings)
        }
    }

    private companion object {
        /**
         * Matches the original's layout debounce. Long enough to collapse a drag
         * into one write, short enough not to lose a change to a closed tab.
         */
        const val SAVE_DEBOUNCE_MILLIS = 500L
    }
}
