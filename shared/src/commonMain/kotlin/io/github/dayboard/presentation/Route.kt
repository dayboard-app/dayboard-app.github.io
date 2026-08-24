package io.github.dayboard.presentation

/**
 * The screens the app can address by URL.
 *
 * Routes live in the URL hash rather than the path. GitHub Pages serves static
 * files with no rewrite rules, so a path route such as `/auth` would return a 404
 * on a hard refresh or a shared link; the hash keeps every route inside the one
 * `index.html` the server knows about.
 */
sealed interface Route {

    /** The card dashboard. Reachable only with a signed-in user. */
    data object Dashboard : Route

    /** Sign-in and sign-up. */
    data object Auth : Route

    /** Any address that matches no known route. */
    data object NotFound : Route
}

/**
 * Resolves a `window.location.hash` to the route it addresses.
 *
 * Tolerates the shapes a browser actually produces: the hash arrives with or
 * without its leading `#`, a visitor to the bare domain gets an empty string, and
 * a link may or may not carry a trailing slash. A query string is ignored so that
 * a redirect target like `#/auth?from=%2F` still resolves to [Route.Auth].
 *
 * Matching is exact and case-sensitive past that normalisation: `#/auth/extra`
 * and `#/AUTH` are both [Route.NotFound] rather than silently reaching a screen.
 */
fun parseRoute(hash: String): Route {
    val path = hash
        .removePrefix("#")
        .substringBefore('?')
        .trim('/')

    return when (path) {
        "" -> Route.Dashboard
        AUTH_PATH -> Route.Auth
        else -> Route.NotFound
    }
}

/**
 * The hash each route is written as, for building links and for
 * `window.location.hash` assignments.
 */
val Route.hash: String
    get() = when (this) {
        Route.Dashboard -> "#/"
        Route.Auth -> "#/$AUTH_PATH"
        // NotFound is a state the app falls into, never one it navigates to, so it
        // has no address of its own and reports the one that shows the dashboard.
        Route.NotFound -> "#/"
    }

private const val AUTH_PATH = "auth"
