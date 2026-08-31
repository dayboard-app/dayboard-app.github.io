package io.github.dayboard.data.firebase

import io.github.dayboard.data.firebase.externals.DocumentReference
import io.github.dayboard.data.firebase.externals.DocumentSnapshot
import io.github.dayboard.data.firebase.externals.doc
import io.github.dayboard.data.firebase.externals.onSnapshot
import io.github.dayboard.data.firebase.externals.serverTimestamp
import io.github.dayboard.data.firebase.externals.setDoc
import io.github.bchmsl.keel.theme.ColorMode
import io.github.dayboard.domain.model.DisplayMode
import io.github.dayboard.domain.model.Settings
import io.github.dayboard.domain.model.parseCardLayout
import io.github.dayboard.domain.repository.SettingsRepository
import kotlinx.coroutines.await

/**
 * [SettingsRepository] backed by one Firestore document per account.
 *
 * The document lives at `users/{uid}/state/settings`, inside the subtree the
 * security rules restrict to its owner, so nothing here has to prove who is asking.
 *
 * Note on style: the helpers below take the document as a parameter rather than
 * being extensions on `dynamic`. An extension on `dynamic` is never resolved as an
 * extension - Kotlin turns any call on a dynamic value into a runtime property
 * lookup, so `data.int(...)` would compile and then look for a JavaScript method
 * called `int` that does not exist.
 */
class FirestoreSettingsRepository : SettingsRepository {

    override fun observe(uid: String, onChange: (Settings) -> Unit): () -> Unit =
        onSnapshot(
            settingsDoc(uid),
            { snapshot ->
                // Firestore replays this device's own write to its own listener
                // before the server confirms it. The caller already applied that
                // change optimistically, so echoing it back would overwrite whatever
                // has been changed since.
                if (!snapshot.metadata.hasPendingWrites) {
                    onChange(readSettings(snapshot))
                }
            },
            // A dropped connection or a permission error must not take the app with
            // it: the settings in memory stay usable and Firestore retries.
            { error -> console.warn("settings listener failed", error) },
        )

    override suspend fun save(uid: String, settings: Settings) {
        // Merged rather than replaced, so a field written by a newer version is not
        // erased by an older one that has never heard of it.
        setDoc(settingsDoc(uid), settingsDocument(settings), mergeOption()).await()
    }
}

private fun settingsDoc(uid: String): DocumentReference =
    doc(Firebase.firestore, "users", uid, "state", "settings")

private fun mergeOption(): dynamic {
    val options = jsObject()
    options["merge"] = true
    return options
}

/**
 * Reads the stored document.
 *
 * Every field falls back on its own: a document written before a setting existed
 * should cost the user that one setting rather than all of them.
 */
private fun readSettings(snapshot: DocumentSnapshot): Settings {
    if (!snapshot.exists()) return Settings.Default

    val data = snapshot.data()
    val defaults = Settings.Default

    return Settings(
        focusDuration = intField(data, "focusDuration", defaults.focusDuration),
        shortBreakDuration = intField(data, "shortBreakDuration", defaults.shortBreakDuration),
        longBreakDuration = intField(data, "longBreakDuration", defaults.longBreakDuration),
        longBreakInterval = intField(data, "longBreakInterval", defaults.longBreakInterval),
        autoStartBreaks = boolField(data, "autoStartBreaks", defaults.autoStartBreaks),
        autoStartFocus = boolField(data, "autoStartFocus", defaults.autoStartFocus),
        soundEnabled = boolField(data, "soundEnabled", defaults.soundEnabled),
        soundVolume = intField(data, "soundVolume", defaults.soundVolume),
        themeId = stringField(data, "themeId") ?: defaults.themeId,
        colorMode = ColorMode.fromId(stringField(data, "colorMode")),
        displayMode = DisplayMode.fromId(stringField(data, "displayMode")),
        showSeconds = boolField(data, "showSeconds", defaults.showSeconds),
        // The only nullable setting. Blank means auto-detect, which is the same
        // outcome as never having been set.
        weatherCity = stringField(data, "weatherCity")?.takeIf { it.isNotBlank() },
        showWeather = boolField(data, "showWeather", defaults.showWeather),
        showPomodoro = boolField(data, "showPomodoro", defaults.showPomodoro),
        showTasks = boolField(data, "showTasks", defaults.showTasks),
        showNotes = boolField(data, "showNotes", defaults.showNotes),
        cardLayout = parseCardLayout(dynamicToMap(data["cardLayout"])),
    )
}

private fun settingsDocument(settings: Settings): dynamic {
    val document = jsObject()
    document["focusDuration"] = settings.focusDuration
    document["shortBreakDuration"] = settings.shortBreakDuration
    document["longBreakDuration"] = settings.longBreakDuration
    document["longBreakInterval"] = settings.longBreakInterval
    document["autoStartBreaks"] = settings.autoStartBreaks
    document["autoStartFocus"] = settings.autoStartFocus
    document["soundEnabled"] = settings.soundEnabled
    document["soundVolume"] = settings.soundVolume
    document["themeId"] = settings.themeId
    document["colorMode"] = settings.colorMode.id
    document["displayMode"] = settings.displayMode.id
    document["showSeconds"] = settings.showSeconds
    document["weatherCity"] = settings.weatherCity
    document["showWeather"] = settings.showWeather
    document["showPomodoro"] = settings.showPomodoro
    document["showTasks"] = settings.showTasks
    document["showNotes"] = settings.showNotes
    document["cardLayout"] = layoutDocument(settings)
    // The server's clock, not the device's, which can be wrong by hours.
    document["updatedAt"] = serverTimestamp()
    return document
}

private fun layoutDocument(settings: Settings): dynamic {
    val layout = settings.cardLayout
    val widths = jsObject()
    layout.widths.forEach { (card, width) -> widths[card] = width.id }

    val document = jsObject()
    // `toTypedArray` matters: a Kotlin List reaches Firestore as an opaque object,
    // an Array reaches it as a real JSON array.
    document["left"] = layout.left.toTypedArray()
    document["right"] = layout.right.toTypedArray()
    document["collapsed"] = layout.collapsed.toTypedArray()
    document["widths"] = widths
    return document
}

// A stored field can be absent, null, or - if something else wrote it - the wrong
// type. All three mean "use the default" rather than "crash".

private fun intField(data: dynamic, name: String, fallback: Int): Int =
    (data[name] as? Number)?.toInt() ?: fallback

private fun boolField(data: dynamic, name: String, fallback: Boolean): Boolean =
    data[name] as? Boolean ?: fallback

private fun stringField(data: dynamic, name: String): String? = data[name] as? String
