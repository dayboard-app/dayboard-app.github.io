@file:JsModule("firebase/firestore")

package io.github.dayboard.data.firebase.externals

import kotlin.js.Promise

external fun getFirestore(app: FirebaseApp): Firestore

/**
 * A document reference, built from a path.
 *
 * The trailing segments are separate arguments rather than one slash-joined string
 * so a segment containing a slash cannot silently become two.
 */
external fun doc(db: Firestore, path: String, vararg pathSegments: String): DocumentReference

/**
 * Live document listener. Returns the unsubscribe function.
 *
 * Typed as a callable rather than an opaque handle because it genuinely has to be
 * called: the listener outlives a sign-out otherwise, and keeps reading a document
 * the next account has no right to.
 */
external fun onSnapshot(
    reference: DocumentReference,
    onNext: (DocumentSnapshot) -> Unit,
    onError: (dynamic) -> Unit = definedExternally,
): () -> Unit

external fun setDoc(
    reference: DocumentReference,
    data: dynamic,
    options: dynamic = definedExternally,
): Promise<Unit>

/** The server's clock, not the device's, for `updatedAt`. */
external fun serverTimestamp(): dynamic

/** Points this Firestore instance at the local emulator. Call before any read or write. */
external fun connectFirestoreEmulator(firestore: Firestore, host: String, port: Int)
