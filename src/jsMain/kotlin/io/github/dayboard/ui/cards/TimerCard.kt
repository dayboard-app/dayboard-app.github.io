package io.github.dayboard.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import io.github.dayboard.domain.model.Settings
import io.github.dayboard.domain.model.TimerMode
import io.github.dayboard.domain.model.TimerState
import io.github.dayboard.domain.model.progressPercent
import io.github.dayboard.domain.model.sessionDots
import io.github.dayboard.presentation.formatCountdown
import io.github.dayboard.ui.icons.Icon
import io.github.dayboard.ui.icons.LucideIcon
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The pomodoro card: mode tabs, a progress ring, the session dots, and the controls.
 *
 * Reads its state and reports presses. Every rule about what a press means lives in
 * the controller and, below that, in `:shared`.
 */
@Composable
fun TimerCard(
    state: TimerState,
    settings: Settings,
    loaded: Boolean,
    expanded: Boolean,
    onSwitchMode: (TimerMode) -> Unit,
    onToggleRunning: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit,
) {
    Div({ classes(*sized("timer", expanded)) }) {
        if (!loaded) {
            LoadingTimer(expanded)
            return@Div
        }

        ModeTabs(current = state.mode, onSwitchMode = onSwitchMode)
        TimerRing(state = state, settings = settings, expanded = expanded)
        SessionDots(state = state, settings = settings)
        TimerControls(
            state = state,
            expanded = expanded,
            onToggleRunning = onToggleRunning,
            onReset = onReset,
            onSkip = onSkip,
        )
    }
}

/**
 * What the card shows before the stored timer has arrived.
 *
 * The same shape as the real thing, with the countdown replaced by `--:--`. Drawing
 * a default of 25:00 and correcting it a moment later would tell the user something
 * untrue, and the correction would be the first thing they saw move.
 */
@Composable
private fun LoadingTimer(expanded: Boolean) {
    Div({ classes("timer__tabs") }) {
        TimerMode.entries.forEach { mode ->
            Span({ classes("timer__tab") }) { Text(mode.label) }
        }
    }

    Div({ classes(*sized("timer__ring", expanded)) }) {
        // Zero progress draws the empty track and nothing else, which is exactly
        // the skeleton: no special case needed for it.
        ProgressRing(progressPercent = 0.0, isBreak = false)

        Div({ classes("timer__readout") }) {
            Div({ classes("timer__time", "timer__time--placeholder", "font-mono-timer") }) {
                Text("--:--")
            }
            Div({ classes("timer__label") }) { Text("Loading") }
        }
    }

    // Hold the space the dots and the controls will take, so nothing below the card
    // jumps when the real timer arrives.
    Div({ classes("timer__dots-placeholder") })
    Div({ classes("timer__controls-placeholder") })
}

@Composable
private fun ModeTabs(current: TimerMode, onSwitchMode: (TimerMode) -> Unit) {
    Div({ classes("timer__tabs") }) {
        TimerMode.entries.forEach { mode ->
            val active = mode == current

            Button({
                classes(
                    *listOfNotNull(
                        "timer__tab",
                        "timer__tab--active".takeIf { active },
                        // The active tab takes the colour of the *current* mode, so
                        // the whole card reads as work or as rest at a glance.
                        "timer__tab--break".takeIf { active && current.isBreak },
                    ).toTypedArray(),
                )
                attr("aria-pressed", active.toString())
                onClick { onSwitchMode(mode) }
            }) {
                Text(mode.label)
            }
        }
    }
}

@Composable
private fun TimerRing(state: TimerState, settings: Settings, expanded: Boolean) {
    Div({ classes(*sized("timer__ring", expanded)) }) {
        ProgressRing(
            progressPercent = state.progressPercent(settings),
            isBreak = state.mode.isBreak,
        )

        Div({ classes("timer__readout") }) {
            Div({
                classes(
                    *listOfNotNull(
                        "timer__time",
                        "font-mono-timer",
                        // Breathing, not blinking: it marks the timer as live
                        // without pulling the eye back every second.
                        "timer__time--running".takeIf { state.running },
                    ).toTypedArray(),
                )
            }) {
                Text(formatCountdown(state.secondsLeft))
            }

            Div({ classes("timer__label") }) { Text(state.mode.label) }
        }
    }
}

