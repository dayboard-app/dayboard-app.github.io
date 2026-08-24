package io.github.dayboard.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dayboard.domain.model.ColorMode
import io.github.dayboard.domain.model.ThemeId
import io.github.dayboard.domain.model.resolvesToDark
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * Owns the active theme: the stored preference, what the device asks for, and the
 * two attributes on `<html>` that `tokens.css` selects palettes with.
 *
 * Lives in `data` because everything it touches is outside the program: the
 * browser's storage, its media queries, and the document element.
 *
 * Storage is `localStorage` only. That is the whole story while signed out, and it
 * is deliberately the first thing read on boot so a reload paints the right palette
 * without waiting for the network. The account's copy arrives later, from the
 * settings document, and wins if it disagrees.
 */
class ThemeController {

    private val darkQuery = window.matchMedia(DARK_MEDIA_QUERY)

    var themeId: ThemeId by mutableStateOf(
        ThemeId.fromId(window.localStorage.getItem(KEY_THEME_ID)),
    )
        private set

    var colorMode: ColorMode by mutableStateOf(
        ColorMode.fromId(window.localStorage.getItem(KEY_COLOR_MODE)),
    )
        private set

    private var systemPrefersDark: Boolean by mutableStateOf(darkQuery.matches)

    /** Whether dark styling applies, taking the device into account under System. */
    val isDark: Boolean get() = colorMode.resolvesToDark(systemPrefersDark)

    /**
     * Applies the stored preference and begins following the device.
     *
     * Call once, before the first composition. Without the listener, a user on
     * System who changes their OS appearance would keep the old palette until they
     * reloaded.
     */
    fun start() {
        darkQuery.addEventListener("change", {
            systemPrefersDark = darkQuery.matches
            applyToDocument()
        })
        applyToDocument()
    }

    fun setThemeId(value: ThemeId) {
        if (value == themeId) return
        themeId = value
        window.localStorage.setItem(KEY_THEME_ID, value.id)
        applyToDocument()
    }

    fun setColorMode(value: ColorMode) {
        if (value == colorMode) return
        colorMode = value
        window.localStorage.setItem(KEY_COLOR_MODE, value.id)
        applyToDocument()
    }

    /**
     * The palette is chosen entirely in CSS; this only states which one.
     * `data-theme` picks the accent block and the `dark` class picks the mode,
     * exactly as the original does, so no colour is ever computed in Kotlin.
     */
    private fun applyToDocument() {
        val root = document.documentElement ?: return
        root.setAttribute("data-theme", themeId.id)
        root.classList.toggle("dark", isDark)
    }

    companion object {
        /**
         * Storage keys, matching the original's so a browser that used it keeps its
         * theme. The inline boot script in `index.html` reads the same two keys to
         * paint before this class exists; changing either name means changing both.
         */
        const val KEY_THEME_ID: String = "themeId"
        const val KEY_COLOR_MODE: String = "colorMode"

        private const val DARK_MEDIA_QUERY = "(prefers-color-scheme: dark)"
    }
}
