package io.github.dayboard.ui

import androidx.compose.runtime.Composable
import io.github.bchmsl.keel.components.ButtonSize
import io.github.bchmsl.keel.components.ButtonVariant
import io.github.bchmsl.keel.components.Callout
import io.github.bchmsl.keel.components.CalloutTone
import io.github.bchmsl.keel.components.Surface
import io.github.bchmsl.keel.components.TextField
import io.github.bchmsl.keel.dom.buttonClasses
import io.github.bchmsl.keel.dom.classNames
import io.github.bchmsl.keel.icons.Icon
import io.github.bchmsl.keel.icons.LucideIcon
import io.github.dayboard.presentation.AuthMode
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.minLength
import org.jetbrains.compose.web.attributes.onSubmit
import org.jetbrains.compose.web.attributes.required
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Form
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
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
            Icon(LucideIcon.Timer, size = 24, className = "auth__brand-icon")
            Text("Dayboard")
        }

        // `elevated`, which is the shadow this card had written out by hand.
        Surface(elevated = true, attrs = { classNames("auth__card") }) {
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
                    Icon(LucideIcon.Mail, size = 16, className = "auth__field-icon")
                    TextField(
                        value = email,
                        onValueChange = onEmailChange,
                        placeholder = "Email",
                        type = InputType.Email,
                        attrs = {
                            classNames("auth__input")
                            required()
                        },
                    )
                }

                Div({ classes("auth__field") }) {
                    Icon(LucideIcon.Lock, size = 16, className = "auth__field-icon")
                    TextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        placeholder = "Password",
                        type = InputType.Password,
                        attrs = {
                            classNames("auth__input")
                            required()
                            // Firebase's own minimum, and the original's, so the browser
                            // rejects a too-short password before a request is made.
                            minLength(MIN_PASSWORD_LENGTH)
                        },
                    )
                }

                // keel's `Callout`, which names this exact case: something that went
                // wrong about the screen rather than about an action, and `announce`
                // for "a failed sign-in" in as many words. Both were a coloured line
                // of text, so both were silent - a screen reader reached them only if
                // focus happened to pass, which after a rejected password it does not.
                //
                // The tone tints the box and leaves the ink at `--foreground`, which
                // keel measured: `--destructive` ink on its own tint reaches 3.0:1 on
                // the light palettes, under the 4.5:1 this size needs. The old rule
                // was that failing pair, in all twelve themes.
                error?.let {
                    Callout(tone = CalloutTone.Destructive, announce = true) { Text(it) }
                }

                // `Success` and not `Primary`, because `--primary` is coral at hue 350
                // and ember at 25, either side of the destructive red at 0 - so on two
                // of the six palettes the confirmation would be the same red box as
                // the failure above it. Only one of the two is ever on screen, so
                // there is nothing beside it to tell them apart. This tone is the one
                // keel added for it.
                message?.let {
                    Callout(tone = CalloutTone.Success, announce = true) { Text(it) }
                }

                // A raw button rather than keel's `Button`, because the content is a
                // label with a trailing arrow that becomes a lone spinner mid-request,
                // and the composable has a leading slot but no trailing one. The classes
                // still come from keel, so a rename there fails to compile here.
                Button({
                    classNames(buttonClasses())
                    type(ButtonType.Submit)
                    if (submitting) disabled()
                }) {
                    if (submitting) {
                        // The label is replaced rather than joined by a spinner, so
                        // the button does not resize mid-request.
                        Icon(LucideIcon.LoaderCircle, size = 16, className = "spinner")
                    } else {
                        Text(mode.submitLabel)
                        Icon(LucideIcon.ArrowRight, size = 16)
                    }
                }
            }

            P({ classes("auth__toggle") }) {
                Text(mode.togglePrompt)
                // `Inline`, the one size that does not put a control's height into a
                // sentence. Raw for consistency with the submit button above it, which
                // has to be.
                Button({
                    classNames(
                        "auth__toggle-action",
                        buttonClasses(ButtonVariant.Link, ButtonSize.Inline),
                    )
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
