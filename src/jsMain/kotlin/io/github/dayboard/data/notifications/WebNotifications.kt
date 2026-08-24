package io.github.dayboard.data.notifications

import kotlinx.browser.window
import org.w3c.notifications.NotificationOptions
import org.w3c.workers.ServiceWorkerContainer

/**
 * Building the options for a timer notification.
 *
 * Kotlin's browser stdlib carries the Notification and service-worker types, so
 * there are no externals here - only the one place that decides what a Dayboard
 * notification looks like.
 *
 * Notifications are shown through a service-worker registration rather than
 * `new Notification(...)`. Only that route supports the tag, renotify and vibrate
 * options, and it is the only one that works at all on Android, where a timer
 * notification is most useful.
 */
internal fun timerNotificationOptions(body: String, url: String): NotificationOptions =
    NotificationOptions(
        body = body,
        icon = NOTIFICATION_ICON,
        badge = NOTIFICATION_ICON,
        // One tag for the whole timer, so notifications replace one another: a
        // phone left alone all afternoon shows the last thing that happened rather
        // than a stack of eight identical ones.
        tag = NOTIFICATION_TAG,
        // Alert again even though the tag replaced an existing notification.
        renotify = true,
        // Buzz, pause, buzz. Enough to notice face-down, short enough not to annoy.
        vibrate = arrayOf(200, 100, 200),
        // Read back by the worker's click handler, which uses it to find an open
        // window before opening a new one.
        data = urlPayload(url),
    )

/**
 * Reads `Notification.permission` without naming the global.
 *
 * Referring to a global that does not exist throws, and its absence is exactly the
 * case being detected - a browser with no Notification API at all.
 */
internal fun readNotificationPermission(): String? =
    js("typeof Notification === 'undefined' ? null : Notification.permission") as? String

/**
 * Asks for permission, or null where there is nothing to ask.
 *
 * Must be called from a user gesture: browsers ignore a request that was not, and
 * some count it against the site.
 */
internal fun requestNotificationPermission(): kotlin.js.Promise<String>? =
    js("typeof Notification === 'undefined' ? null : Notification.requestPermission()")
        .unsafeCast<kotlin.js.Promise<String>?>()

/**
 * `navigator.serviceWorker`, when there really is one.
 *
 * The stdlib declares it non-null, and reality disagrees: it is absent on an
 * insecure origin and in private windows in some browsers. Reading it dynamically
 * is the only way to ask, and asking is the whole point - a missing worker means
 * the app should not offer notifications at all.
 */
internal fun serviceWorkerContainer(): ServiceWorkerContainer? {
    val container = window.navigator.asDynamic().serviceWorker

    return if (container == null || container == undefined) {
        null
    } else {
        container.unsafeCast<ServiceWorkerContainer>()
    }
}

/** Kotlin has no JS object literal, so the payload is made and filled in. */
private fun urlPayload(url: String): dynamic {
    val payload = js("{}")
    payload.url = url
    return payload
}

/** The app's own mark, so a notification is recognisable at a glance. */
private const val NOTIFICATION_ICON = "icon-192.png"

private const val NOTIFICATION_TAG = "timer-notification"
