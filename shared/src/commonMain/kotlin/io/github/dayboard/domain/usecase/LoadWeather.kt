package io.github.dayboard.domain.usecase

import io.github.dayboard.domain.model.Weather
import io.github.dayboard.domain.repository.WeatherRepository

/**
 * Finds out what the weather is, or decides there is nothing to show.
 *
 * Null means "show nothing", and it is the answer to every failure as well as to
 * the weather being switched off. The card cannot tell the difference and does not
 * need to: it either has a reading or it does not.
 *
 * The interesting part is that the two branches differ. A city the user typed is an
 * instruction, so if it cannot be found the card shows nothing rather than quietly
 * reporting the weather somewhere else - being told the temperature in the wrong
 * place, with no hint that it is the wrong place, is worse than being told nothing.
 * The automatic path does fall back, because there both lookups are guesses at the
 * same question, and a second guess is still an answer to what was asked.
 */
suspend fun loadWeather(
    enabled: Boolean,
    city: String?,
    repository: WeatherRepository,
): Weather? {
    if (!enabled) return null

    val requested = city.orEmpty().trim().ifEmpty { null }

    val location = if (requested != null) {
        repository.geocodeCity(requested)
    } else {
        repository.locateByAddress() ?: repository.locateByDevice()
    } ?: return null

    return repository.readWeather(location)
}
