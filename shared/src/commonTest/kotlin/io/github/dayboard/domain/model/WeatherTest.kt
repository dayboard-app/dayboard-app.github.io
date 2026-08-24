package io.github.dayboard.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class WeatherTest {

    @Test
    fun weatherConditionOf_mapsEveryCodeTheOriginalNames() {
        // The whole table, spelled out. This is a transcription of somebody else's
        // list, so the test that protects it has to be a transcription too: deriving
        // the expected values from ranges would reproduce whatever I misread.
        val expected = mapOf(
            WeatherCondition.Clear to listOf(0, 1),
            WeatherCondition.PartlyCloudy to listOf(2),
            WeatherCondition.Cloudy to listOf(3, 45, 48),
            WeatherCondition.Drizzle to listOf(51, 53, 55, 56, 57),
            WeatherCondition.Rain to listOf(61, 63, 65, 66, 67, 80, 81, 82),
            WeatherCondition.Snow to listOf(71, 73, 75, 77, 85, 86),
            WeatherCondition.Thunderstorm to listOf(95, 96, 99),
        )

        expected.forEach { (condition, codes) ->
            codes.forEach { code ->
                assertEquals(condition, weatherConditionOf(code), "code $code")
            }
        }
    }

    @Test
    fun weatherConditionOf_coversEveryCondition() {
        // Guards the mapping against a condition that nothing can ever produce,
        // which would be a drawing nobody ever sees.
        val reachable = (0..100).map(::weatherConditionOf).toSet()

        assertEquals(WeatherCondition.entries.toSet(), reachable)
    }

    @Test
    fun weatherConditionOf_readsAnUnknownCodeAsPartlyCloudy() {
        // Gaps inside the table, values outside it, and nonsense. A code the WMO
        // adds next year must still leave the temperature on screen.
        listOf(-1, 4, 30, 49, 58, 70, 83, 90, 97, 100, 1000, Int.MAX_VALUE, Int.MIN_VALUE)
            .forEach { code ->
                assertEquals(WeatherCondition.PartlyCloudy, weatherConditionOf(code), "code $code")
            }
    }

    @Test
    fun temperatureLabel_isCelsiusWithADegreeSign() {
        assertEquals("21°C", Weather(21, WeatherCondition.Clear, "Tbilisi").temperatureLabel)
    }

    @Test
    fun temperatureLabel_keepsTheSignOnAFreezingReading() {
        // Below zero is the case a formatter built around string concatenation of a
        // positive example gets wrong.
        assertEquals("-8°C", Weather(-8, WeatherCondition.Snow, "Oslo").temperatureLabel)
        assertEquals("0°C", Weather(0, WeatherCondition.Snow, "Oslo").temperatureLabel)
    }

    @Test
    fun cityNameOrFallback_keepsARealName() {
        assertEquals("Tbilisi", cityNameOrFallback("Tbilisi"))
        assertEquals("São Paulo", cityNameOrFallback("  São Paulo  "))
    }

    @Test
    fun cityNameOrFallback_namesTheUnnamed() {
        // Each of these is one lookup's way of saying it found no place name.
        listOf(null, "", "   ", "\t").forEach { name ->
            assertEquals(UNNAMED_PLACE, cityNameOrFallback(name), "name ${name?.let { "\"$it\"" }}")
        }
    }
}
