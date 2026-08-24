package io.github.dayboard.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Span

/**
 * Draws a lucide icon at [size] pixels square, in the current text colour.
 *
 * The markup is written straight into the span rather than built from a Compose
 * SVG tree. The icons are compile-time constants generated from the lucide
 * repository, so there is no untrusted content to escape, and this keeps the
 * generated catalogue to one string per icon instead of a composable each.
 *
 * The `key(icon)` matters: the effect that writes the markup runs when the element
 * is created, not on every recomposition, so swapping the icon on an existing span
 * would leave the old drawing in place. Keying discards the span instead, which is
 * what makes a play/pause button actually change.
 */
@Composable
fun Icon(icon: LucideIcon, size: Int = 16, className: String? = null) {
    key(icon) {
        Span({
            classes(*listOfNotNull("icon", className).toTypedArray())
            style {
                width(size.px)
                height(size.px)
            }
            ref { element ->
                element.innerHTML = icon.svg()
                onDispose { }
            }
        })
    }
}

/**
 * Wraps an icon's body in the frame every lucide icon shares.
 *
 * `stroke="currentColor"` is what lets an icon inherit its colour from the text
 * around it, so a button hover recolours the icon with no icon-specific rule.
 * It is hidden from assistive technology: every icon in the app sits next to a
 * label or inside a control that carries its own accessible name.
 */
private fun LucideIcon.svg(): String =
    """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" """ +
        """stroke="currentColor" stroke-width="2" stroke-linecap="round" """ +
        """stroke-linejoin="round" aria-hidden="true" focusable="false">$body</svg>"""
