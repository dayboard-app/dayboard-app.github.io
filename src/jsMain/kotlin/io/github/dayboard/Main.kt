package io.github.dayboard

import io.github.dayboard.data.AuthController
import io.github.dayboard.data.Router
import io.github.dayboard.data.ThemeController
import io.github.dayboard.data.firebase.FirebaseAuthRepository
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
 * The three `start()` calls happen before the first composition so the palette,
 * the address and the session are all settled before anything paints. Without
 * that, a signed-in user would see the sign-in page for a frame.
 */
fun main() {
    val theme = ThemeController()
    val router = Router()
    val auth = AuthController(
        repository = FirebaseAuthRepository(),
        // Outlives every screen: a sign-in that is still in flight when the route
        // changes must still finish and report.
        scope = MainScope(),
    )

    theme.start()
    router.start()
    auth.start()

    renderComposable(rootElementId = "root") {
        App(auth = auth, router = router, theme = theme)
    }
}
