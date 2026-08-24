package io.github.dayboard.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dayboard.domain.model.AuthState
import io.github.dayboard.domain.repository.AuthFailure
import io.github.dayboard.domain.repository.AuthRepository
import io.github.dayboard.presentation.AuthMessages
import io.github.dayboard.presentation.AuthMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The sign-in page's state, and the session behind it.
 *
 * Holds the form as well as the account because the two are one conversation: a
 * failed sign-in has to leave the typed email in place, and a successful one has to
 * clear the form's error before the screen disappears.
 */
class AuthController(
    private val repository: AuthRepository,
    private val scope: CoroutineScope,
) {

    var state: AuthState by mutableStateOf(AuthState.Loading)
        private set

    var mode: AuthMode by mutableStateOf(AuthMode.Default)
        private set

    var email: String by mutableStateOf("")
        private set

    var password: String by mutableStateOf("")
        private set

    var submitting: Boolean by mutableStateOf(false)
        private set

    var error: String? by mutableStateOf(null)
        private set

    var message: String? by mutableStateOf(null)
        private set

    /** Begins following the session. Call once, before the first composition. */
    fun start() {
        repository.observeSession { state = it }
    }

    fun setEmail(value: String) {
        email = value
    }

    fun setPassword(value: String) {
        password = value
    }

    /**
     * Switches between signing in and signing up.
     *
     * Clears both the error and the message, because neither is about the mode
     * being switched to: an "invalid credentials" left over from a sign-in attempt
     * would read as a comment on the sign-up form.
     */
    fun toggleMode() {
        mode = mode.toggled()
        error = null
        message = null
    }

    /**
     * Submits the form.
     *
     * A successful sign-in does not navigate: the session listener notices and the
     * route guard moves the user. That keeps one path into the dashboard rather
     * than two that could disagree.
     */
    fun submit() {
        if (submitting) return

        submitting = true
        error = null
        message = null

        scope.launch {
            try {
                when (mode) {
                    AuthMode.SignIn -> repository.signIn(email, password)
                    AuthMode.SignUp -> {
                        repository.signUp(email, password)
                        message = AuthMessages.CONFIRMATION_SENT
                    }
                }
            } catch (failure: AuthFailure) {
                error = AuthMessages.forCode(failure.code)
            } finally {
                submitting = false
            }
        }
    }

    /**
     * Signs out and returns the form to its opening state.
     *
     * Without the reset, signing out and back in would show the previous session's
     * email already filled in.
     */
    fun signOut() {
        scope.launch {
            try {
                repository.signOut()
            } catch (failure: AuthFailure) {
                error = AuthMessages.forCode(failure.code)
            }
            mode = AuthMode.Default
            email = ""
            password = ""
            message = null
        }
    }
}
