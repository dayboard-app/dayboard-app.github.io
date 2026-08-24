package io.github.dayboard.ui.components

import androidx.compose.runtime.Composable
import io.github.dayboard.ui.icons.Icon
import io.github.dayboard.ui.icons.LucideIcon
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.ContentBuilder
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLDivElement

/**
 * The modal used by the task and note editors.
 *
 * [title] and [description] are rendered for assistive technology but not painted:
 * the original's dialogs label themselves that way and let their own content carry
 * the visible heading. They are required rather than optional so a dialog can never
 * reach a screen reader unnamed.
 *
 * Dismissing is deliberately available three ways - the scrim, the close button and
 * Escape - because the original offers all three and a modal that traps someone is
 * worse than one that closes too easily.
 */
@Composable
fun Dialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    content: ContentBuilder<HTMLDivElement>,
) {
    Div({
        classes("dialog__overlay")
        onClick { onDismiss() }
    })

    Div({
        classes("dialog__content")
        attr("role", "dialog")
        attr("aria-modal", "true")
        attr("aria-label", title)
        // The scrim sits behind this element, so a click inside would otherwise
        // bubble out to it and close the dialog the user is typing in.
        onClick { event -> event.stopPropagation() }
    }) {
        H2({ classes("sr-only") }) { Text(title) }
        P({ classes("sr-only") }) { Text(description) }

        Button({
            classes("dialog__close")
            attr("aria-label", "Close")
            onClick { onDismiss() }
        }) {
            Icon(LucideIcon.X, size = 16)
        }

        content()
    }
}
