package io.github.dayboard.data.weather

import io.github.dayboard.domain.model.GeoLocation
import io.github.dayboard.domain.model.Weather
import io.github.dayboard.domain.model.cityNameOrFallback
import io.github.dayboard.domain.model.weatherConditionOf
import io.github.dayboard.domain.repository.WeatherRepository
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.url.URLSearchParams
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * [WeatherRepository] backed by open-meteo, plus two ways of guessing where "here" is.
 *
 * All of these services are public and unauthenticated, which is why the original's
 * server-side proxy could be dropped: there is no key to keep out of the bundle.
 * The one thing lost with it was the second IP lookup, `ip-api.com`, which serves
 * plain HTTP only and is therefore blocked as mixed content on a page served over
 * HTTPS - it could never have worked from the browser. The device lookup covers the
 * same gap and asks the user rather than guessing.
 *
 * Every method answers null rather than throwing, as the interface requires.
 */
class OpenMeteoWeatherRepository : WeatherRepository {

    override suspend fun geocodeCity(city: String): GeoLocation? {
        val query = URLSearchParams().apply {
            append("name", city)
            append("count", RESULT_COUNT)
        }

        return toGeoLocation(firstGeocodingResult(query))
    }

    override suspend fun locateByAddress(): GeoLocation? {
        val response = fetchJson(IP_LOCATION_URL) ?: return null
        val latitude = asDouble(response.latitude) ?: return null
        val longitude = asDouble(response.longitude) ?: return null

        return GeoLocation(
            latitude = latitude,
            longitude = longitude,
            city = cityNameOrFallback(response.city as? String),
        )
    }

    override suspend fun locateByDevice(): GeoLocation? {
        val position = devicePosition() ?: return null
        val latitude = position.coords.latitude
        val longitude = position.coords.longitude

        // The device gives coordinates and no name, so the name is a second lookup -
        // and an optional one. A location the user allowed is worth showing the
        // weather for even if nothing will admit what the place is called.
        val query = URLSearchParams().apply {
            append("name", "")
            append("latitude", latitude.toString())
            append("longitude", longitude.toString())
            append("count", RESULT_COUNT)
        }

        return GeoLocation(
            latitude = latitude,
            longitude = longitude,
            city = cityNameOrFallback(nameOf(firstGeocodingResult(query))),
        )
    }

    override suspend fun readWeather(location: GeoLocation): Weather? {
        val query = URLSearchParams().apply {
            append("latitude", location.latitude.toString())
            append("longitude", location.longitude.toString())
            append("current_weather", "true")
        }

        val current = fetchJson("$FORECAST_URL?$query")?.current_weather ?: return null
        val temperature = asDouble(current.temperature) ?: return null
        val code = asDouble(current.weathercode) ?: return null

        return Weather(
            // Rounded here and nowhere else, so the reading the card holds is already
            // the number it shows.
            temperatureCelsius = temperature.roundToInt(),
            condition = weatherConditionOf(code.roundToInt()),
            city = location.city,
        )
    }

    /** Runs a geocoding query and returns its first hit, if it has one. */
    private suspend fun firstGeocodingResult(query: URLSearchParams): dynamic =
        firstElement(fetchJson("$GEOCODING_URL?$query")?.results)

    /**
     * Asks the device where it is, once.
     *
     * The callback pair collapses into one nullable answer because the app treats
     * every refusal the same: no permission, no signal and no hardware all mean
     * "we do not know", and none of them deserves a different card.
     */
    private suspend fun devicePosition(): GeolocationPosition? {
        val geolocation = window.navigator.geolocation ?: return null

        return suspendCancellableCoroutine { continuation ->
            geolocation.getCurrentPosition(
                { position -> continuation.resume(position) },
                { continuation.resume(null) },
                geolocationOptions(DEVICE_TIMEOUT_MILLIS),
            )
        }
    }

    private companion object {
        const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        const val IP_LOCATION_URL = "https://ipapi.co/json/"

        /** One hit is all any of these lookups uses. */
        const val RESULT_COUNT = "1"

        /**
         * The original's five seconds. Long enough for a device that is going to
         * answer, short enough that a device that never will does not hold the card
         * on a spinner.
         */
        const val DEVICE_TIMEOUT_MILLIS = 5000
    }
}

/**
 * Fetches a URL and parses it as JSON, or answers null.
 *
 * Failure here is not exceptional: a blocked request, an offline device, a rate
 * limit and a service returning HTML instead of JSON all mean the same thing to a
 * clock card, so they all arrive as null and the card does without.
 *
 * Cancellation is deliberately let through. It is not a failed request, it is the
 * caller saying it no longer wants the answer - swallowing it here would keep a
 * refresh loop running after the settings it belongs to had changed.
 */
private suspend fun fetchJson(url: String): dynamic = try {
    val response = window.fetch(url).await()
    if (response.ok) response.json().await() else null
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Throwable) {
    null
}

/**
 * The first element of a JSON array, or null for anything that is not one.
 *
 * These services answer a query that matched nothing by leaving the array out
 * entirely, but an empty array means the same thing and either would be read as a
 * hit by a plain index - putting `undefined` coordinates into the next request.
 */
private fun firstElement(value: dynamic): dynamic =
    if (value != null && js("Array.isArray")(value) as Boolean) {
        value.unsafeCast<Array<dynamic>>().firstOrNull()
    } else {
        null
    }

/**
 * Reads the shape both geocoding lookups return.
 *
 * A plain function rather than an extension on `dynamic`: Kotlin does not resolve
 * extensions on a dynamic receiver, it compiles the call into a property lookup on
 * the JS object, which would silently be `undefined` at runtime.
 */
private fun toGeoLocation(result: dynamic): GeoLocation? {
    if (result == null) return null

    val latitude = asDouble(result.latitude) ?: return null
    val longitude = asDouble(result.longitude) ?: return null

    return GeoLocation(
        latitude = latitude,
        longitude = longitude,
        city = cityNameOrFallback(nameOf(result)),
    )
}

/** Reads a geocoding result's place name, which it may not have. */
private fun nameOf(result: dynamic): String? = if (result == null) null else result.name as? String

/**
 * Reads a JSON number.
 *
 * Everything numeric in these responses is a JS number, which is a `Double` in
 * Kotlin whether or not it was written with a decimal point. Reading it as anything
 * narrower would drop a whole-number temperature on the floor.
 */
private fun asDouble(value: dynamic): Double? = value as? Double
