package io.github.dayboard.domain.model

/**
 * The six accent palettes the user can pick between.
 *
 * [id] is the stored value: it is written to `localStorage`, saved on the user's
 * settings document, and set as `data-theme` on the document element, where it
 * selects a palette in `tokens.css`. Changing one would orphan every stored
 * preference, so the strings are part of the data format, not a display detail.
 *
 * [accentHex] is the swatch the theme picker paints. It is a plain hex rather
 * than a token because it has to be drawn while a *different* theme is active,
 * so it cannot come from the current palette's variables.
 */
enum class ThemeId(val id: String, val accentHex: String, val label: String) {
    Coral("coral", "#f43f5e", "Coral"),
    Ocean("ocean", "#0ea5e9", "Ocean"),
    Forest("forest", "#22c55e", "Forest"),
    Lavender("lavender", "#a78bfa", "Lavender"),
    Ember("ember", "#f97316", "Ember"),
    Slate("slate", "#64748b", "Slate"),
    ;

    companion object {
        /** Coral is the default, and the palette `tokens.css` falls back to. */
        val Default: ThemeId = Coral

        /**
         * Resolves a stored theme id, falling back to [Default] for anything
         * unrecognised: a null or empty value from a first visit, or a value left
         * behind by a theme that no longer exists.
         */
        fun fromId(value: String?): ThemeId = entries.firstOrNull { it.id == value } ?: Default
    }
}

/**
 * Whether the user wants light styling, dark styling, or whatever the device asks
 * for. [ColorMode.System] is the default, so a first visit matches the OS.
 */
enum class ColorMode(val id: String, val label: String) {
    Light("light", "Light"),
    Dark("dark", "Dark"),
    System("system", "System"),
    ;

    companion object {
        val Default: ColorMode = System

        /** Resolves a stored colour mode, falling back to [Default]. See [ThemeId.fromId]. */
        fun fromId(value: String?): ColorMode = entries.firstOrNull { it.id == value } ?: Default
    }
}

/**
 * Whether dark styling applies right now.
 *
 * [systemPrefersDark] is what `prefers-color-scheme` currently reports. It only
 * matters under [ColorMode.System]; an explicit choice ignores the device, which
 * is the point of making one.
 */
fun ColorMode.resolvesToDark(systemPrefersDark: Boolean): Boolean = when (this) {
    ColorMode.Light -> false
    ColorMode.Dark -> true
    ColorMode.System -> systemPrefersDark
}
