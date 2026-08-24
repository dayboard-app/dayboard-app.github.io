package io.github.dayboard.data

import kotlinx.browser.document
import org.w3c.dom.Document

/**
 * Runs [onVisible] every time the page comes back to the foreground.
 *
 * Both things that count seconds need this, and for the same reason: a browser
 * throttles a hidden tab's timers to roughly one a minute and eventually stops them
 * altogether, so whatever they were keeping up to date is stale by the time anyone
 * looks at it again. The fix in both cases is to recompute from the clock on the
 * way back rather than to trust the ticks.
 *
 * There is no way to stop listening, and nothing needs one: both callers live as
 * long as the page does.
 */
internal fun onPageVisible(onVisible: () -> Unit) {
    document.addEventListener("visibilitychange", {
        if (!document.isHidden) onVisible()
    })
}

/**
 * `document.hidden`, which Kotlin's `Document` does not declare.
 *
 * A browser too old to have the Page Visibility API reports `undefined`, which is
 * read as visible. That is the harmless direction: it means recomputing when it was
 * not strictly necessary.
 */
private val Document.isHidden: Boolean get() = asDynamic().hidden == true
