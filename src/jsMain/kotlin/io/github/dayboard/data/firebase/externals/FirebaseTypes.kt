package io.github.dayboard.data.firebase.externals

/**
 * Structural types for the Firebase JS SDK (v12, modular).
 *
 * Pure shapes with no `@JsModule`, so they generate no imports of their own. The
 * functions that must actually be imported live in the sibling `Firebase*Module.kt`
 * files, which can hold nothing else.
 *
 * The `var` / `val` split is not cosmetic: `var` marks the objects the app builds
 * and hands to Firebase, `val` marks the ones Firebase hands back.
 */

external interface FirebaseApp

/** Built by the app, so every field is a `var`. Firebase treats all of them as optional. */
external interface FirebaseOptions {
    var apiKey: String
    var authDomain: String
    var projectId: String
    var storageBucket: String
    var messagingSenderId: String
    var appId: String
}

external interface Auth {
    val currentUser: FirebaseUser?
}

external interface FirebaseUser {
    /** Always present. */
    val uid: String

    /**
     * Nullable in the SDK, because an account can exist without one - an anonymous
     * or phone sign-in. This app only ever creates email accounts, but the type
     * tells the truth rather than what is convenient.
     */
    val email: String?

    val emailVerified: Boolean
}

external interface UserCredential {
    /** There is no flattened `uid` or `email` here; both are reached through this. */
    val user: FirebaseUser
}

/** Where the confirmation link returns to. `url` is the only required field. */
external interface ActionCodeSettings {
    var url: String
}
