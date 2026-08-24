package io.github.dayboard.domain.model

/**
 * The three stretches a pomodoro cycle alternates between.
 *
 * [id] is the stored value and must not be renamed: it is what a running timer is
 * restored from, on this device and on any other.
 */
enum class TimerMode(val id: String, val label: String) {
    Focus("focus", "Focus"),
    ShortBreak("shortBreak", "Short Break"),
    LongBreak("longBreak", "Long Break"),
    ;

    /**
     * Whether this is time off rather than time working.
     *
     * Both breaks behave identically everywhere except in how long they last and
     * what triggers them, so almost everything asks this rather than naming them.
     */
    val isBreak: Boolean get() = this != Focus

    /**
     * How a finished stretch is named when the user is told about it.
     *
     * Deliberately not [label]: the tab says "Short Break" because it is a thing to
     * pick, while the notification says "Short break has ended" because it is a
     * thing that happened.
     */
    val endedLabel: String
        get() = when (this) {
            Focus -> "Focus session"
            ShortBreak -> "Short break"
            LongBreak -> "Long break"
        }

    companion object {
        val Default: TimerMode = Focus

        /** Reads a stored mode, falling back to focus for anything unrecognised. */
        fun fromId(value: String?): TimerMode = entries.firstOrNull { it.id == value } ?: Default
    }
}
