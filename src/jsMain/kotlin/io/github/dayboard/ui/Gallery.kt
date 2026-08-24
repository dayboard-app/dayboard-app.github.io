package io.github.dayboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.dayboard.data.ThemeController
import io.github.dayboard.domain.model.ColorMode
import io.github.dayboard.domain.model.ThemeId
import io.github.dayboard.ui.components.Button
import io.github.dayboard.ui.components.ButtonSize
import io.github.dayboard.ui.components.ButtonVariant
import io.github.dayboard.ui.components.Card
import io.github.dayboard.ui.components.Dialog
import io.github.dayboard.ui.components.IconButton
import io.github.dayboard.ui.components.Slider
import io.github.dayboard.ui.components.Switch
import io.github.dayboard.ui.components.TextAreaField
import io.github.dayboard.ui.components.TextField
import io.github.dayboard.ui.icons.Icon
import io.github.dayboard.ui.icons.LucideIcon
import org.jetbrains.compose.web.dom.Button as HtmlButton
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The design-system gallery: every base component, in whichever of the twelve
 * palettes is selected.
 *
 * It exists so the token set and the components can be checked against the
 * original side by side before any feature depends on them, and it is scaffolding.
 * The dashboard replaces it, and `gallery.css` goes with it.
 */
@Composable
fun Gallery(theme: ThemeController) {
    var switchOn by remember { mutableStateOf(true) }
    var sliderValue by remember { mutableStateOf(25) }
    var text by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var collapsed by remember { mutableStateOf(false) }
    var dialogOpen by remember { mutableStateOf(false) }

    Div({ classes("gallery") }) {
        Header(theme)

        Section("Buttons") {
            Div({ classes("row") }) {
                ButtonVariant.entries.forEach { variant ->
                    Button(variant.name, onClick = {}, variant = variant)
                }
            }
            Div({ classes("row") }) {
                ButtonSize.entries.filter { it != ButtonSize.Icon }.forEach { size ->
                    Button(size.name, onClick = {}, size = size)
                }
                IconButton("Add", onClick = {}, variant = ButtonVariant.Default) {
                    Icon(LucideIcon.Plus)
                }
                IconButton("Settings", onClick = {}, variant = ButtonVariant.Outline) {
                    Icon(LucideIcon.Settings)
                }
                Button("Disabled", onClick = {}, enabled = false)
            }
        }

        Section("Card") {
            Card(
                title = "Pomodoro",
                collapsed = collapsed,
                onToggleCollapsed = { collapsed = !collapsed },
                onToggleExpanded = {},
                centerContent = true,
            ) {
                Span({ classes("font-mono-timer", "gallery__timer", "timer-pulse") }) {
                    Text("25:00")
                }
                P({ classes("muted") }) { Text("Focus") }
            }
        }

        Section("Controls") {
            Div({ classes("row") }) {
                Switch(switchOn, { switchOn = it }, ariaLabel = "Demo toggle")
                Span({ classes("muted") }) { Text(if (switchOn) "On" else "Off") }
            }
            Div({ classes("row", "row--slider") }) {
                Slider(sliderValue, min = 1, max = 90, onValueChange = { sliderValue = it }, ariaLabel = "Focus")
                Span({ classes("muted", "tabular") }) { Text("$sliderValue min") }
            }
            TextField(text, { text = it }, placeholder = "Add a new task...", ariaLabel = "Task")
            TextAreaField(notes, { notes = it }, rows = 3, placeholder = "Add details or notes...", ariaLabel = "Notes")
        }

        Section("Dialog") {
            Button("Open dialog", onClick = { dialogOpen = true }, variant = ButtonVariant.Outline)
        }

        Section("Icons") {
            Div({ classes("icons") }) {
                LucideIcon.entries.forEach { icon ->
                    Div({ classes("icons__cell") }) {
                        Icon(icon, size = 20)
                        Span({ classes("icons__name") }) { Text(icon.name) }
                    }
                }
            }
        }

        Section("Tokens") {
            Div({ classes("swatches") }) {
                TOKEN_SWATCHES.forEach { token ->
                    Div({ classes("swatch") }) {
                        Div({
                            classes("swatch__chip")
                            style { property("background-color", "hsl(var(--$token))") }
                        })
                        Span({ classes("swatch__name") }) { Text(token) }
                    }
                }
            }
        }
    }

    if (dialogOpen) {
        Dialog(
            title = "Edit Task",
            description = "Edit task details, subtasks, and tags",
            onDismiss = { dialogOpen = false },
        ) {
            P { Text("Dismiss with the scrim or the close button.") }
        }
    }
}

/** Theme and mode pickers, so all twelve palettes are reachable from the page. */
@Composable
private fun Header(theme: ThemeController) {
    Div({ classes("gallery__header") }) {
        H1 { Text("Dayboard design system") }
        P({ classes("muted") }) {
            Text("Six accent themes, light and dark. Selection persists across reloads.")
        }

        Div({ classes("row") }) {
            ThemeId.entries.forEach { id ->
                HtmlButton({
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
                HtmlButton({
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
            Span({ classes("muted") }) {
                Text(if (theme.isDark) "rendering dark" else "rendering light")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Div({ classes("section") }) {
        H2 { Text(title) }
        content()
    }
}

private fun pickerClasses(selected: Boolean): Array<String> =
    listOfNotNull("picker", "picker--on".takeIf { selected }).toTypedArray()

/**
 * Every token a palette defines, so a missing or wrong value is visible rather
 * than merely wrong somewhere downstream.
 */
private val TOKEN_SWATCHES = listOf(
    "background", "foreground", "card", "popover", "primary", "primary-foreground",
    "secondary", "secondary-foreground", "muted", "muted-foreground", "accent",
    "accent-foreground", "destructive", "destructive-foreground", "border", "input", "ring",
)
