package io.github.dayboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.bchmsl.keel.components.Card
import io.github.bchmsl.keel.icons.Icon
import io.github.bchmsl.keel.icons.LucideIcon
import io.github.bchmsl.keel.theme.ThemeController
import io.github.dayboard.data.ClockController
import io.github.dayboard.data.DragController
import io.github.dayboard.data.ListDragController
import io.github.dayboard.data.NotesController
import io.github.dayboard.data.NotificationController
import io.github.dayboard.data.SettingsController
import io.github.dayboard.data.TagsController
import io.github.dayboard.data.TasksController
import io.github.dayboard.data.TimerController
import io.github.dayboard.data.WeatherController
import io.github.dayboard.domain.model.BoardColumn
import io.github.dayboard.domain.model.CardId
import io.github.dayboard.ui.cards.ClockCard
import io.github.dayboard.ui.cards.NotesCard
import io.github.dayboard.ui.cards.TasksCard
import io.github.dayboard.ui.cards.TimerCard
import io.github.dayboard.ui.dialogs.NoteEditDialog
import io.github.dayboard.ui.dialogs.NoteViewDialog
import io.github.dayboard.ui.dialogs.TaskEditDialog
import io.github.dayboard.ui.dialogs.TaskViewDialog
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Header
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The board: a clock across the top and two columns of cards under it.
 *
 * The clock is outside the columns on purpose. It is the one card that is always
 * shown and never dragged, so giving it a column would make both possible.
 */
