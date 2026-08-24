package io.github.dayboard.ui.components

import androidx.compose.runtime.Composable
import io.github.dayboard.ui.icons.Icon
import io.github.dayboard.ui.icons.LucideIcon
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.ContentBuilder
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLDivElement

/**
 * The shell every dashboard card sits in: the clock, the timer, tasks and notes.
 *
 * Collapsing and expanding are the card's own behaviour, but *where* a card sits
 * and whether it is expanded belong to the board, so both are passed in rather
 * than held here. That is what lets the board render the same card inline or
 * full-screen without the card knowing which it is.
 *
 * When [expanded] the drag handle and the collapse toggle are hidden: a card
 * filling the screen cannot be reordered, and collapsing it would leave nothing.
 */
@Composable
fun Card(
    title: String,
    collapsed: Boolean = false,
    expanded: Boolean = false,
    draggable: Boolean = true,
    centerContent: Boolean = false,
    onToggleCollapsed: (() -> Unit)? = null,
    onToggleExpanded: (() -> Unit)? = null,
    onDragStart: (() -> Unit)? = null,
    content: ContentBuilder<HTMLDivElement>,
) {
    Div({ classes("card") }) {
        Div({ classes("card__header") }) {
            Div({ classes("card__title-group") }) {
                if (draggable && !expanded) {
                    Span({
                        classes("card__grip")
                        onDragStart?.let { start ->
                            // Left button only: a right-click on the handle should
                            // open a context menu, not begin a drag the user cannot
                            // see themselves having started.
                            onMouseDown { event ->
                                if (event.button.toInt() == 0) {
                                    event.preventDefault()
                                    start()
                                }
                            }
                            // Touch reports no button, and the default here is the
                            // page scrolling under the finger instead of the card
                            // following it.
                            onTouchStart { event ->
                                event.preventDefault()
                                start()
                            }
                        }
                    }) {
                        Icon(LucideIcon.GripVertical, size = 16)
                    }
                }

                if (expanded || onToggleCollapsed == null) {
                    Span({ classes("card__title") }) { Text(title) }
                } else {
                    Button({
                        classes("card__title")
                        attr("aria-expanded", (!collapsed).toString())
                        onClick { onToggleCollapsed() }
                    }) {
                        Icon(
                            if (collapsed) LucideIcon.ChevronDown else LucideIcon.ChevronUp,
                            size = 16,
                        )
                        Text(title)
                    }
                }
            }

            Div({ classes("card__actions") }) {
                onToggleExpanded?.let { toggle ->
                    Button({
                        classes("card__icon-button")
                        attr("aria-label", if (expanded) "Minimize" else "Maximize")
                        onClick { toggle() }
                    }) {
                        Icon(
                            if (expanded) LucideIcon.Minimize2 else LucideIcon.Maximize2,
                            size = 14,
                        )
                    }
                }
            }
        }

        // Collapsed hides the body outright rather than shrinking it: the original
        // keeps only the header, so a collapsed card is one row tall.
        if (!collapsed) {
            Div({
                classes(
                    *listOfNotNull(
                        "card__content",
                        "card__content--centered".takeIf { centerContent },
                    ).toTypedArray(),
                )
            }) {
                content()
            }
        }
    }
}
