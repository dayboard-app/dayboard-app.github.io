package io.github.dayboard

import io.github.dayboard.data.ThemeController
import io.github.dayboard.ui.Gallery
import org.jetbrains.compose.web.renderComposable

/**
 * Entry point, and the only place that builds anything.
 *
 * The theme is started before the first composition so the palette is settled
 * before anything paints, and it is passed down rather than reached for globally.
 *
 * The page is still the design-system gallery; the dashboard replaces it once the
 * board and the cards exist.
 */
fun main() {
    val theme = ThemeController()
    theme.start()

    renderComposable(rootElementId = "root") {
        Gallery(theme)
    }
}
