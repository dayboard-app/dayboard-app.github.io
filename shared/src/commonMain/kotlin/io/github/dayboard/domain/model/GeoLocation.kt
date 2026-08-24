package io.github.dayboard.domain.model

/**
 * Somewhere to ask the forecast about, and the name to show for it.
 *
 * The name travels with the coordinates because the two come from the same lookup.
 * Asking again later for a label to put under the temperature would be a second
 * request that can fail on its own, leaving a reading with nowhere attached to it.
 */
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val city: String,
)

/** Shown when a lookup found coordinates but no place name. */
const val UNNAMED_PLACE: String = "Your location"

/**
 * The name to show for a location, for lookups that may not return one.
 *
 * Every source has its own way of saying "no name": absent, null, or an empty
 * string. They all mean the same thing to the card, so they are collapsed here
 * rather than in each source.
 */
fun cityNameOrFallback(name: String?): String = name.orEmpty().trim().ifEmpty { UNNAMED_PLACE }
