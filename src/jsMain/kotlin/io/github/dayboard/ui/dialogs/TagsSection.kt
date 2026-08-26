package io.github.dayboard.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.bchmsl.keel.color.SwatchShade
import io.github.bchmsl.keel.color.Swatches
import io.github.bchmsl.keel.components.Button
import io.github.bchmsl.keel.components.ButtonSize
import io.github.bchmsl.keel.components.ButtonVariant
import io.github.bchmsl.keel.components.PillButton
import io.github.bchmsl.keel.components.PillSize
import io.github.bchmsl.keel.components.Swatch
import io.github.bchmsl.keel.components.SwatchSize
import io.github.bchmsl.keel.components.TextField
import io.github.bchmsl.keel.dom.classNames
import io.github.bchmsl.keel.icons.Icon
import io.github.bchmsl.keel.icons.LucideIcon
import io.github.dayboard.domain.model.TAG_EMOJI_MAX_LENGTH
import io.github.dayboard.domain.model.Tag
import io.github.dayboard.ui.cards.ICON_MICRO
import io.github.dayboard.ui.cards.ICON_TINY
import org.jetbrains.compose.web.attributes.maxLength
import org.jetbrains.compose.web.dom.Div
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
            Button(
                label = "New tag",
                onClick = { creating = true },
                variant = ButtonVariant.Quiet,
                size = ButtonSize.ExtraSmall,
                attrs = { classNames("editor__inline-action") },
                leading = { Icon(LucideIcon.Plus, size = ICON_TINY) },
            )
        }
    }
}

/**
 * A [Tag] as keel's `PillButton`.
 *
 * The fill and the ink come from the tag's own colour, which keel resolves from
 * [shade] - so the two shades this section uses, a full pill for an attached tag and a
 * faint one for an available tag, are the same component at two strengths.
 */
@Composable
private fun TagPill(
    tag: Tag,
    shade: SwatchShade,
    ariaLabel: String,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    PillButton(
        label = tag.name,
        color = tag.color,
        ariaLabel = ariaLabel,
        onClick = onClick,
        shade = shade,
        emoji = tag.emoji,
        size = if (small) PillSize.Small else PillSize.Default,
        // Only an attached tag can be taken off, so only that one gets the cross.
        trailing = if (small) null else ({ Icon(LucideIcon.X, size = ICON_MICRO) }),
    )
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
            TextField(
                value = emoji,
                onValueChange = { emoji = it },
                placeholder = "\uD83D\uDE0A",
                ariaLabel = "Tag emoji",
                attrs = {
                    classNames("creator__emoji")
                    maxLength(TAG_EMOJI_MAX_LENGTH)
                },
            )

            TextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Tag name",
                ariaLabel = "Tag name",
                attrs = {
                    onKeyDown { event ->
                        if (event.key == "Enter" && name.isNotBlank()) {
                            event.preventDefault()
                            onCreate(name, color, emoji)
                        }
                    }
                },
            )
        }

        Div({ classes("creator__colors") }) {
            Swatches.All.forEach { swatch ->
                Swatch(
                    color = swatch,
                    ariaLabel = "Colour $swatch",
                    selected = swatch == color,
                    onSelect = { color = swatch },
                    // `Small`, which is the tier for a grid: what matters here is
                    // telling ten colours apart, not aiming at one of them.
                    size = SwatchSize.Small,
                )
            }
        }

        Div({ classes("creator__row") }) {
            Button(
                label = "Create",
                onClick = { onCreate(name, color, emoji) },
                size = ButtonSize.ExtraSmall,
                enabled = name.isNotBlank(),
            )
            Button(
                label = "Cancel",
                onClick = onCancel,
                variant = ButtonVariant.Quiet,
                size = ButtonSize.ExtraSmall,
            )
        }
    }
}
