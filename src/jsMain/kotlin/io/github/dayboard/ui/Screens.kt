package io.github.dayboard.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Button
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
        Button({
            classes("notfound__home")
            onClick { onHome() }
        }) {
            Text("Return to Home")
        }
    }
}

