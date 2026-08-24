package io.github.dayboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.dayboard.data.AuthController
import io.github.dayboard.data.ClockController
import io.github.dayboard.data.DragController
import io.github.dayboard.data.Router
import io.github.dayboard.data.SettingsController
import io.github.dayboard.data.TimerController
import io.github.dayboard.data.WeatherController
import io.github.dayboard.domain.model.AuthState
import io.github.dayboard.presentation.Destination
import io.github.dayboard.presentation.Route
import io.github.dayboard.presentation.resolveDestination

/**
 * The whole app: decides which screen the address and the session add up to.
 *
 * The decision itself is [resolveDestination] in `:shared`, which is unit-tested.
 * All this does is act on the answer, so the guard cannot be quietly rewritten by
 * a change to a screen.
 */
@Composable
fun App(
    auth: AuthController,
    router: Router,
    settings: SettingsController,
    clock: ClockController,
    weather: WeatherController,
    timer: TimerController,
    drag: DragController,
) {
    // The settings document belongs to an account, so the listener follows the
    // session rather than the screen: attaching it in the dashboard would drop it
    // every time the user looked at another route.
    LaunchedEffect(auth.state) {
        when (val state = auth.state) {
            is AuthState.SignedIn -> settings.start(state.user.uid)
            AuthState.SignedOut -> {
                settings.stop()
                timer.stop()
            }
            AuthState.Loading -> Unit
        }
    }

    // The timer waits for the settings as well as the account, because a timer with
    // nothing stored starts at the configured focus duration: attaching first would
    // show the default length and correct it a moment later, in full view.
    //
    // Re-run on every settings change rather than only on the first, so that
    // shortening a stretch reaches a timer already counting one down.
    val account = (auth.state as? AuthState.SignedIn)?.user?.uid
    LaunchedEffect(account, settings.loaded, settings.settings) {
        if (account != null && settings.loaded) {
            timer.follow(account, settings.settings)
        }
    }

    when (val destination = resolveDestination(auth.state, router.route)) {
        Destination.Pending -> PendingScreen()

        is Destination.Redirect -> {
            // In an effect, not during composition: navigating writes to the router's
            // state, and a composable that changes state while composing is a loop.
            LaunchedEffect(destination.to) {
                router.navigate(destination.to, replace = true)
            }
            PendingScreen()
        }

        is Destination.Show -> when (destination.route) {
            Route.Auth -> AuthScreen(
                mode = auth.mode,
                email = auth.email,
                password = auth.password,
                submitting = auth.submitting,
                error = auth.error,
                message = auth.message,
                onEmailChange = auth::setEmail,
                onPasswordChange = auth::setPassword,
                onToggleMode = auth::toggleMode,
                onSubmit = auth::submit,
            )

            Route.Dashboard -> {
                val signedIn = auth.state as? AuthState.SignedIn
                // The board waits for the stored layout rather than drawing the
                // default and rearranging itself a moment later.
                if (signedIn == null || !settings.loaded) {
                    PendingScreen()
                } else {
                    Dashboard(
                        email = signedIn.user.email,
                        settings = settings,
                        clock = clock,
                        weather = weather,
                        timer = timer,
                        drag = drag,
                        onSignOut = auth::signOut,
                    )
                }
            }

            Route.NotFound -> NotFoundScreen(onHome = { router.navigate(Route.Dashboard) })
        }
    }
}
