package io.github.dayboard.ui

import androidx.compose.runtime.Composable
import io.github.dayboard.presentation.AuthMode
import io.github.dayboard.ui.icons.Icon
import io.github.dayboard.ui.icons.LucideIcon
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.minLength
import org.jetbrains.compose.web.attributes.onSubmit
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.required
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Form
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Sign in and sign up.
 *
 * Stateless on purpose: everything it shows is passed in, and everything it does
 * is a callback. That keeps the screen renderable in any state - mid-request, with
 * an error, with a confirmation message - without a Firebase project behind it.
 */
@Composable
fun AuthScreen(
    mode: AuthMode,
    email: String,
    password: String,
    submitting: Boolean,
    error: String?,
    message: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleMode: () -> Unit,
    onSubmit: () -> Unit,
) {
    Div({ classes("auth") }) {
        Div({ classes("auth__brand") }) {
            Icon(LucideIcon.Timer, size = 24)
            Text("Dayboard")
        }

        Div({ classes("auth__card") }) {
            H1({ classes("auth__heading") }) { Text(mode.heading) }
            P({ classes("auth__subtext") }) { Text(mode.subtext) }

            Form(attrs = {
                classes("auth__form")
                // A real form, so Enter submits from either field and the browser
                // runs its own validation first. preventDefault stops the navigation
                // that submitting would otherwise cause.
                //
                // Qualified with `this` because the `onSubmit` parameter of this
                // composable would otherwise shadow the attribute of the same name.
                this.onSubmit { event ->
                    event.preventDefault()
                    onSubmit()
                }
            }) {
                Div({ classes("auth__field") }) {
                    Icon(LucideIcon.Mail, size = 16)
                    Input(InputType.Email) {
                        classes("auth__input")
                        placeholder("Email")
                        required()
                        value(email)
                        onInput { onEmailChange(it.value) }
                    }
                }

                Div({ classes("auth__field") }) {
                    Icon(LucideIcon.Lock, size = 16)
                    Input(InputType.Password) {
                        classes("auth__input")
                        placeholder("Password")
                        required()
                        // Firebase's own minimum, and the original's, so the browser
                        // rejects a too-short password before a request is made.
                        minLength(MIN_PASSWORD_LENGTH)
                        value(password)
                        onInput { onPasswordChange(it.value) }
                    }
                }

                error?.let { P({ classes("auth__error") }) { Text(it) } }
                message?.let { P({ classes("auth__message") }) { Text(it) } }

                Button({
                    classes("auth__submit")
                    type(ButtonType.Submit)
                    if (submitting) disabled()
                }) {
                    if (submitting) {
                        // The label is replaced rather than joined by a spinner, so
                        // the button does not resize mid-request.
                        Icon(LucideIcon.LoaderCircle, size = 16, className = "auth__spinner")
                    } else {
                        Text(mode.submitLabel)
                        Icon(LucideIcon.ArrowRight, size = 16)
                    }
                }
            }

            P({ classes("auth__toggle") }) {
                Text(mode.togglePrompt)
                Button({
                    classes("auth__toggle-action")
                    type(ButtonType.Button)
                    onClick { onToggleMode() }
                }) {
                    Text(mode.toggleAction)
                }
            }
        }
    }
}

/**
 * The blank screen shown while Firebase decides whether a session was restored.
 *
 * Deliberately empty rather than a spinner: the wait is normally a few frames, and
 * the original shows the bare background too. A spinner that flashes for 50ms reads
 * as a glitch.
 */
@Composable
fun PendingScreen() {
    Div({ classes("pending") })
}

private const val MIN_PASSWORD_LENGTH = 6
