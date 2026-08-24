package io.github.dayboard.domain.usecase

import io.github.dayboard.domain.model.GeoLocation
import io.github.dayboard.domain.model.Weather
import io.github.dayboard.domain.model.WeatherCondition
import io.github.dayboard.domain.repository.WeatherRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoadWeatherTest {

    @Test
    fun asksNothingAtAllWhenWeatherIsSwitchedOff() = runTest {
        val repository = FakeWeatherRepository(byAddress = BERLIN, reading = READING)

        assertNull(loadWeather(enabled = false, city = "Tbilisi", repository = repository))
        assertEquals(emptyList(), repository.calls)
    }

    @Test
    fun readsTheWeatherWhereTheTypedCityTurnedOutToBe() = runTest {
        val repository = FakeWeatherRepository(geocoded = TBILISI, reading = READING)

        assertEquals(
            READING,
            loadWeather(enabled = true, city = "Tbilisi", repository = repository),
        )
        assertEquals(listOf("geocode:Tbilisi", "read:Tbilisi"), repository.calls)
    }

    @Test
    fun givesUpWhenATypedCityCannotBeFound() = runTest {
        // The point of the whole use case. A city the user typed is an instruction,
        // so an unfindable one shows nothing - falling back would report the
        // temperature somewhere else with no sign that it is somewhere else.
        val repository = FakeWeatherRepository(
            geocoded = null,
            byAddress = BERLIN,
            byDevice = BERLIN,
            reading = READING,
        )

        assertNull(loadWeather(enabled = true, city = "Atlantis", repository = repository))
        assertEquals(listOf("geocode:Atlantis"), repository.calls)
    }

    @Test
    fun trimsATypedCityBeforeLookingItUp() = runTest {
        val repository = FakeWeatherRepository(geocoded = TBILISI, reading = READING)

        loadWeather(enabled = true, city = "  Tbilisi  ", repository = repository)

        assertEquals(listOf("geocode:Tbilisi", "read:Tbilisi"), repository.calls)
    }

    @Test
    fun treatsAnEmptyCityAsNoCity() = runTest {
        // What the settings field leaves behind when it is cleared: the input stores
        // null, but a stored blank from any other route must mean the same thing.
        listOf(null, "", "   ").forEach { city ->
            val repository = FakeWeatherRepository(byAddress = BERLIN, reading = READING)

            assertEquals(
                READING,
                loadWeather(enabled = true, city = city, repository = repository),
                "city ${city?.let { "\"$it\"" }}",
            )
            assertEquals(listOf("address", "read:Berlin"), repository.calls)
        }
    }

    @Test
    fun asksTheDeviceOnlyWhenTheAddressLookupFails() = runTest {
        val repository =
            FakeWeatherRepository(byAddress = null, byDevice = BERLIN, reading = READING)

        assertEquals(READING, loadWeather(enabled = true, city = null, repository = repository))
        assertEquals(listOf("address", "device", "read:Berlin"), repository.calls)
    }

    @Test
    fun givesUpWhenNeitherAutomaticLookupKnowsWhereThisIs() = runTest {
        val repository = FakeWeatherRepository(byAddress = null, byDevice = null, reading = READING)

        assertNull(loadWeather(enabled = true, city = null, repository = repository))
        // No forecast request: there is nowhere to ask about.
        assertEquals(listOf("address", "device"), repository.calls)
    }

    @Test
    fun givesUpWhenTheForecastItselfFails() = runTest {
        val repository = FakeWeatherRepository(byAddress = BERLIN, reading = null)

        assertNull(loadWeather(enabled = true, city = null, repository = repository))
        assertEquals(listOf("address", "read:Berlin"), repository.calls)
    }

    private companion object {
        val TBILISI = GeoLocation(latitude = 41.7151, longitude = 44.8271, city = "Tbilisi")
        val BERLIN = GeoLocation(latitude = 52.52, longitude = 13.405, city = "Berlin")
        val READING = Weather(21, WeatherCondition.Clear, "Tbilisi")
    }
}

/**
 * Records what was asked, in order, and answers with whatever it was built with.
 *
 * The order is the assertion in most of these tests: the use case is a sequence of
 * decisions about which lookup to try, so what it did *not* ask is as much of the
 * behaviour as what it returned.
 */
private class FakeWeatherRepository(
    private val geocoded: GeoLocation? = null,
    private val byAddress: GeoLocation? = null,
    private val byDevice: GeoLocation? = null,
    private val reading: Weather? = null,
) : WeatherRepository {

    val calls = mutableListOf<String>()

    override suspend fun geocodeCity(city: String): GeoLocation? {
        calls += "geocode:$city"
        return geocoded
    }

    override suspend fun locateByAddress(): GeoLocation? {
        calls += "address"
        return byAddress
    }

    override suspend fun locateByDevice(): GeoLocation? {
        calls += "device"
        return byDevice
    }

    override suspend fun readWeather(location: GeoLocation): Weather? {
        calls += "read:${location.city}"
        return reading
    }
}
