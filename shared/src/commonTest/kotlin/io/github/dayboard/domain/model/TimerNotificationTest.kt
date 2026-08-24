package io.github.dayboard.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TimerNotificationTest {

    @Test
    fun theTitleNamesTheStretchThatEnded() {
        assertEquals("Focus session complete!", timerEndNotification(TimerMode.Focus).title)
        assertEquals("Short break complete!", timerEndNotification(TimerMode.ShortBreak).title)
        assertEquals("Long break complete!", timerEndNotification(TimerMode.LongBreak).title)
    }

    @Test
    fun theBodySaysWhatToDoNext() {
        // Not a restatement of the title: after work it gives permission to stop,
        // after a break it asks for attention back.
        assertEquals(
            "Great work! Time for a break.",
            timerEndNotification(TimerMode.Focus).body,
        )
        assertEquals(
            "Break's over — time to focus!",
            timerEndNotification(TimerMode.ShortBreak).body,
        )
        assertEquals(
            "Break's over — time to focus!",
            timerEndNotification(TimerMode.LongBreak).body,
        )
    }

    @Test
    fun everyModeProducesSomethingToSay() {
        TimerMode.entries.forEach { mode ->
            val notification = timerEndNotification(mode)

            assertEquals(true, notification.title.isNotBlank(), "title for $mode")
            assertEquals(true, notification.body.isNotBlank(), "body for $mode")
        }
    }

    @Test
    fun permission_readsWhatTheBrowserReports() {
        val read = NotificationPermission::fromBrowserValue

        assertEquals(NotificationPermission.Granted, read("granted"))
        assertEquals(NotificationPermission.Denied, read("denied"))
        assertEquals(NotificationPermission.Default, read("default"))
    }

    @Test
    fun permission_readsAnythingElseAsNotYetAsked() {
        // Includes a browser with no Notification API at all, which reports nothing.
        listOf(null, "", "GRANTED", "prompt").forEach { value ->
            assertEquals(
                NotificationPermission.Default,
                NotificationPermission.fromBrowserValue(value),
                "reported \"$value\"",
            )
        }
    }
}
