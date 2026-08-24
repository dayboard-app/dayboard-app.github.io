package io.github.dayboard.ui.components

import androidx.compose.runtime.Composable
import io.github.dayboard.domain.text.FormattedNode
import io.github.dayboard.domain.text.parseFormattedText
import org.jetbrains.compose.web.attributes.ATarget
import org.jetbrains.compose.web.attributes.target
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Code
import org.jetbrains.compose.web.dom.Em
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.TagElement
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement

/**
 * Draws [text] with its inline markers applied, and its web addresses clickable.
 *
 * The reading is [parseFormattedText] in `:shared`, which is tested; this only turns
 * the result into elements. Nothing here builds markup from the user's text, so
 * there is no escaping to get wrong - a title of `<script>` is a title, drawn as
 * eight characters.
 */
@Composable
fun FormattedText(text: String, classNames: List<String> = emptyList()) {
    // A list rather than one string: `classes` puts each entry through
    // `DOMTokenList.add`, which throws on a token containing a space. Taking a list
    // makes the several-classes case the obvious one to write correctly.
    Span({ classes("formatted", *classNames.toTypedArray()) }) {
        FormattedNodes(parseFormattedText(text))
    }
}

@Composable
private fun FormattedNodes(nodes: List<FormattedNode>) {
    nodes.forEach { node ->
        when (node) {
            is FormattedNode.Plain -> Text(node.text)

            is FormattedNode.Link -> A(href = node.url, attrs = {
                classes("formatted__link")
                // A new tab, because following a link should not throw away whatever
                // was being written. `noopener` is what stops the opened page
                // reaching back through `window.opener`.
                target(ATarget.Blank)
                attr("rel", "noopener noreferrer")
                // A link inside a task row sits on top of the area that opens the
                // task. Without this, following the link would open the dialog too.
                onClick { event -> event.stopPropagation() }
            }) {
                Text(node.url)
            }

            // `strong` and `em` rather than `b` and `i`: the distinction is
            // meaning rather than appearance, and it is what a screen reader
            // announces. Compose HTML has no `Strong`, hence the generic builder.
            is FormattedNode.Bold -> TagElement<HTMLElement>("strong", null) {
                FormattedNodes(node.children)
            }

            is FormattedNode.Italic -> Em { FormattedNodes(node.children) }

            // No element in HTML means "underlined for emphasis"; `u` means a
            // proper-noun or misspelling annotation. A span carrying the style says
            // exactly as much as is true.
            is FormattedNode.Underline -> Span({ classes("formatted__underline") }) {
                FormattedNodes(node.children)
            }

            is FormattedNode.Code -> Code({ classes("formatted__code") }) { Text(node.text) }
        }
    }
}
