package io.github.dayboard.domain.model

/**
 * The signed-in account, reduced to what the app actually uses.
 *
 * Deliberately not the Firebase user object: that type belongs to the browser SDK
 * and would drag it into every layer that wanted an email address.
 */
data class AuthUser(
    val uid: String,
    val email: String,
    val emailVerified: Boolean,
)

/**
 * Whether an account may use the app.
 *
 * The original is built on Supabase, which by default refuses to sign in an
 * account whose email has not been confirmed. Firebase does the opposite: it signs
 * unverified accounts in and leaves the decision to the app. This is that
 * decision, so the two behave the same from the outside.
 *
 * It has to hold for restored sessions as well as fresh sign-ins - someone who
 * signs up, closes the tab and returns still has a persisted Firebase session -
 * which is why it is a property of the user rather than a step in the sign-in flow.
 */
val AuthUser.mayUseApp: Boolean get() = emailVerified

/**
 * Whether a session is known yet, and if so whose.
 *
 * [Loading] is a real state rather than a null user: on boot Firebase has not yet
 * told us whether a session was restored, and treating "not known yet" as "signed
 * out" would bounce a returning user to the sign-in page for a frame.
 *
 * [SignedIn] always means an account that [mayUseApp]; an unverified one is signed
 * back out before it reaches here.
 */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AuthUser) : AuthState
}
