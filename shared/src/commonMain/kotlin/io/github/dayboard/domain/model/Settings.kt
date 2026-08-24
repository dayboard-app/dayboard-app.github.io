package io.github.dayboard.domain.model

/**
 * Which face the app leads with.
 *
 * Stored and round-tripped but never read, exactly as in the original, where the
 * field exists on the settings row and no screen consults it. Kept so the stored
 * shape matches and a future use does not need a migration.
 */
enum class DisplayMode(val id: String) {
    Pomodoro("pomodoro"),
    Clock("clock"),
    ;

    companion object {
        val Default: DisplayMode = Pomodoro

        fun fromId(value: String?): DisplayMode = entries.firstOrNull { it.id == value } ?: Default
    }
}

/**
 * Everything the user can configure, as one value.
 *
 * One object rather than a field-per-setting store because the original saves the
 * whole row on every change, and because a screen that reads four settings should
 * recompose once, not four times.
 *
 * The defaults are the ones a brand-new account gets, and they match the original's
 * database defaults exactly - a difference here would show up as a different first
 * run rather than as an error.
 */
data class Settings(
    val focusDuration: Int = 25,
    val shortBreakDuration: Int = 5,
    val longBreakDuration: Int = 15,
    val longBreakInterval: Int = 4,
    val autoStartBreaks: Boolean = false,
    val autoStartFocus: Boolean = false,
    val soundEnabled: Boolean = true,
    val soundVolume: Int = 70,
    val themeId: ThemeId = ThemeId.Default,
    val colorMode: ColorMode = ColorMode.Default,
    val displayMode: DisplayMode = DisplayMode.Default,
    val showSeconds: Boolean = false,
    val weatherCity: String? = null,
    val showWeather: Boolean = true,
    val showPomodoro: Boolean = true,
    val showTasks: Boolean = true,
    val showNotes: Boolean = true,
    val cardLayout: CardLayout = CardLayout.Default,
) {

    /**
     * Whether a card is on the board at all.
     *
     * The clock is not switchable: it has no toggle in the settings panel and
     * always renders. The other three each have one.
     */
    fun isVisible(card: CardId): Boolean = when (card) {
        CardId.Clock -> true
        CardId.Timer -> showPomodoro
        CardId.Tasks -> showTasks
        CardId.Notes -> showNotes
    }

    /** Whether a stored card id is on the board, ignoring ids that name no card. */
    fun isVisible(cardId: String): Boolean =
        CardId.fromId(cardId)?.let(::isVisible) ?: false

    companion object {
        val Default: Settings = Settings()
    }
}
