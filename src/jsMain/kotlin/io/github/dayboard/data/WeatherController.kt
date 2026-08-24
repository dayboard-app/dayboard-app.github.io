package io.github.dayboard.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dayboard.domain.model.Weather
import io.github.dayboard.domain.repository.WeatherRepository
import io.github.dayboard.domain.usecase.loadWeather
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The current reading for the clock card, kept fresh.
 *
 * The decision of where to look and what to do when a lookup fails is
 * [loadWeather] in `:shared`, which is tested. This holds the answer, repeats the
 * question every ten minutes, and starts over when the settings change.
 */
class WeatherController(
    private val repository: WeatherRepository,
    private val scope: CoroutineScope,
) {

    var weather: Weather? by mutableStateOf(null)
        private set

    /**
     * True while a lookup is in flight.
     *
     * The card only shows this when it has nothing else to show. A refresh behind an
     * already-visible temperature is not worth a spinner every ten minutes.
     */
    var loading: Boolean by mutableStateOf(false)
        private set

    private var job: Job? = null

    /**
     * Follows one weather setting until told to follow another.
     *
     * Called whenever the switch or the city changes, so the first thing it does is
     * abandon the previous loop - otherwise changing the city twice would leave two
     * loops racing to write the same state, and the older one could win.
     */
    fun follow(enabled: Boolean, city: String?) {
        job?.cancel()
        job = null

        if (!enabled) {
            // Switching weather off clears it rather than freezing the last reading,
            // so turning it back on cannot show yesterday's temperature.
            weather = null
            loading = false
            return
        }

        job = scope.launch {
            while (isActive) {
                loading = true
                weather = loadWeather(enabled = true, city = city, repository = repository)
                loading = false
                delay(REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    /** Stops looking, and forgets what it found. */
    fun stop() {
        follow(enabled = false, city = null)
    }

    private companion object {
        /**
         * The original's ten minutes. Weather that is a few minutes stale is still
         * the weather; asking more often would be traffic nobody would notice.
         */
        const val REFRESH_INTERVAL_MILLIS = 10L * 60 * 1000
    }
}
