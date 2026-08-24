package io.github.dayboard.domain.model

/**
 * The bounds a settings slider allows, and the unit shown beside it.
 *
 * Here rather than in the panel because a bound is a rule about the setting, not
 * about the control: a duration of zero would give a timer with nothing to count,
 * and a long-break interval of one would mean every session ends in a long break.
 * Storing the pair together also stops a slider and its label disagreeing.
 */
enum class SettingRange(val min: Int, val max: Int, val unit: String) {
    /** Long enough to be worth starting, short enough to stay in one sitting. */
    FocusDuration(1, 90, "min"),

    ShortBreakDuration(1, 30, "min"),
    LongBreakDuration(1, 60, "min"),

    /** Two at least, or the short break would never be reached. */
    LongBreakInterval(2, 8, "sessions"),

    SoundVolume(0, 100, "%"),
    ;

    /** Brings a value inside the range, for one arriving from storage. */
    fun clamp(value: Int): Int = value.coerceIn(min, max)

    /** The value as the panel shows it, beside the slider. */
    fun label(value: Int): String = "${clamp(value)} $unit"
}
