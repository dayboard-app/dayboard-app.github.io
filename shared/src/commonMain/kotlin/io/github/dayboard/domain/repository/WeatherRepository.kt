package io.github.dayboard.domain.repository

import io.github.dayboard.domain.model.GeoLocation
import io.github.dayboard.domain.model.Weather

/**
 * Everything the clock card needs from the network.
 *
 * Four calls rather than one, so the order they are tried in can live above this
 * interface and be tested without a network. An implementation only has to know how
 * to make one request each.
 *
 * Every call returns null for "could not", and none of them throws for an ordinary
 * failure. Weather is decoration on a clock: an unreachable forecast is not an error
 * the user needs told about, it is a card that shows the time and nothing else.
 */
interface WeatherRepository {

    /** Looks a typed-in city up by name. Null when nowhere matches it. */
    suspend fun geocodeCity(city: String): GeoLocation?

    /** Guesses the location from the address the request comes from. */
    suspend fun locateByAddress(): GeoLocation?

    /** Asks the device, which asks the user. Null if they decline or it times out. */
    suspend fun locateByDevice(): GeoLocation?

    /** Reads the current conditions at a location. */
    suspend fun readWeather(location: GeoLocation): Weather?
}
