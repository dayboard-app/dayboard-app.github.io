package io.github.dayboard.presentation

/**
 * Which half of the sign-in page is showing.
 *
 * One page with two modes rather than two routes, matching the original: the form
 * is the same three controls either way, and switching should not cost a
 * navigation or clear what has been typed.
 *
 * The words live here rather than in the composable so that parity with the
 * original is a unit test rather than a careful reading of the markup.
 */
enum class AuthMode {

    SignIn {
        override val heading get() = "Welcome back"
        override val subtext get() = "Sign in to sync your tasks & timer"
        override val submitLabel get() = "Sign in"
        override val togglePrompt get() = "Don't have an account?"
        override val toggleAction get() = "Sign up"
    },

    SignUp {
        override val heading get() = "Create account"
        override val subtext get() = "Sign up to save your progress"
        override val submitLabel get() = "Sign up"
        override val togglePrompt get() = "Already have an account?"
        override val toggleAction get() = "Sign in"
    },
    ;

    abstract val heading: String
    abstract val subtext: String
    abstract val submitLabel: String

    /** The question above the mode switch, e.g. "Already have an account?". */
    abstract val togglePrompt: String

    /** The link-styled word that performs the switch, e.g. "Sign in". */
    abstract val toggleAction: String

    /** The other mode. Switching also clears any error or message on screen. */
    fun toggled(): AuthMode = if (this == SignIn) SignUp else SignIn

    companion object {
        /** The page opens on sign-in; signing up is the deliberate detour. */
        val Default: AuthMode = SignIn
    }
}
