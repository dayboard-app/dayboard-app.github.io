package io.github.dayboard.presentation

import io.github.dayboard.domain.model.AuthState
import io.github.dayboard.domain.model.AuthUser
import kotlin.test.Test
import kotlin.test.assertEquals

class DestinationTest {

    private val signedIn = AuthState.SignedIn(
        AuthUser(uid = "u1", email = "someone@example.com", emailVerified = true),
    )

    /**
     * Listed here rather than exposed from `Route`, which would be production API
     * existing only for tests. A route added without updating this list is still
     * caught: `resolveDestination` matches exhaustively and stops compiling.
     */
    private val allRoutes = listOf(Route.Dashboard, Route.Auth, Route.NotFound)

    @Test
    fun whileTheSessionIsResolving_nothingIsShownAndNothingIsRewritten() {
        // Every route, so a returning user's address survives the wait rather than
        // being redirected away before Firebase has reported.
        allRoutes.forEach { route ->
            assertEquals(Destination.Pending, resolveDestination(AuthState.Loading, route), "route $route")
        }
    }

    @Test
    fun signedOut_theDashboardSendsYouToSignIn() {
        assertEquals(
            Destination.Redirect(Route.Auth),
            resolveDestination(AuthState.SignedOut, Route.Dashboard),
        )
    }

    @Test
    fun signedOut_theSignInPageIsShown() {
        assertEquals(
            Destination.Show(Route.Auth),
            resolveDestination(AuthState.SignedOut, Route.Auth),
        )
    }

    @Test
    fun signedIn_theSignInPageSendsYouToTheDashboard() {
        assertEquals(
            Destination.Redirect(Route.Dashboard),
            resolveDestination(signedIn, Route.Auth),
        )
    }

    @Test
    fun signedIn_theDashboardIsShown() {
        assertEquals(
            Destination.Show(Route.Dashboard),
            resolveDestination(signedIn, Route.Dashboard),
        )
    }

    @Test
    fun notFound_isShownWhicheverWayTheSessionWent() {
        // A wrong address should say it is wrong, not imply you need to sign in.
        listOf(AuthState.SignedOut, signedIn).forEach { auth ->
            assertEquals(
                Destination.Show(Route.NotFound),
                resolveDestination(auth, Route.NotFound),
                "auth $auth",
            )
        }
    }

    @Test
    fun aRedirectNeverPointsAtTheRouteItCameFrom() {
        // Guards that send you where you already are loop forever.
        allRoutes.forEach { route ->
            listOf(AuthState.SignedOut, signedIn).forEach { auth ->
                val destination = resolveDestination(auth, route)
                if (destination is Destination.Redirect) {
                    assertEquals(
                        Destination.Show(destination.to),
                        resolveDestination(auth, destination.to),
                        "redirect from $route under $auth must settle",
                    )
                }
            }
        }
    }
}
