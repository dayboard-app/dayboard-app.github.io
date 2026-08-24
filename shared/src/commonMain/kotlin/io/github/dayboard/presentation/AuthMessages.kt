package io.github.dayboard.presentation

import io.github.dayboard.domain.repository.AuthFailure

/**
 * Account failures turned into words.
 *
 * The only place that knows both what can go wrong and how to say it. It lives in
 * `presentation` because that is exactly the boundary: the repository throws a
 * code, the screen shows a sentence. Building the sentence in the repository would
 * put user-facing English in the layer that talks to Firebase.
 *
 * Codes are matched with `contains` so the `auth/` prefix is tolerated either way,
 * and so a code that arrives wrapped in a longer string still lands.
 */
object AuthMessages {

    /**
     * What to show a user whose sign-in or sign-up failed.
     *
     * The three credential codes collapse into one sentence on purpose. Which of
     * them Firebase sends depends on whether the project has email-enumeration
     * protection switched on, and telling a stranger whether an address is
     * registered is exactly what that protection exists to prevent - so the app
     * must not undo it by wording the two cases differently.
     */
    fun forCode(code: String): String = when {
        code.contains(AuthFailure.EMAIL_NOT_VERIFIED) -> EMAIL_NOT_VERIFIED

        code.contains("invalid-credential") ||
            code.contains("wrong-password") ||
            code.contains("user-not-found") -> "Invalid login credentials."

        code.contains("invalid-email") -> "That email address is not valid."
        code.contains("email-already-in-use") -> "That email is already registered."
        code.contains("weak-password") -> "Password must be at least 6 characters."
        code.contains("too-many-requests") -> "Too many attempts. Try again in a moment."
        code.contains("network-request-failed") -> "Could not reach the network."
        code.contains("user-disabled") -> "That account has been disabled."

        // The two configuration mistakes that look like app bugs from the outside.
        // Both are one switch in the Firebase console, so the message says which.
        code.contains("unauthorized-domain") ->
            "This site is not on the Firebase authorized-domain list."
        code.contains("operation-not-allowed") ->
            "Email and password sign-in is not enabled for this project."

        else -> "Something went wrong. Please try again."
    }

    /**
     * Shown when an account exists but its email has never been confirmed.
     *
     * Firebase would happily sign such an account in; the app refuses, to match the
     * original, whose backend blocks it. See `AuthUser.mayUseApp`.
     */
    const val EMAIL_NOT_VERIFIED: String =
        "Please confirm your email address first, then sign in."

    /** Shown after a successful sign-up, while the confirmation email is on its way. */
    const val CONFIRMATION_SENT: String = "Check your email for a confirmation link!"
}
