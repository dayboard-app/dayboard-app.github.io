package io.github.dayboard.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dayboard.presentation.Route
import io.github.dayboard.presentation.hash
import io.github.dayboard.presentation.parseRoute
import kotlinx.browser.window

/**
 * The address bar, as observable state.
 *
 * Lives in `data` for the same reason [ThemeController] does: what it wraps is the
 * browser, not the program. The rule for turning an address into a [Route] is in
 * `:shared` and unit-tested; this only watches for changes and writes them back.
 */
class Router {

    var route: Route by mutableStateOf(parseRoute(window.location.hash))
        private set

    /**
     * Begins following the address bar.
     *
     * Without this, the back button would change the URL and leave the app showing
     * the previous screen.
     */
    fun start() {
        window.addEventListener("hashchange", {
            route = parseRoute(window.location.hash)
        })
    }

    /**
     * Goes to [to].
     *
     * [replace] rewrites the current history entry instead of adding one, which is
     * what a guard redirect wants: a signed-out user bounced off the dashboard
     * should not be able to press Back into the page that just rejected them.
     */
    fun navigate(to: Route, replace: Boolean = false) {
        if (route == to) return

        if (replace) {
            // replaceState fires no hashchange, so the listener will not see this
            // and the state is set here instead.
            window.history.replaceState(null, "", to.hash)
            route = to
        } else {
            // Assigning the hash does fire hashchange, so the listener picks it up
            // and there is exactly one place that writes `route` for a real navigation.
            window.location.hash = to.hash
        }
    }
}
