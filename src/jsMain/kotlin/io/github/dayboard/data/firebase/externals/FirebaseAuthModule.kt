@file:JsModule("firebase/auth")

package io.github.dayboard.data.firebase.externals

import kotlin.js.Promise

external fun getAuth(app: FirebaseApp): Auth

/**
 * Reports the session now and on every change.
 *
 * The callback receives `null` when nobody is signed in, including on the very
 * first call once Firebase has finished checking stored credentials. Getting that
 * nullability wrong is the classic bug in these externals: typed as non-null, a
 * signed-out user crashes the listener instead of reaching the sign-in page.
 *
 * Returns the unsubscribe function. It is a real callable rather than an opaque
 * handle, so a caller that outlives the listener can detach it.
 */
external fun onAuthStateChanged(auth: Auth, nextOrObserver: (user: FirebaseUser?) -> Unit): () -> Unit

external fun signInWithEmailAndPassword(
    auth: Auth,
    email: String,
    password: String,
): Promise<UserCredential>

external fun createUserWithEmailAndPassword(
    auth: Auth,
    email: String,
    password: String,
): Promise<UserCredential>

external fun signOut(auth: Auth): Promise<Unit>

/**
 * Sends the confirmation email.
 *
 * The second argument carries the address the link returns to. Its field is named
 * `url`, not `continueUrl` - the latter is only how it appears as a query parameter
 * on the generated link.
 */
external fun sendEmailVerification(
    user: FirebaseUser,
    actionCodeSettings: ActionCodeSettings = definedExternally,
): Promise<Unit>

/**
 * Points this Auth instance at the local emulator.
 *
 * Must be called before any other Auth operation, which is why it happens inside
 * the lazy that builds the instance rather than at some later start-up step.
 */
external fun connectAuthEmulator(auth: Auth, url: String, options: dynamic = definedExternally)
