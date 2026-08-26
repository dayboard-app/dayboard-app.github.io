package io.github.dayboard.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dayboard.data.notifications.readNotificationPermission
import io.github.dayboard.data.notifications.requestNotificationPermission
import io.github.dayboard.data.notifications.serviceWorkerContainer
import io.github.dayboard.data.notifications.timerNotificationOptions
import io.github.dayboard.domain.model.NotificationPermission
import io.github.dayboard.domain.model.TimerNotification
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.w3c.workers.ServiceWorkerRegistration

/**
 * Telling the user a stretch has ended, when the tab is not the thing they are
 * looking at.
 *
 * Shown by the service worker rather than by the page, which is what lets a
 * notification carry a vibration pattern and survive the tab being in the
 * background - and is the only route that works at all on Android.
 *
 * This is the free-tier half of the feature: the notification appears on the
 * device that finished the timer. Reaching a user's other devices needs a server
 * to push to them, and a paid plan to run it.
 */
class NotificationController(private val scope: CoroutineScope) {

    /** What the browser currently says. Read once at start-up, then on every change. */
    var permission: NotificationPermission by mutableStateOf(
        NotificationPermission.fromBrowserValue(readNotificationPermission()),
    )
        private set

    /**
     * Whether notifications are possible here at all.
     *
     * False on an insecure origin, and in browsers with no service worker. The
     * settings panel hides the whole section rather than offering a button that
     * cannot work.
     */
    val supported: Boolean = serviceWorkerContainer() != null &&
        readNotificationPermission() != null

    /** True once the worker is registered and able to show anything. */
    var ready: Boolean by mutableStateOf(false)
        private set

    private var registration: ServiceWorkerRegistration? = null

    /**
     * Registers the worker if permission has already been given.
     *
     * Called at start-up so a returning user needs no second visit to the settings
     * panel: the browser remembers the permission, and this remembers nothing, so
     * without it every reload would look like notifications had been turned off.
     */
    fun start() {
        if (!supported || permission != NotificationPermission.Granted) return

        scope.launch { register() }
    }

    /**
     * Asks for permission, and registers the worker if it is given.
     *
     * Must be called straight from a click. A browser ignores a permission request
     * that did not come from a user gesture, and some hold it against the site.
     */
    fun enable() {
        if (!supported) return

        scope.launch {
            val granted = requestNotificationPermission()?.await()
            permission = NotificationPermission.fromBrowserValue(granted)

            if (permission == NotificationPermission.Granted) register()
        }
    }

    /**
     * Shows a notification, if it is possible and wanted.
     *
     * Does nothing quietly otherwise. A timer that ends is not the moment to raise
     * a problem about notification permissions.
     */
    fun show(notification: TimerNotification) {
        val worker = registration ?: return
        if (permission != NotificationPermission.Granted) return

        scope.launch {
            try {
                worker.showNotification(
                    notification.title,
                    timerNotificationOptions(
                        body = notification.body,
                        // Origin *and* path. The worker opens this URL when the
                        // notification is clicked, and matches open tabs by prefix
                        // against it - so the bare origin means any copy of the app
                        // on this host answers for any other. Serving a second copy
                        // under a path is enough to break it: a timer that ends in
                        // one would focus the other, or open it fresh.
                        //
                        // The hash is deliberately left off. It is the current route,
                        // and a notification arriving while the user is somewhere else
                        // should not drag them back to wherever they were when the
                        // timer started.
                        url = window.location.origin + window.location.pathname,
                    ),
                ).await()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // Most likely permission revoked between the check and the call.
                console.warn("the timer notification could not be shown", error)
            }
        }
    }

    private suspend fun register() {
        val container = serviceWorkerContainer() ?: return

        try {
            container.register(WORKER_URL).await()
            registration = container.ready.await()
            ready = true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            console.warn("the notification worker could not be registered", error)
        }
    }

    private companion object {
        const val WORKER_URL = "sw.js"
    }
}
