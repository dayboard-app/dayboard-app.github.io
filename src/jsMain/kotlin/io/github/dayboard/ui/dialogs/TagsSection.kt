package io.github.dayboard.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.bchmsl.keel.color.SwatchShade
import io.github.bchmsl.keel.color.Swatches
import io.github.bchmsl.keel.icons.Icon
import io.github.bchmsl.keel.icons.LucideIcon
import io.github.dayboard.domain.model.TAG_EMOJI_MAX_LENGTH
import io.github.dayboard.domain.model.Tag
import io.github.dayboard.domain.model.background
import io.github.dayboard.ui.cards.ICON_MICRO
import io.github.dayboard.ui.cards.ICON_TINY
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.maxLength
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The tag controls shared by the task and note editors.
 *
 * Both take tags from the same collection, so a tag made on a task can be put on a
 * note straight away. Sharing the control as well as the data is what keeps the two
 * from drifting into two slightly different ways of doing the same thing.
 *
 * It knows nothing about what it is tagging: it is handed the tags already on the
 * thing, the tags that are not, and two callbacks.
 */
@Composable
fun TagsSection(
    attached: List<Tag>,
    available: List<Tag>,
    onToggle: (String) -> Unit,
    onCreate: (name: String, color: String, emoji: String?) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }

    Div({ classes("editor__section") }) {
        Div({ classes("editor__label") }) {
            Icon(LucideIcon.Tag, size = ICON_TINY)
            Text("Tags")
        }

        if (attached.isNotEmpty()) {
            Div({ classes("editor__tags") }) {
                attached.forEach { tag ->
                    TagPill(tag, SwatchShade.Pill, "Remove tag ${tag.name}") { onToggle(tag.id) }
                }
            }
        }

        if (available.isNotEmpty()) {
            Div({ classes("editor__tags") }) {
                available.forEach { tag ->
                    TagPill(tag, SwatchShade.Faint, "Add tag ${tag.name}", small = true) {
                        onToggle(tag.id)
                    }
                }
            }
        }

        if (creating) {
            TagCreator(
                onCancel = { creating = false },
                onCreate = { name, color, emoji ->
                    onCreate(name, color, emoji)
                    creating = false
                },
            )
        } else {
            Button({
                classes("editor__ghost-button")
                attr("type", "button")
                onClick { creating = true }
            }) {
                Icon(LucideIcon.Plus, size = ICON_TINY)
                Text("New tag")
            }
        }
    }
}

@Composable
private fun TagPill(
    tag: Tag,
    shade: SwatchShade,
    ariaLabel: String,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    Button({
        classes(
            *listOfNotNull("pill", "pill--button", "pill--small".takeIf { small })
                .toTypedArray(),
        )
        attr("type", "button")
        attr("aria-label", ariaLabel)
        style {
            property("background-color", tag.background(shade))
            property("color", tag.color)
        }
        onClick { onClick() }
    }) {
        tag.emoji?.let { Span({ classes("pill__emoji") }) { Text(it) } }
        Text(tag.name)
        // Only an attached tag can be taken off, so only that one gets the cross.
        if (!small) Icon(LucideIcon.X, size = ICON_MICRO)
    }
}

/**
 * The panel for making a new tag.
 *
 * A name that already exists is not refused here - the caller attaches the existing
 * tag instead. Deciding that at this level would mean this control knowing what is
 * already tagged, which is exactly what it is built not to know.
 */
@Composable
private fun TagCreator(
    onCancel: () -> Unit,
    onCreate: (name: String, color: String, emoji: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(Swatches.Default) }

    Div({ classes("creator") }) {
        Div({ classes("creator__row") }) {
            Input(InputType.Text) {
                classes("input", "creator__emoji")
                value(emoji)
                placeholder("😊")
                maxLength(TAG_EMOJI_MAX_LENGTH)
                attr("aria-label", "Tag emoji")
                onInput { event -> emoji = event.value }
            }

            Input(InputType.Text) {
                classes("input")
                value(name)
                placeholder("Tag name")
                attr("aria-label", "Tag name")
                onInput { event -> name = event.value }
                onKeyDown { event ->
                    if (event.key == "Enter" && name.isNotBlank()) {
                        event.preventDefault()
                        onCreate(name, color, emoji)
                    }
                }
            }
        }

        Div({ classes("creator__colors") }) {
            Swatches.All.forEach { swatch ->
                Button({
                    classes(
                        *listOfNotNull("swatch", "swatch--on".takeIf { swatch == color })
                            .toTypedArray(),
                    )
                    attr("type", "button")
                    attr("aria-label", "Colour $swatch")
                    attr("aria-pressed", (swatch == color).toString())
                    style { property("background-color", swatch) }
                    onClick { color = swatch }
                })
            }
        }

        Div({ classes("creator__row") }) {
            Button({
                classes("editor__primary-button")
                attr("type", "button")
                if (name.isBlank()) attr("disabled", "")
                onClick { onCreate(name, color, emoji) }
            }) {
                Text("Create")
            }
            Button({
                classes("editor__ghost-button")
                attr("type", "button")
                onClick { onCancel() }
            }) {
                Text("Cancel")
            }
        }
    }
}
