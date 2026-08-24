package io.github.dayboard.ui.cards

import androidx.compose.runtime.Composable
import io.github.dayboard.domain.model.Weather
import io.github.dayboard.domain.model.WeatherCondition
import io.github.dayboard.presentation.ClockTime
import io.github.dayboard.presentation.formatDate
import io.github.dayboard.presentation.formatSecondsSuffix
import io.github.dayboard.presentation.formatTime
import io.github.dayboard.ui.icons.Icon
import io.github.dayboard.ui.icons.LucideIcon
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The clock card's contents: the time, the date, and the weather if there is any.
 *
 * [expanded] only changes how big everything is. The card renders the same pieces
 * either way, so there is one place to change what the clock shows rather than two
 * that can drift apart.
 */
@Composable
fun ClockCard(
    time: ClockTime,
    showSeconds: Boolean,
    weather: Weather?,
    loadingWeather: Boolean,
    expanded: Boolean,
) {
    Div({ classes(*sized("clock", expanded)) }) {
        Div({ classes("clock__face") }) {
            Div({ classes("clock__time", "font-mono-timer") }) {
                Text(time.formatTime())

                if (showSeconds) {
                    // A separate span so the seconds can be smaller and muted: they
                    // tick constantly, and at the size of the hours they would be the
                    // most distracting thing on a screen meant to help you focus.
                    Span({ classes("clock__seconds") }) { Text(time.formatSecondsSuffix()) }
                }
            }

            Div({ classes("clock__date") }) { Text(time.formatDate()) }
        }

        // Nothing at all is the third state, and the common one: no permission, no
        // network, or weather switched off. The clock is the card; weather is a
        // decoration it does without.
        when {
            weather != null -> WeatherPill(weather, expanded)
            loadingWeather -> LoadingWeatherPill()
            else -> Unit
        }
    }
}

@Composable
private fun WeatherPill(weather: Weather, expanded: Boolean) {
    Div({ classes(*sized("weather", expanded)) }) {
        Icon(
            icon = weather.condition.icon,
            size = if (expanded) CONDITION_ICON_EXPANDED else CONDITION_ICON,
            className = "weather__condition",
        )

        Div({ classes("weather__item") }) {
            Icon(LucideIcon.Thermometer, size = if (expanded) LABEL_ICON_EXPANDED else LABEL_ICON)
            Span({ classes("weather__temperature") }) { Text(weather.temperatureLabel) }
        }

        Div({ classes("weather__item") }) {
            Icon(LucideIcon.MapPin, size = if (expanded) PLACE_ICON_EXPANDED else PLACE_ICON)
            Span({ classes("weather__city") }) { Text(weather.city) }
        }
    }
}

/**
 * Shown only while the first lookup is running.
 *
 * A refresh behind an already-visible temperature replaces nothing, because a
 * spinner every ten minutes would be movement reporting that nothing changed.
 */
@Composable
private fun LoadingWeatherPill() {
    Div({ classes("weather", "weather--loading") }) {
        Icon(LucideIcon.LoaderCircle, size = LABEL_ICON, className = "spinner")
        Span { Text("Loading weather...") }
    }
}

/** Adds the `--expanded` modifier to a block's class, for the full-screen card. */
private fun sized(block: String, expanded: Boolean): Array<String> =
    if (expanded) arrayOf(block, "$block--expanded") else arrayOf(block)

/**
 * The picture for a condition.
 *
 * The domain names conditions and this names drawings, so a change to the icon set
 * cannot reach the WMO code table and a change to that table cannot reach the icons.
 */
private val WeatherCondition.icon: LucideIcon
    get() = when (this) {
        WeatherCondition.Clear -> LucideIcon.Sun
        WeatherCondition.PartlyCloudy -> LucideIcon.CloudSun
        WeatherCondition.Cloudy -> LucideIcon.Cloud
        WeatherCondition.Drizzle -> LucideIcon.CloudDrizzle
        WeatherCondition.Rain -> LucideIcon.CloudRain
        WeatherCondition.Snow -> LucideIcon.CloudSnow
        WeatherCondition.Thunderstorm -> LucideIcon.CloudLightning
    }

// Icon sizes are set here rather than in CSS because `Icon` writes width and height
// inline, which a stylesheet rule could only beat with `!important`.
private const val CONDITION_ICON = 20
private const val CONDITION_ICON_EXPANDED = 32
private const val LABEL_ICON = 14
private const val LABEL_ICON_EXPANDED = 20
private const val PLACE_ICON = 12
private const val PLACE_ICON_EXPANDED = 16
