package io.github.dayboard.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.dayboard.ui.components.FormattingTarget
import io.github.dayboard.ui.components.FormattingToolbar
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.rows
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement

/**
 * A text field with the formatting toolbar under it.
 *
 * Deliberately **uncontrolled**: the DOM owns the text, and [onCommit] is called
 * when the user is finished with it rather than on every keystroke. Feeding every
 * keystroke back through composition would put the caret at the end of the field on
 * every character typed into the middle of a word - and it would mean a write per
 * keystroke.
 *
 * [resetKey] is what makes an uncontrolled field safe. Changing it rebuilds the
 * element with fresh text, which is exactly what has to happen when the dialog
 * switches to a different task. Without it the second task would open showing the
 * first one's words.
 *
 * "Finished with it" means blurring, and for a single-line field also pressing
 * Enter, which blurs. Formatting commits too, because the toolbar deliberately does
 * not blur the field and closing the dialog straight after pressing Bold should not
 * lose it.
 */
@Composable
internal fun FormattedField(
    resetKey: String,
    initial: String,
    onCommit: (String) -> Unit,
    placeholder: String? = null,
    ariaLabel: String? = null,
    multiline: Boolean = false,
    textRows: Int = NOTES_ROWS,
) {
    key(resetKey) {
        var field: FormattingTarget? by remember { mutableStateOf(null) }

        Div({ classes("field") }) {
            if (multiline) {
                TextArea {
                    classes("textarea")
                    rows(textRows)
                    placeholder?.let { placeholder(it) }
                    ariaLabel?.let { attr("aria-label", it) }
                    ref { element ->
                        element.value = initial
                        field = FormattingTarget(element)
                        onDispose { field = null }
                    }
                    // Read from the event rather than from the remembered field:
                    // the handler is attached while the element is being built,
                    // before the ref effect has recorded it, so a captured reference
                    // would be null for the element's whole first life.
                    onBlur { event ->
                        (event.target as? HTMLTextAreaElement)?.let { onCommit(it.value) }
                    }
                }
            } else {
                Input(InputType.Text) {
                    classes("input")
                    placeholder?.let { placeholder(it) }
                    ariaLabel?.let { attr("aria-label", it) }
                    ref { element ->
                        element.value = initial
                        field = FormattingTarget(element)
                        onDispose { field = null }
                    }
                    onKeyDown { event ->
                        if (event.key == "Enter") {
                            // Stops the surrounding form submitting, and blurs, which
                            // is what actually saves.
                            event.preventDefault()
                            (event.target as? HTMLInputElement)?.blur()
                        }
                    }
                    // See the note on the multi-line branch: the event carries the
                    // element, so nothing has to have been remembered first.
                    onBlur { event ->
                        (event.target as? HTMLInputElement)?.let { onCommit(it.value) }
                    }
                }
            }

            FormattingToolbar(target = { field }, onTextChange = onCommit)
        }
    }
}

internal const val NOTES_ROWS = 6
