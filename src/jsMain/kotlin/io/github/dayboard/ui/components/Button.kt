package io.github.dayboard.ui.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.ContentBuilder
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLButtonElement

/** The button colour treatments, matching the original's shadcn variants. */
enum class ButtonVariant(internal val className: String) {
    Default("btn--default"),
    Destructive("btn--destructive"),
    Outline("btn--outline"),
    Secondary("btn--secondary"),
    Ghost("btn--ghost"),
    Link("btn--link"),
}

/** The button heights. [Icon] is the square form used for icon-only controls. */
enum class ButtonSize(internal val className: String) {
    Default("btn--size-default"),
    Small("btn--size-sm"),
    Large("btn--size-lg"),
    Icon("btn--size-icon"),
}

/**
 * A button with a text label.
 *
 * [ariaLabel] is only needed when the visible text does not describe the action;
 * for an icon-only button use [IconButton], which requires one.
 */
@Composable
fun Button(
    label: String,
    onClick: () -> Unit,
    variant: ButtonVariant = ButtonVariant.Default,
    size: ButtonSize = ButtonSize.Default,
    enabled: Boolean = true,
    ariaLabel: String? = null,
    leading: ContentBuilder<HTMLButtonElement>? = null,
) {
    Button({
        classes("btn", variant.className, size.className)
        if (!enabled) disabled()
        ariaLabel?.let { attr("aria-label", it) }
        onClick { onClick() }
    }) {
        leading?.invoke(this)
        Text(label)
    }
}

/**
 * A button whose content is an icon.
 *
 * [ariaLabel] is required rather than optional: an icon-only control has no
 * accessible name otherwise, and the original names every one of these.
 */
@Composable
fun IconButton(
    ariaLabel: String,
    onClick: () -> Unit,
    variant: ButtonVariant = ButtonVariant.Ghost,
    size: ButtonSize = ButtonSize.Icon,
    enabled: Boolean = true,
    title: String? = null,
    content: ContentBuilder<HTMLButtonElement>,
) {
    Button({
        classes("btn", variant.className, size.className)
        if (!enabled) disabled()
        attr("aria-label", ariaLabel)
        title?.let { attr("title", it) }
        onClick { onClick() }
    }) {
        content()
    }
}
