package io.github.dayboard.domain.repository

import io.github.dayboard.domain.model.AuthState

/**
 * The account, as the rest of the app sees it.
 *
 * Names no vendor: the implementation is Firebase, but nothing above this
 * interface knows that, which is what keeps `domain` compilable and testable
 * without a browser.
 *
 * Every method throws [AuthFailure] rather than returning a result type, because
 * a failure here is always exceptional - the user asked to sign in and it did not
 * happen - and the caller is a screen that shows one sentence either way.
 */
interface AuthRepository {

    /**
     * Reports the session, now and whenever it changes.
     *
     * Calls back with [AuthState.Loading] until Firebase has said whether a stored
     * session was restored. An account that has not confirmed its email is never
     * reported as signed in: it is signed back out and reported as
     * [AuthState.SignedOut], so callers cannot forget the check.
     */
    fun observeSession(onChange: (AuthState) -> Unit)

    /**
     * Signs in.
     *
     * @throws AuthFailure with [AuthFailure.EMAIL_NOT_VERIFIED] when the account
     *   exists but has not confirmed its email, after signing it back out.
     */
    suspend fun signIn(email: String, password: String)

    /**
     * Registers an account and sends its confirmation email.
     *
     * Leaves nobody signed in. Firebase signs a new account in immediately, but
     * the app requires a confirmed email, so the session is dropped and the caller
     * tells the user to go and check their inbox.
     */
    suspend fun signUp(email: String, password: String)

    suspend fun signOut()
}

/**
 * Something went wrong with an account operation.
 *
 * [code] is the identifier the SDK gave, such as `auth/invalid-credential`, or one
 * of the app's own codes below. It is deliberately not a sentence: the domain does
 * not know what the reader speaks, and turning a code into words is
 * `presentation/AuthMessages`.
 */
class AuthFailure(val code: String) : RuntimeException("auth failed: $code") {

    companion object {

        /**
         * The app's own code for the email-confirmation gate.
         *
         * Namespaced `dayboard/` so it cannot collide with an SDK code, and shaped
         * like one so it travels the same path to the same mapping instead of
         * needing a second kind of failure.
         */
        const val EMAIL_NOT_VERIFIED: String = "dayboard/email-not-verified"

        /** Used when a failure arrives carrying no code at all. */
        const val UNKNOWN: String = ""
    }
}
