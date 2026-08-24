package io.github.dayboard

import androidx.compose.runtime.Composable
import io.github.dayboard.presentation.parseRoute
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.renderComposable

/**
 * Entry point.
 *
 * Still a placeholder: it proves the toolchain end to end, that the bundle boots
 * on GitHub Pages and that `:shared` is linked into it. The real composition root
 * that builds the dependency graph arrives with authentication.
 */
fun main() {
    renderComposable(rootElementId = "root") {
        Placeholder()
    }
}

/**
 * The holding page.
 *
 * Reads the route through [parseRoute] rather than hardcoding a string, so a
 * deployed build demonstrates that the `:shared` module compiled, linked and runs
 * in the browser. Visiting `#/auth` changes what this renders.
 */
@Composable
private fun Placeholder() {
    val route = parseRoute(window.location.hash)

    Div({ classes("shell") }) {
        H1 { Text("Dayboard") }
        P({ classes("lead") }) {
            Text("Pomodoro timer, tasks, and notes in one board.")
        }
        P({ classes("status") }) {
            Text("Deploy pipeline is live. Route resolves to ")
            Span({ classes("route") }) { Text(route.toString()) }
            Text(".")
        }
    }
}
