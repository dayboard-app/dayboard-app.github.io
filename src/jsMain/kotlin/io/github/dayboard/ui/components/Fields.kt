package io.github.dayboard.ui.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.max
import org.jetbrains.compose.web.attributes.min
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.rows
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.TextArea

/**
 * The on/off control used throughout the settings panel.
 *
 * A button with `role="switch"` rather than a checkbox: the original's is a 20x36
 * track with a sliding 16px knob, which cannot be built from a native checkbox
 * without hiding it and duplicating its semantics anyway. The role and
 * `aria-checked` give assistive technology the same information the visual gives
 * everyone else, and `aria-checked` is also what the CSS keys the "on" colour off,
 * so the two can never disagree.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    ariaLabel: String,
) {
    Button({
        classes("switch")
        attr("role", "switch")
        attr("aria-checked", checked.toString())
        attr("aria-label", ariaLabel)
        onClick { onCheckedChange(!checked) }
    }) {
        Span({ classes("switch__knob") })
    }
}

/**
 * A value slider, used for the timer durations and the sound volume.
 *
 * Built on a native range input so keyboard stepping, touch dragging and
 * assistive-technology support come for free; only its appearance is replaced.
 * Fires on every movement, matching the original, so the number beside it tracks
 * the thumb rather than waiting for release.
 */
@Composable
fun Slider(
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    ariaLabel: String,
) {
    Input(InputType.Range) {
        classes("slider")
        min(min.toString())
        max(max.toString())
        value(value)
        attr("aria-label", ariaLabel)
        // A range input carries a Number rather than the String a text input
        // would, so there is nothing to parse. It is nullable only because the
        // event type is shared with inputs that can genuinely be empty; a range
        // always has a value, and ignoring the null needs no fallback guess.
        onInput { event -> event.value?.let { onValueChange(it.toInt()) } }
    }
}

/** A single-line text field. */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    ariaLabel: String? = null,
) {
    Input(InputType.Text) {
        classes("input")
        value(value)
        placeholder?.let { placeholder(it) }
        ariaLabel?.let { attr("aria-label", it) }
        onInput { event -> onValueChange(event.value) }
    }
}

/** A multi-line text field, vertically resizable like the original's. */
@Composable
fun TextAreaField(
    value: String,
    onValueChange: (String) -> Unit,
    rows: Int = 6,
    placeholder: String? = null,
    ariaLabel: String? = null,
) {
    TextArea(value = value) {
        classes("textarea")
        rows(rows)
        placeholder?.let { placeholder(it) }
        ariaLabel?.let { attr("aria-label", it) }
        onInput { event -> onValueChange(event.value) }
    }
}
