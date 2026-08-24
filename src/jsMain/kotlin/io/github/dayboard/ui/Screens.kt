package io.github.dayboard.ui

import androidx.compose.runtime.Composable
import io.github.dayboard.data.ThemeController
import io.github.dayboard.domain.model.ColorMode
import io.github.dayboard.domain.model.ThemeId
import io.github.dayboard.ui.icons.Icon
import io.github.dayboard.ui.icons.LucideIcon
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Stands in for the dashboard until the card board exists.
 *
 * Deliberately minimal, but not empty: it shows the signed-in address and offers
 * sign-out, which is what makes the whole authentication round trip demonstrable
 * on the deployed site. The theme pickers stay reachable so the palettes can still
 * be checked while the gallery is gone.
 */
@Composable
fun DashboardPlaceholder(
    email: String,
    theme: ThemeController,
    onSignOut: () -> Unit,
) {
    Div({ classes("placeholder") }) {
        Div({ classes("placeholder__brand") }) {
            Icon(LucideIcon.Timer, size = 24)
            Text("Dayboard")
        }

        P({ classes("placeholder__email") }) { Text(email) }
        P({ classes("muted") }) { Text("Signed in. The board arrives in the next phase.") }

        Div({ classes("row") }) {
            ThemeId.entries.forEach { id ->
                Button({
                    classes(*pickerClasses(id == theme.themeId))
                    onClick { theme.setThemeId(id) }
                }) {
                    Span({
                        classes("picker__dot")
                        style { property("background-color", id.accentHex) }
                    })
                    Text(id.label)
                }
            }
        }

        Div({ classes("row") }) {
            ColorMode.entries.forEach { mode ->
                Button({
                    classes(*pickerClasses(mode == theme.colorMode))
                    onClick { theme.setColorMode(mode) }
                }) {
                    Icon(
                        when (mode) {
                            ColorMode.Light -> LucideIcon.Sun
                            ColorMode.Dark -> LucideIcon.Moon
                            ColorMode.System -> LucideIcon.Monitor
                        },
                        size = 14,
                    )
                    Text(mode.label)
                }
            }
        }

        Button({
            classes("placeholder__sign-out")
            onClick { onSignOut() }
        }) {
            Icon(LucideIcon.LogOut, size = 16)
            Text("Sign out")
        }
    }
}

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

private fun pickerClasses(selected: Boolean): Array<String> =
    listOfNotNull("picker", "picker--on".takeIf { selected }).toTypedArray()
