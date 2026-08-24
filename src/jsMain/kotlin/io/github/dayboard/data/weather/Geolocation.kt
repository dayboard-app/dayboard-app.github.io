package io.github.dayboard.data.weather

import org.w3c.dom.Navigator

/**
 * The slice of the browser's Geolocation API this app uses.
 *
 * Declared here because Kotlin's browser stdlib does not carry these types, and
 * kept to the three members that are actually called: an external declaration is a
 * promise about what exists at runtime, and every unused member is a promise nobody
 * has checked.
 *
 * Unlike the Firebase externals these carry no `@JsModule`. Geolocation is a
 * browser global reached through `navigator`, not something that is imported.
 */
external interface Geolocation {

    /**
     * Asks for the device's position once.
     *
     * Exactly one of the two callbacks runs, which is what lets the caller wrap this
     * in a single suspending answer.
     */
    fun getCurrentPosition(
        success: (GeolocationPosition) -> Unit,
        error: (dynamic) -> Unit,
        options: GeolocationPositionOptions,
    )
}

external interface GeolocationPosition {
    val coords: GeolocationCoordinates
}

external interface GeolocationCoordinates {
    val latitude: Double
    val longitude: Double
}

external interface GeolocationPositionOptions {
    var timeout: Int
}

/**
 * `navigator.geolocation`, if this browser has it.
 *
 * Null on an insecure origin and in any browser old enough to lack the API. Both
 * are rare and neither is an error worth reporting, but calling a method on
 * `undefined` throws a `TypeError` that would escape the weather lookup and kill
 * the refresh loop, so the absence is answered here instead.
 */
internal val Navigator.geolocation: Geolocation?
    get() {
        val api = asDynamic().geolocation
        return if (api == null || api == undefined) null else api.unsafeCast<Geolocation>()
    }

/** Builds the options object, since Kotlin has no JS object literal. */
internal fun geolocationOptions(timeoutMillis: Int): GeolocationPositionOptions =
    js("{}").unsafeCast<GeolocationPositionOptions>().apply { timeout = timeoutMillis }
