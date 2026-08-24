package io.github.dayboard.presentation

import io.github.dayboard.domain.model.AuthState

/**
 * What the app should do with the address currently in the URL bar.
 *
 * Separating this from [Route] keeps the guard honest: a route is where the user
 * asked to go, a destination is where the session lets them go.
 */
sealed interface Destination {

    /**
     * The session is not known yet. Paint the background and nothing else.
     *
     * Deliberately not a redirect: rewriting the URL before Firebase has reported
     * would throw away the address a returning user arrived on.
     */
    data object Pending : Destination

    /** Render this route. */
    data class Show(val route: Route) : Destination

    /** The address disagrees with the session; send the user here instead. */
    data class Redirect(val to: Route) : Destination
}

/**
 * Applies the route guard.
 *
 * Three rules, matching the original:
 *  - while the session is resolving, show nothing;
 *  - signed out, the dashboard sends you to sign in;
 *  - signed in, the sign-in page sends you to the dashboard.
 *
 * [Route.NotFound] is deliberately outside the guard, as in the original, where the
 * catch-all route sits outside the protected wrapper. A wrong address should say so
 * rather than pretending the problem is that you are signed out.
 */
fun resolveDestination(auth: AuthState, route: Route): Destination = when (auth) {
    AuthState.Loading -> Destination.Pending

    AuthState.SignedOut -> when (route) {
        Route.Dashboard -> Destination.Redirect(Route.Auth)
        Route.Auth, Route.NotFound -> Destination.Show(route)
    }

    is AuthState.SignedIn -> when (route) {
        Route.Auth -> Destination.Redirect(Route.Dashboard)
        Route.Dashboard, Route.NotFound -> Destination.Show(route)
    }
}