@Composable
fun Dashboard(
    email: String,
    settings: SettingsController,
    clock: ClockController,
    weather: WeatherController,
    timer: TimerController,
    tasks: TasksController,
    notes: NotesController,
    tags: TagsController,
    theme: ThemeController,
    notifications: NotificationController,
    drag: DragController,
    listDrag: ListDragController,
    onSignOut: () -> Unit,
) {
    var expanded: CardId? by remember { mutableStateOf(null) }

    // The dialogs belong to the board rather than to the card, so that collapsing
    // the card behind an open dialog cannot take the dialog with it.
    var editingTaskId: String? by remember { mutableStateOf(null) }
    var viewingTaskId: String? by remember { mutableStateOf(null) }
    var editingNoteId: String? by remember { mutableStateOf(null) }
    var viewingNoteId: String? by remember { mutableStateOf(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    val layout = settings.settings.cardLayout

    val visibleLeft = layout.left.filter(settings.settings::isVisible)
    val visibleRight = layout.right.filter(settings.settings::isVisible)

    // The drag controller needs to know what is on screen to work out where a drop
    // would land. Written in an effect rather than during composition, since it
    // mutates state the composition does not own.
    SideEffect { drag.setVisible(visibleLeft, visibleRight) }

    // Driven from the board rather than from the clock card, because a collapsed
    // card has no body: looking up the weather there would throw the reading away
    // every time somebody collapsed the clock, and fetch it again on every expand.
    DisposableEffect(settings.settings.showWeather, settings.settings.weatherCity) {
        weather.follow(settings.settings.showWeather, settings.settings.weatherCity)
        onDispose { weather.stop() }
    }

    Div({ classes("app") }) {
        BoardHeader(email = email, onOpenSettings = { settingsOpen = true })

        Main({ classes("board") }) {
            if (expanded == null) {
                BoardCard(
                    card = CardId.Clock,
                    settings = settings,
                    clock = clock,
                    weather = weather,
                    timer = timer,
                    tasks = tasks,
                    notes = notes,
                    listDrag = listDrag,
                    drag = drag,
                    draggable = false,
                    onExpand = { expanded = CardId.Clock },
                    onEditTask = { editingTaskId = it },
                    onViewTask = { viewingTaskId = it },
                    onEditNote = { editingNoteId = it },
                    onViewNote = { viewingNoteId = it },
                )

                Div({ classes("board__columns") }) {
                    BoardColumnView(
                        column = BoardColumn.Left,
                        visible = visibleLeft,
                        settings = settings,
                        clock = clock,
                        weather = weather,
                        timer = timer,
                        tasks = tasks,
                        notes = notes,
                        listDrag = listDrag,
                        drag = drag,
                        onExpand = { expanded = it },
                        onEditTask = { editingTaskId = it },
                        onViewTask = { viewingTaskId = it },
                        onEditNote = { editingNoteId = it },
                        onViewNote = { viewingNoteId = it },
                    )
                    BoardColumnView(
                        column = BoardColumn.Right,
                        visible = visibleRight,
                        settings = settings,
                        clock = clock,
                        weather = weather,
                        timer = timer,
                        tasks = tasks,
                        notes = notes,
                        listDrag = listDrag,
                        drag = drag,
                        onExpand = { expanded = it },
                        onEditTask = { editingTaskId = it },
                        onViewTask = { viewingTaskId = it },
                        onEditNote = { editingNoteId = it },
                        onViewNote = { viewingNoteId = it },
                    )
                }
            }
        }
    }

    editingTaskId?.let { id ->
        TaskEditDialog(
            taskId = id,
            tasks = tasks,
            drag = listDrag,
            onDismiss = { editingTaskId = null },
        )
    }

    viewingTaskId?.let { id ->
        TaskViewDialog(
            taskId = id,
            tasks = tasks,
            drag = listDrag,
            // One dialog at a time: reading a task and then editing it is a step
            // forward, not a second window on top of the first.
            onEdit = {
                viewingTaskId = null
                editingTaskId = id
            },
            onDismiss = { viewingTaskId = null },
        )
    }

    if (settingsOpen) {
        SettingsPanel(
            settings = settings,
            theme = theme,
            tags = tags,
            notifications = notifications,
            // Deleting a tag has to reach three places: the tag itself, and every
            // task and note carrying it. Coordinated here because this is where the
            // three meet; a list left holding an id that matches no tag is invisible
            // but real, and enough to hide it under a filter that no longer exists.
            onDeleteTag = { tagId ->
                tasks.removeTag(tagId)
                notes.removeTag(tagId)
                tags.delete(tagId)
            },
            onSignOut = onSignOut,
            onDismiss = { settingsOpen = false },
        )
    }

    editingNoteId?.let { id ->
        NoteEditDialog(noteId = id, notes = notes, onDismiss = { editingNoteId = null })
    }

    viewingNoteId?.let { id ->
        NoteViewDialog(
            noteId = id,
            notes = notes,
            onEdit = {
                viewingNoteId = null
                editingNoteId = id
            },
            onDismiss = { viewingNoteId = null },
        )
    }

    expanded?.let { card ->
        // The backdrop is a sibling of the card rather than its parent, so a click
        // on the card cannot bubble out and close it.
        Div({
            classes("expanded__backdrop")
            onClick { expanded = null }
        })
        Div({ classes("expanded") }) {
            Card(
                title = card.title,
                expanded = true,
                draggable = false,
                centerContent = card == CardId.Clock || card == CardId.Timer,
                onToggleExpanded = { expanded = null },
            ) {
                CardBody(
                    card = card,
                    expanded = true,
                    settings = settings,
                    clock = clock,
                    weather = weather,
                    timer = timer,
                    tasks = tasks,
                    notes = notes,
                    listDrag = listDrag,
                    onEditTask = { editingTaskId = it },
                    onViewTask = { viewingTaskId = it },
                    onEditNote = { editingNoteId = it },
                    onViewNote = { viewingNoteId = it },
                )
            }
        }
    }
}

@Composable
private fun BoardHeader(email: String, onOpenSettings: () -> Unit) {
    Header({ classes("header") }) {
        Div({ classes("header__inner") }) {
            H1({ classes("header__title") }) { Text("Dayboard") }

            Div({ classes("header__actions") }) {
                Span({ classes("header__email") }) { Text(email) }
                Button({
                    classes("header__button")
                    attr("aria-label", "Settings")
                    attr("title", "Settings")
                    onClick { onOpenSettings() }
                }) {
                    Icon(LucideIcon.Settings, size = 16)
                }
            }
        }
    }
}

@Composable
private fun BoardColumnView(
    column: BoardColumn,
    visible: List<String>,
    settings: SettingsController,
    clock: ClockController,
    weather: WeatherController,
    timer: TimerController,
    tasks: TasksController,
    notes: NotesController,
    listDrag: ListDragController,
    drag: DragController,
    onExpand: (CardId) -> Unit,
    onEditTask: (String) -> Unit,
    onViewTask: (String) -> Unit,
    onEditNote: (String) -> Unit,
    onViewNote: (String) -> Unit,
) {
    val isTarget = drag.drag != null && drag.target?.column == column

    Div({
        classes(
            *listOfNotNull("board__column", "board__column--target".takeIf { isTarget })
                .toTypedArray(),
        )
        ref { element ->
            drag.registerColumn(column, element)
            onDispose { drag.registerColumn(column, null) }
        }
    }) {
        if (visible.isEmpty()) {
            // Only meaningful mid-drag; an empty column at rest just takes no space.
            Div({ classes("board__empty") }) { Text("Drop cards here") }
        }

        visible.forEachIndexed { index, cardId ->
            CardId.fromId(cardId)?.let { card ->
                // Keyed by card, and it matters: Compose reuses a slot's element for
                // whatever card now sits in that position, while the `ref` that
                // registers it with the drag controller runs only once, when the
                // element is created. Without this, hiding a card in the settings
                // would leave the controller measuring drops against elements filed
                // under the wrong card. The same fix went into the task and note
                // lists, where the failure was reproducible.
                key(card) {
                    BoardCard(
                        card = card,
                        settings = settings,
                        clock = clock,
                        weather = weather,
                        timer = timer,
                        tasks = tasks,
                        notes = notes,
                        listDrag = listDrag,
                        drag = drag,
                        column = column,
                        index = index,
                        onExpand = { onExpand(card) },
                        onEditTask = onEditTask,
                        onViewTask = onViewTask,
                        onEditNote = onEditNote,
                        onViewNote = onViewNote,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardCard(
    card: CardId,
    settings: SettingsController,
    clock: ClockController,
    weather: WeatherController,
    timer: TimerController,
    tasks: TasksController,
    notes: NotesController,
    listDrag: ListDragController,
    drag: DragController,
    column: BoardColumn = BoardColumn.Left,
    index: Int = 0,
    draggable: Boolean = true,
    onExpand: () -> Unit,
    onEditTask: (String) -> Unit,
    onViewTask: (String) -> Unit,
    onEditNote: (String) -> Unit,
    onViewNote: (String) -> Unit,
) {
    val dragging = drag.isDragging(card.id)

    Div({
        classes(
            *listOfNotNull("board__slot", "board__slot--dragging".takeIf { dragging })
                .toTypedArray(),
        )
        ref { element ->
            drag.registerCard(card.id, element)
            onDispose { drag.registerCard(card.id, null) }
        }
    }) {
        Card(
            title = card.title,
            collapsed = settings.settings.cardLayout.isCollapsed(card),
            draggable = draggable,
            centerContent = card == CardId.Clock || card == CardId.Timer,
            onToggleCollapsed = { settings.toggleCollapsed(card) },
            onToggleExpanded = onExpand,
            onDragStart = if (!draggable) null else {
                {
                    drag.begin(card.id, column, index) { from, to ->
                        settings.moveCard(from.from, to.column, from.sourceIndex, to.index)
                    }
                }
            },
        ) {
            CardBody(
                card = card,
                expanded = false,
                settings = settings,
                clock = clock,
                weather = weather,
                timer = timer,
                tasks = tasks,
                notes = notes,
                listDrag = listDrag,
                onEditTask = onEditTask,
                onViewTask = onViewTask,
                onEditNote = onEditNote,
                onViewNote = onViewNote,
            )
        }
    }
}

/**
 * A card's contents, inline or full-screen.
 *
 * The three that are still placeholders are each a phase of their own; the board
 * exists first so they have somewhere to be built.
 */
@Composable
private fun CardBody(
    card: CardId,
    expanded: Boolean,
    settings: SettingsController,
    clock: ClockController,
    weather: WeatherController,
    timer: TimerController,
    tasks: TasksController,
    notes: NotesController,
    listDrag: ListDragController,
    onEditTask: (String) -> Unit,
    onViewTask: (String) -> Unit,
    onEditNote: (String) -> Unit,
    onViewNote: (String) -> Unit,
) {
    when (card) {
        CardId.Clock -> ClockCard(
            time = clock.time,
            showSeconds = settings.settings.showSeconds,
            weather = weather.weather,
            loadingWeather = weather.loading,
            expanded = expanded,
        )

        CardId.Timer -> TimerCard(
            state = timer.state,
            settings = settings.settings,
            loaded = timer.loaded,
            expanded = expanded,
            onSwitchMode = timer::switchTo,
            onToggleRunning = timer::toggleRunning,
            onReset = timer::reset,
            onSkip = timer::skip,
        )
        CardId.Tasks -> TasksCard(
            tasks = tasks,
            drag = listDrag,
            expanded = expanded,
            onEditTask = onEditTask,
            onViewTask = onViewTask,
        )
        CardId.Notes -> NotesCard(
            notes = notes,
            drag = listDrag,
            expanded = expanded,
            onEditNote = onEditNote,
            onViewNote = onViewNote,
        )
    }
}

@Composable
private fun Placeholder(text: String) {
    P({ classes("muted") }) { Text(text) }
}
