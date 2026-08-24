package io.github.dayboard.ui.components

import androidx.compose.runtime.Composable
import io.github.dayboard.domain.text.FormattingMarker
import io.github.dayboard.domain.text.TextSelection
import io.github.dayboard.domain.text.applyFormatting
import io.github.dayboard.ui.icons.Icon
import io.github.dayboard.ui.icons.LucideIcon
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div

/**
 * A text field the toolbar can format.
 *
 * `input` and `textarea` carry the same four members this needs - a value, a
 * selection, focus and a way to set the selection back - and share no Kotlin type
 * that declares any of them. Rather than two near-identical wrappers, the element is
 * held untyped and the four members are named here, in one place, where a typo shows
 * up as a missing formatting button rather than anywhere else.
 */
class FormattingTarget(private val element: dynamic) {

    var value: String
        get() = element.value as String
        set(newValue) {
            element.value = newValue
        }

    /** Null when the field has never been focused, in which case there is no caret. */
    val selectionStart: Int? get() = element.selectionStart as? Int
    val selectionEnd: Int? get() = element.selectionEnd as? Int

    fun focus() {
        element.focus()
    }

    fun select(start: Int, end: Int) {
        element.setSelectionRange(start, end)
    }
}

/**
 * The four formatting buttons that sit under a text field.
 *
 * [target] hands back the field itself rather than its text, because the work needs
 * the selection too and the selection only exists on the element.
 *
 * The field is written directly rather than through composed state. A controlled
 * field would need the caller's state, the DOM value and the caret to agree within
 * one frame, and the caret is what loses that race: it jumps to the end of the text.
 * Writing the element and putting the selection back keeps the cursor where the user
 * left it.
 */
@Composable
fun FormattingToolbar(target: () -> FormattingTarget?, onTextChange: (String) -> Unit) {
    Div({
        classes("toolbar")
        // Without this the field blurs the moment a button is pressed. Every field
        // these sit under saves on blur, so formatting would save the text as it was
        // *before* the button did anything, and then be overwritten by it.
        onMouseDown { event -> event.preventDefault() }
    }) {
        FormattingMarker.entries.forEach { marker ->
            Button({
                classes("toolbar__button")
                // Buttons inside a form submit it unless told otherwise, and one of
                // these does sit in a form.
                attr("type", "button")
                attr("title", marker.label)
                attr("aria-label", marker.label)
                onClick { target()?.let { apply(it, marker, onTextChange) } }
            }) {
                Icon(marker.icon, size = TOOLBAR_ICON_SIZE)
            }
        }
    }
}

private fun apply(
    field: FormattingTarget,
    marker: FormattingMarker,
    onTextChange: (String) -> Unit,
) {
    val start = field.selectionStart ?: field.value.length
    val end = field.selectionEnd ?: start

    val result = applyFormatting(field.value, TextSelection(start, end), marker)

    field.value = result.text
    onTextChange(result.text)

    // Next frame rather than now: a browser puts the caret at the end of a field
    // whose value has just been set, and does it after the current task finishes.
    // Restoring the selection before that would simply be undone.
    window.requestAnimationFrame {
        field.focus()
        field.select(result.selection.start, result.selection.end)
    }
}

/** The picture for each button, in the order the toolbar shows them. */
private val FormattingMarker.icon: LucideIcon
    get() = when (this) {
        FormattingMarker.Bold -> LucideIcon.Bold
        FormattingMarker.Italic -> LucideIcon.Italic
        FormattingMarker.Underline -> LucideIcon.Underline
        FormattingMarker.Code -> LucideIcon.Code
    }

private const val TOOLBAR_ICON_SIZE = 14
