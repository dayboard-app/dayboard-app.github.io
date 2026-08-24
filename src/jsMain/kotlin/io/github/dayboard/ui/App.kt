package io.github.dayboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.dayboard.data.AuthController
import io.github.dayboard.data.Router
import io.github.dayboard.data.ThemeController
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
fun App(auth: AuthController, router: Router, theme: ThemeController) {
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
                DashboardPlaceholder(
                    email = signedIn?.user?.email.orEmpty(),
                    theme = theme,
                    onSignOut = auth::signOut,
                )
            }

            Route.NotFound -> NotFoundScreen(onHome = { router.navigate(Route.Dashboard) })
        }
    }
}
