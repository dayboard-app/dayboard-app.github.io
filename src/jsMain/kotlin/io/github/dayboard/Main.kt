package io.github.dayboard

import io.github.dayboard.data.AuthController
import io.github.dayboard.data.ClockController
import io.github.dayboard.data.DragController
import io.github.dayboard.data.Router
import io.github.dayboard.data.SettingsController
import io.github.dayboard.data.ThemeController
import io.github.dayboard.data.WeatherController
import io.github.dayboard.data.firebase.FirebaseAuthRepository
import io.github.dayboard.data.firebase.FirestoreSettingsRepository
import io.github.dayboard.data.weather.OpenMeteoWeatherRepository
import io.github.dayboard.ui.App
import kotlinx.coroutines.MainScope
import org.jetbrains.compose.web.renderComposable

/**
 * Entry point, and the only place that builds anything.
 *
 * Everything below is handed what it needs: this function constructs the graph,
 * starts the pieces that watch the browser, and gives the whole thing to the
 * composition. Nothing else calls a constructor.
 *
 * The theme, the router and the session all start before the first composition so
 * the palette, the address and the account are settled before anything paints.
 * Settings start later, because they belong to an account rather than to the page.
 */
fun main() {
    // Outlives every screen: a write that is still in flight when the route changes
    // must still finish.
    val scope = MainScope()

    val theme = ThemeController()
    val router = Router()
    val auth = AuthController(repository = FirebaseAuthRepository(), scope = scope)
    val settings = SettingsController(repository = FirestoreSettingsRepository(), scope = scope)
    val clock = ClockController(scope = scope)
    val weather = WeatherController(repository = OpenMeteoWeatherRepository(), scope = scope)
    val drag = DragController()

    theme.start()
    router.start()
    auth.start()
    // The clock belongs to the page rather than to an account, so it ticks from the
    // start. The weather does not: it waits for the settings that say whether it is
    // wanted at all, and the board starts it.
    clock.start()

    renderComposable(rootElementId = "root") {
        App(
            auth = auth,
            router = router,
            settings = settings,
            clock = clock,
            weather = weather,
            drag = drag,
        )
    }
}
