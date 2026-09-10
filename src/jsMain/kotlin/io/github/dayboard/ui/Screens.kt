package io.github.dayboard.ui

import androidx.compose.runtime.Composable
import io.github.bchmsl.keel.components.Button
import io.github.bchmsl.keel.components.ButtonSize
import io.github.bchmsl.keel.components.ButtonVariant
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * An address that matches no route.
 *
 * Outside the session guard, as in the original: a wrong address should say it is
 * wrong rather than implying the problem is that you are not signed in.
 */
@Composable
fun NotFoundScreen(onHome: () -> Unit) {
    Div({ classes("notfound") }) {
        H1({ classes("notfound__code") }) { Text("404") }
        P({ classes("notfound__text") }) { Text("Oops! Page not found") }
        // A `Button` and not a `LinkButton`: this goes through the app's own router
        // rather than following an href. `Inline` for the size, because it is a line
        // of text under two others rather than a control on its own row.
        Button(
            label = "Return to Home",
            onClick = onHome,
            variant = ButtonVariant.Link,
            size = ButtonSize.Inline,
        )
    }
}
