package io.github.dayboard

import io.github.dayboard.data.AuthController
import io.github.dayboard.data.ClockController
import io.github.dayboard.data.DragController
import io.github.dayboard.data.ListDragController
import io.github.dayboard.data.NotesController
import io.github.dayboard.data.NotificationController
import io.github.dayboard.data.Router
import io.github.dayboard.data.SettingsController
import io.github.dayboard.data.TagsController
import io.github.dayboard.data.TasksController
import io.github.dayboard.data.ThemeController
import io.github.dayboard.data.TimerController
import io.github.dayboard.data.WeatherController
import io.github.dayboard.data.firebase.FirebaseAuthRepository
import io.github.dayboard.data.audio.WebAudioChime
import io.github.dayboard.data.firebase.FirestoreSettingsRepository
import io.github.dayboard.data.firebase.FirestoreNoteRepository
import io.github.dayboard.data.firebase.FirestoreTagRepository
import io.github.dayboard.data.firebase.FirestoreTaskRepository
import io.github.dayboard.data.firebase.FirestoreTimerRepository
import io.github.dayboard.domain.model.timerEndNotification
import io.github.dayboard.data.weather.OpenMeteoWeatherRepository
import io.github.dayboard.ui.App
import kotlinx.coroutines.MainScope
import org.jetbrains.compose.web.renderComposable

/**
 * Entry point, and the only place that builds anything.
 *
 * Everything below is handed what it needs: this function constructs the graph,
 * starts the pieces that watch the browser, and gives the whole thing to the
 * composition. Nothing else calls a constructor.
 *
 * The theme, the router and the session all start before the first composition so
 * the palette, the address and the account are settled before anything paints.
 * Settings start later, because they belong to an account rather than to the page.
 */
fun main() {
    // Outlives every screen: a write that is still in flight when the route changes
    // must still finish.
    val scope = MainScope()

    val theme = ThemeController()
    val router = Router()
    val auth = AuthController(repository = FirebaseAuthRepository(), scope = scope)
    val settings = SettingsController(repository = FirestoreSettingsRepository(), scope = scope)
    val clock = ClockController(scope = scope)
    val weather = WeatherController(repository = OpenMeteoWeatherRepository(), scope = scope)
    val timer = TimerController(
        repository = FirestoreTimerRepository(),
        chime = WebAudioChime(),
        scope = scope,
    )
    // One tag vocabulary, shared: a tag made on a note is available to a task at
    // once, and one listener means no update can be lost to its own echo.
    val tags = TagsController(repository = FirestoreTagRepository(), scope = scope)
    val tasks = TasksController(
        tasks = FirestoreTaskRepository(),
        tags = tags,
        scope = scope,
    )
    val notes = NotesController(
        notes = FirestoreNoteRepository(),
        tags = tags,
        scope = scope,
    )
    val notifications = NotificationController(scope = scope)
    val drag = DragController()
    val listDrag = ListDragController()

    // A stretch ending is the one thing worth interrupting for, and the timer knows
    // nothing about notifications - it reports what ended and this decides the rest.
    timer.onCompleted = { ended -> notifications.show(timerEndNotification(ended)) }

    theme.start()
    router.start()
    auth.start()
    // The clock belongs to the page rather than to an account, so it ticks from the
    // start. The weather does not: it waits for the settings that say whether it is
    // wanted at all, and the board starts it.
    clock.start()
    // Registers the worker again if permission was given on a previous visit: the
    // browser remembers the permission, nothing here does, and without this every
    // reload would look as though notifications had been turned off.
    notifications.start()

    renderComposable(rootElementId = "root") {
        App(
            auth = auth,
            router = router,
            settings = settings,
            clock = clock,
            weather = weather,
            timer = timer,
            tasks = tasks,
            notes = notes,
            tags = tags,
            theme = theme,
            notifications = notifications,
            drag = drag,
            listDrag = listDrag,
        )
    }
}
