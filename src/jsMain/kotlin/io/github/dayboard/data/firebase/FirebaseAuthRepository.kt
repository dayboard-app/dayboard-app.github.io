package io.github.dayboard.data.firebase

import io.github.dayboard.data.firebase.externals.FirebaseUser
import io.github.dayboard.data.firebase.externals.createUserWithEmailAndPassword
import io.github.dayboard.data.firebase.externals.onAuthStateChanged
import io.github.dayboard.data.firebase.externals.sendEmailVerification
import io.github.dayboard.data.firebase.externals.signInWithEmailAndPassword
import io.github.dayboard.data.firebase.externals.signOut
import io.github.dayboard.domain.model.AuthState
import io.github.dayboard.domain.model.AuthUser
import io.github.dayboard.domain.model.mayUseApp
import io.github.dayboard.domain.repository.AuthFailure
import io.github.dayboard.domain.repository.AuthRepository
import kotlinx.browser.window
import kotlinx.coroutines.await

/**
 * [AuthRepository] backed by Firebase Authentication.
 *
 * Names no UI: every failure leaves here as a code, and `presentation/AuthMessages`
 * turns it into a sentence. That is what stops a change to the wording from
 * touching this file.
 */
class FirebaseAuthRepository : AuthRepository {

    override fun observeSession(onChange: (AuthState) -> Unit) {
        onAuthStateChanged(Firebase.auth) { user ->
            when {
                user == null -> onChange(AuthState.SignedOut)

                user.toAuthUser().mayUseApp -> onChange(AuthState.SignedIn(user.toAuthUser()))

                // A restored session for an account that never confirmed its email.
                // Firebase is happy to keep it signed in; the app is not, and dropping
                // it here means no screen above ever has to repeat the check.
                else -> {
                    onChange(AuthState.SignedOut)
                    signOut(Firebase.auth)
                }
            }
        }
    }

    override suspend fun signIn(email: String, password: String) {
        val user = failingWithCode {
            signInWithEmailAndPassword(Firebase.auth, email, password).await().user
        }

        if (!user.toAuthUser().mayUseApp) {
            // Sign back out before reporting, so a rejected attempt cannot leave a
            // usable session behind for the next reload to pick up.
            failingWithCode { signOut(Firebase.auth).await() }
            throw AuthFailure(AuthFailure.EMAIL_NOT_VERIFIED)
        }
    }

    override suspend fun signUp(email: String, password: String) {
        val user = failingWithCode {
            createUserWithEmailAndPassword(Firebase.auth, email, password).await().user
        }

        // The link returns to this deployment rather than a hardcoded address, so a
        // local build sends people back to localhost and the live one to Pages.
        failingWithCode {
            sendEmailVerification(user, actionCodeSettings(window.location.origin)).await()
        }

        // Firebase signs a new account straight in. The app requires a confirmed
        // email, so the session is dropped and the caller tells them to check
        // their inbox - which is what the original does, having never signed them
        // in at all.
        failingWithCode { signOut(Firebase.auth).await() }
    }

    override suspend fun signOut() {
        failingWithCode { signOut(Firebase.auth).await() }
    }
}

private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
    uid = uid,
    // The SDK allows a user with no address; this app only creates email accounts,
    // so an empty one means something is wrong rather than that it is optional.
    email = email.orEmpty(),
    emailVerified = emailVerified,
)

/**
 * Runs a Firebase call and rethrows any failure as an [AuthFailure] carrying its code.
 *
 * Every path out of this repository goes through here, so no raw JS error and no
 * SDK type escapes into the layers above.
 */
private inline fun <T> failingWithCode(block: () -> T): T =
    try {
        block()
    } catch (error: Throwable) {
        throw AuthFailure(errorCodeOf(error))
    }
