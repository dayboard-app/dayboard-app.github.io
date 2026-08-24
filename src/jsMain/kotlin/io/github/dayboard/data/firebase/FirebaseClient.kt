package io.github.dayboard.data.firebase

import io.github.dayboard.data.firebase.externals.ActionCodeSettings
import io.github.dayboard.data.firebase.externals.Auth
import io.github.dayboard.data.firebase.externals.FirebaseApp
import io.github.dayboard.data.firebase.externals.FirebaseOptions
import io.github.dayboard.data.firebase.externals.Firestore
import io.github.dayboard.data.firebase.externals.getAuth
import io.github.dayboard.data.firebase.externals.getFirestore
import io.github.dayboard.data.firebase.externals.initializeApp
import io.github.dayboard.di.FirebaseConfig

/**
 * The one initialised Firebase app, and the services hanging off it.
 *
 * Both are lazy so that nothing touches the SDK until something actually needs an
 * account, and both are singletons because `initializeApp` throws if called twice
 * for the same name.
 */
object Firebase {

    private val app: FirebaseApp by lazy {
        initializeApp(
            firebaseOptions(
                apiKey = FirebaseConfig.API_KEY,
                authDomain = FirebaseConfig.AUTH_DOMAIN,
                projectId = FirebaseConfig.PROJECT_ID,
                storageBucket = FirebaseConfig.STORAGE_BUCKET,
                messagingSenderId = FirebaseConfig.MESSAGING_SENDER_ID,
                appId = FirebaseConfig.APP_ID,
            ),
        )
    }

    val auth: Auth by lazy { getAuth(app) }

    val firestore: Firestore by lazy { getFirestore(app) }
}

/**
 * Builds the config object Firebase expects.
 *
 * Kotlin has no JS object literal, so the shape is made by casting an empty one and
 * filling it in. This is why [FirebaseOptions] declares `var` properties.
 */
private fun firebaseOptions(
    apiKey: String,
    authDomain: String,
    projectId: String,
    storageBucket: String,
    messagingSenderId: String,
    appId: String,
): FirebaseOptions = js("{}").unsafeCast<FirebaseOptions>().apply {
    this.apiKey = apiKey
    this.authDomain = authDomain
    this.projectId = projectId
    this.storageBucket = storageBucket
    this.messagingSenderId = messagingSenderId
    this.appId = appId
}

/** Builds the settings that tell a confirmation link where to return to. */
internal fun actionCodeSettings(url: String): ActionCodeSettings =
    js("{}").unsafeCast<ActionCodeSettings>().apply { this.url = url }

/**
 * Digs the error code out of a rejected Firebase call.
 *
 * A `FirebaseError` is a JS `Error` subclass carrying an extra `code` property that
 * Kotlin's `Throwable` knows nothing about, so it has to be read dynamically. Any
 * other failure - a network stack throwing, a bug in this file - has no `code` and
 * comes back empty, which the message mapping renders as its generic sentence.
 */
internal fun errorCodeOf(error: Throwable): String =
    (error.asDynamic().code as? String).orEmpty()
