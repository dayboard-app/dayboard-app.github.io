package io.github.dayboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.dayboard.data.DragController
import io.github.dayboard.data.SettingsController
import io.github.dayboard.domain.model.BoardColumn
import io.github.dayboard.domain.model.CardId
import io.github.dayboard.ui.components.Card
import io.github.dayboard.ui.icons.Icon
import io.github.dayboard.ui.icons.LucideIcon
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
    drag: DragController,
    onSignOut: () -> Unit,
) {
    var expanded: CardId? by remember { mutableStateOf(null) }
    val layout = settings.settings.cardLayout

    val visibleLeft = layout.left.filter(settings.settings::isVisible)
    val visibleRight = layout.right.filter(settings.settings::isVisible)

    // The drag controller needs to know what is on screen to work out where a drop
    // would land. Written in an effect rather than during composition, since it
    // mutates state the composition does not own.
    SideEffect { drag.setVisible(visibleLeft, visibleRight) }

    Div({ classes("app") }) {
        BoardHeader(email = email, onSignOut = onSignOut)

        Main({ classes("board") }) {
            if (expanded == null) {
                BoardCard(
                    card = CardId.Clock,
                    settings = settings,
                    drag = drag,
                    draggable = false,
                    onExpand = { expanded = CardId.Clock },
                )

                Div({ classes("board__columns") }) {
                    BoardColumnView(BoardColumn.Left, visibleLeft, settings, drag) { expanded = it }
                    BoardColumnView(BoardColumn.Right, visibleRight, settings, drag) { expanded = it }
                }
            }
        }
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
                CardBody(card)
            }
        }
    }
}

@Composable
private fun BoardHeader(email: String, onSignOut: () -> Unit) {
    Header({ classes("header") }) {
        Div({ classes("header__inner") }) {
            H1({ classes("header__title") }) { Text("Dayboard") }

            Div({ classes("header__actions") }) {
                Span({ classes("header__email") }) { Text(email) }
                Button({
                    classes("header__button")
                    attr("aria-label", "Sign out")
                    attr("title", "Sign out")
                    onClick { onSignOut() }
                }) {
                    Icon(LucideIcon.LogOut, size = 16)
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
    drag: DragController,
    onExpand: (CardId) -> Unit,
) {
    val isTarget = drag.drag != null && drag.target?.column == column

    Div({
        classes(*listOfNotNull("board__column", "board__column--target".takeIf { isTarget }).toTypedArray())
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
                BoardCard(
                    card = card,
                    settings = settings,
                    drag = drag,
                    column = column,
                    index = index,
                    onExpand = { onExpand(card) },
                )
            }
        }
    }
}

@Composable
private fun BoardCard(
    card: CardId,
    settings: SettingsController,
    drag: DragController,
    column: BoardColumn = BoardColumn.Left,
    index: Int = 0,
    draggable: Boolean = true,
    onExpand: () -> Unit,
) {
    val dragging = drag.isDragging(card.id)

    Div({
        classes(*listOfNotNull("board__slot", "board__slot--dragging".takeIf { dragging }).toTypedArray())
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
            CardBody(card)
        }
    }
}

/**
 * Stands in for a card's real contents.
 *
 * Each of these is a phase of its own; the board exists first so they have
 * somewhere to be built.
 */
@Composable
private fun CardBody(card: CardId) {
    P({ classes("muted") }) {
        Text(
            when (card) {
                CardId.Clock -> "Clock and weather arrive next."
                CardId.Timer -> "The Pomodoro timer arrives after the clock."
                CardId.Tasks -> "Tasks arrive after the timer."
                CardId.Notes -> "Notes arrive after tasks."
            },
        )
    }
}
