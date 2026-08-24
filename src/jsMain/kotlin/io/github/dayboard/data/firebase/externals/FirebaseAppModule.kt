@file:JsModule("firebase/app")

package io.github.dayboard.data.firebase.externals

/**
 * The `firebase/app` entry point.
 *
 * A `@file:JsModule` file may contain nothing but external declarations, which is
 * why the shapes these use live in `FirebaseTypes.kt` and the helpers that build
 * them live in `FirebaseClient.kt`.
 */
external fun initializeApp(options: FirebaseOptions): FirebaseApp
