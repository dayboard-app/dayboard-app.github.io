package io.github.dayboard.domain.model

/**
 * What the sky is doing, reduced to the states the clock card can draw.
 *
 * The forecast reports far more detail than seven cases, but the card shows one
 * small icon, so anything finer would be a distinction nobody could see. Naming
 * the conditions rather than the icons keeps the drawing out of the domain: which
 * picture stands for [Rain] is the card's business.
 */
enum class WeatherCondition {
    Clear,
    PartlyCloudy,
    Cloudy,
    Drizzle,
    Rain,
    Snow,
    Thunderstorm,
}

/**
 * Reads a WMO weather code as one of the conditions above.
 *
 * The groupings are the original's, and the codes are the World Meteorological
 * Organization's table that open-meteo reports: 0 and 1 are clear to mainly clear,
 * 45 and 48 are fog, the 5x codes are drizzle, 6x and 8x rain and showers, 7x and
 * 85/86 snow, and 95 upwards thunderstorms.
 *
 * An unknown code is drawn as [PartlyCloudy] rather than dropped. The table gains
 * entries over time, and a code this app has never heard of still means the forecast
 * worked; showing something noncommittal beats hiding the temperature as well.
 */
fun weatherConditionOf(code: Int): WeatherCondition = when (code) {
    0, 1 -> WeatherCondition.Clear
    2 -> WeatherCondition.PartlyCloudy
    3, 45, 48 -> WeatherCondition.Cloudy
    51, 53, 55, 56, 57 -> WeatherCondition.Drizzle
    61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.Rain
    71, 73, 75, 77, 85, 86 -> WeatherCondition.Snow
    95, 96, 99 -> WeatherCondition.Thunderstorm
    else -> WeatherCondition.PartlyCloudy
}

/**
 * One reading, ready to render.
 *
 * The temperature is already rounded because the card has no use for the fraction
 * the forecast returns, and rounding once here means the displayed value cannot
 * disagree with itself between two places that format it.
 */
data class Weather(
    val temperatureCelsius: Int,
    val condition: WeatherCondition,
    val city: String,
) {

    /**
     * The temperature as the card shows it.
     *
     * Celsius only, exactly as the original: there is no unit setting, so there is
     * no conversion to get wrong.
     */
    val temperatureLabel: String get() = "$temperatureCelsius°C"
}
