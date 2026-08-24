package io.github.dayboard.data.firebase

import io.github.dayboard.data.firebase.externals.DocumentReference
import io.github.dayboard.data.firebase.externals.DocumentSnapshot
import io.github.dayboard.data.firebase.externals.doc
import io.github.dayboard.data.firebase.externals.onSnapshot
import io.github.dayboard.data.firebase.externals.serverTimestamp
import io.github.dayboard.data.firebase.externals.setDoc
import io.github.dayboard.domain.model.StoredTimer
import io.github.dayboard.domain.model.TimerMode
import io.github.dayboard.domain.model.TimerState
import io.github.dayboard.domain.repository.TimerRepository
import kotlinx.coroutines.await

/**
 * [TimerRepository] backed by one Firestore document per account.
 *
 * The document sits beside the settings at `users/{uid}/state/timer`, inside the
 * subtree the security rules restrict to its owner.
 *
 * Kept separate from the settings document on purpose. The two are written at
 * completely different rates - settings when someone changes their mind, the timer
 * every time it is started, paused or skipped - and a single document would mean
 * every timer press racing every settings change for the same write.
 */
class FirestoreTimerRepository : TimerRepository {

    override fun observe(uid: String, onChange: (StoredTimer?) -> Unit): () -> Unit =
        onSnapshot(
            timerDoc(uid),
            { snapshot ->
                // This device's own writes come back through its own listener before
                // the server has confirmed them. The caller already applied that
                // change, and applying it again would undo whatever came after -
                // most visibly, it would drag a running countdown backwards.
                if (!snapshot.metadata.hasPendingWrites) {
                    onChange(readTimer(snapshot))
                }
            },
            { error -> console.warn("timer listener failed", error) },
        )

    override suspend fun save(uid: String, state: TimerState, lastTickAtMillis: Long?) {
        setDoc(timerDoc(uid), timerDocument(state, lastTickAtMillis), mergeOption()).await()
    }
}

private fun timerDoc(uid: String): DocumentReference =
    doc(Firebase.firestore, "users", uid, "state", "timer")

/** Reads the stored timer, or null when this account has never run one. */
private fun readTimer(snapshot: DocumentSnapshot): StoredTimer? {
    if (!snapshot.exists()) return null

    val data = snapshot.data()

    return StoredTimer(
        mode = TimerMode.fromId(data["mode"] as? String),
        secondsLeft = (data["secondsLeft"] as? Number)?.toInt() ?: 0,
        running = data["running"] as? Boolean ?: false,
        completedSessions = (data["completedSessions"] as? Number)?.toInt() ?: 0,
        // Stored as a plain number of milliseconds rather than a Firestore
        // Timestamp, because it is arithmetic rather than a date: the only thing
        // ever done with it is subtracting it from now.
        lastTickAtMillis = (data["lastTickAtMillis"] as? Number)?.toDouble()?.toLong(),
    )
}

private fun timerDocument(state: TimerState, lastTickAtMillis: Long?): dynamic {
    val document = jsObject()
    document["mode"] = state.mode.id
    document["secondsLeft"] = state.secondsLeft
    document["running"] = state.running
    document["completedSessions"] = state.completedSessions
    // A Long is not a JS number. Firestore would store it as an opaque object that
    // reads back as null, and the countdown would silently stop surviving reloads.
    document["lastTickAtMillis"] = lastTickAtMillis?.toDouble()
    // Not read by anything; it is here so a human looking at the document can tell
    // when it was last touched, and by whose clock.
    document["updatedAt"] = serverTimestamp()
    return document
}

private fun mergeOption(): dynamic {
    val options = jsObject()
    options["merge"] = true
    return options
}
