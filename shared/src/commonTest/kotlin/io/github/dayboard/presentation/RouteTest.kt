package io.github.dayboard.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class RouteTest {

    private data class Case(val hash: String, val expected: Route)

    @Test
    fun parseRoute_resolvesEveryHashShapeABrowserProduces() {
        listOf(
            // A visitor to the bare domain: no hash at all.
            Case("", Route.Dashboard),
            Case("#", Route.Dashboard),
            Case("#/", Route.Dashboard),
            // Already stripped of its `#` by a caller.
            Case("/", Route.Dashboard),
            Case("#/auth", Route.Auth),
            Case("#/auth/", Route.Auth),
            Case("auth", Route.Auth),
            // A redirect target carried as a query string must not break matching.
            Case("#/auth?from=%2F", Route.Auth),
            Case("#/?from=%2Fauth", Route.Dashboard),
            // Unknown, over-long and wrong-case addresses all fall through rather
            // than reaching a screen by accident.
            Case("#/nope", Route.NotFound),
            Case("#/auth/extra", Route.NotFound),
            Case("#/AUTH", Route.NotFound),
        ).forEach { (hash, expected) ->
            assertEquals(expected, parseRoute(hash), "hash \"$hash\"")
        }
    }

    @Test
    fun hash_roundTripsThroughParseRoute() {
        listOf(Route.Dashboard, Route.Auth).forEach { route ->
            assertEquals(route, parseRoute(route.hash), "route $route")
        }
    }

    @Test
    fun hash_reportsTheDashboardForNotFound() {
        assertEquals(Route.Dashboard.hash, Route.NotFound.hash)
    }
}