/**
 * The ring: a full track with an arc drawn over however much of the stretch is gone.
 *
 * The markup is written once as a string rather than built from Compose elements,
 * because an SVG child has to be created in the SVG namespace and Compose HTML's
 * builders create HTML ones - an `<svg><circle>` built that way parses without
 * complaint and draws nothing at all.
 *
 * That leaves the arc to be updated by hand every second. It is done by setting two
 * attributes rather than by rewriting the markup, which matters: replacing the
 * element would restart it from empty, and the one-second tween that makes the ring
 * sweep rather than jump only happens when an existing element's value changes.
 *
 * The arc itself is a stroke dash exactly as long as the circumference, pulled back
 * by the fraction remaining. A quarter turn in CSS puts its start at twelve o'clock.
 */
@Composable
private fun ProgressRing(progressPercent: Double, isBreak: Boolean) {
    val offset = RING_CIRCUMFERENCE * (1 - progressPercent / PERCENT)
    val stroke = if (isBreak) "hsl(var(--secondary))" else "hsl(var(--primary))"

    Div({ classes("timer__svg") }) {
        DisposableEffect(Unit) {
            scopeElement.innerHTML = RING_MARKUP
            onDispose { }
        }

        // Runs immediately after the effect above on the first pass, before the
        // browser has painted, so a restored timer appears already filled rather
        // than sweeping up to its position.
        DisposableEffect(offset, stroke) {
            scopeElement.querySelector(".timer__arc")?.apply {
                setAttribute("stroke", stroke)
                setAttribute("stroke-dashoffset", offset.toString())
            }
            onDispose { }
        }
    }
}

private val RING_MARKUP =
    """<svg viewBox="0 0 200 200" aria-hidden="true" focusable="false">""" +
        """<circle cx="100" cy="100" r="$RING_RADIUS" fill="none" """ +
        """stroke="hsl(var(--accent))" stroke-width="$RING_WIDTH" />""" +
        """<circle class="timer__arc" cx="100" cy="100" r="$RING_RADIUS" fill="none" """ +
        """stroke-width="$RING_WIDTH" stroke-linecap="round" """ +
        """stroke-dasharray="$RING_CIRCUMFERENCE" """ +
        """stroke-dashoffset="$RING_CIRCUMFERENCE" /></svg>"""

@Composable
private fun SessionDots(state: TimerState, settings: Settings) {
    Div({ classes("timer__dots") }) {
        sessionDots(state.completedSessions, settings.longBreakInterval).forEach { done ->
            Div({
                classes(
                    *listOfNotNull("timer__dot", "timer__dot--done".takeIf { done })
                        .toTypedArray(),
                )
            })
        }
    }
}

@Composable
private fun TimerControls(
    state: TimerState,
    expanded: Boolean,
    onToggleRunning: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit,
) {
    val iconSize = if (expanded) SIDE_ICON_EXPANDED else SIDE_ICON

    Div({ classes("timer__controls") }) {
        Button({
            classes("timer__side-button")
            attr("aria-label", "Reset")
            onClick { onReset() }
        }) {
            Icon(LucideIcon.RotateCcw, size = iconSize)
        }

        Button({
            classes(
                *listOfNotNull(
                    "timer__play",
                    "timer__play--break".takeIf { state.mode.isBreak },
                ).toTypedArray(),
            )
            attr("aria-label", if (state.running) "Pause" else "Start")
            onClick { onToggleRunning() }
        }) {
            Icon(
                if (state.running) LucideIcon.Pause else LucideIcon.Play,
                size = if (expanded) PLAY_ICON_EXPANDED else PLAY_ICON,
                // A triangle is visually off-centre inside a circle; the square
                // pause glyph is not, so only one of them is nudged.
                className = if (state.running) null else "timer__play-glyph",
            )
        }

        Button({
            classes("timer__side-button")
            attr("aria-label", "Skip")
            onClick { onSkip() }
        }) {
            Icon(LucideIcon.SkipForward, size = iconSize)
        }
    }
}

private const val RING_RADIUS = 90
private const val RING_WIDTH = 6

/** The circumference at [RING_RADIUS], which the dash length and offset are measured in. */
private const val RING_CIRCUMFERENCE = 565.4867

private const val PERCENT = 100.0

private const val SIDE_ICON = 16
private const val SIDE_ICON_EXPANDED = 24
private const val PLAY_ICON = 24
private const val PLAY_ICON_EXPANDED = 36
