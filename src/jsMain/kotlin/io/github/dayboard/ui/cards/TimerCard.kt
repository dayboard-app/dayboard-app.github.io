package io.github.dayboard.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import io.github.bchmsl.keel.components.ButtonShape
import io.github.bchmsl.keel.components.ButtonSize
import io.github.bchmsl.keel.components.ButtonVariant
import io.github.bchmsl.keel.components.IconButton
import io.github.bchmsl.keel.components.Segment
import io.github.bchmsl.keel.components.SegmentedControl
import io.github.bchmsl.keel.icons.Icon
import io.github.bchmsl.keel.icons.LucideIcon
import io.github.dayboard.domain.model.Settings
import io.github.dayboard.domain.model.TimerMode
import io.github.dayboard.domain.model.TimerState
import io.github.dayboard.domain.model.progressPercent
import io.github.dayboard.domain.model.sessionDots
import io.github.dayboard.presentation.formatCountdown
import org.jetbrains.compose.web.dom.Div
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
    // An empty box at the strip's height, like the two below. The placeholder used to
    // be the tab markup written again as inert spans, which is no longer available:
    // the strip is keel's `SegmentedControl` and spelling its classes out here to get
    // a dead copy is exactly what this migration is removing. A real one would be
    // worse than either - focusable and clickable, and discarding both.
    Div({ classes("timer__tabs-placeholder") })

    Div({ classes(*sized("timer__ring", expanded)) }) {
        // Zero progress draws the empty track and nothing else, which is exactly
        // the skeleton: no special case needed for it.
        ProgressRing(progressPercent = 0.0, isBreak = false)

        Div({ classes("timer__readout") }) {
            Div({ classes("timer__time", "timer__time--placeholder", "font-mono") }) {
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

/**
 * The three modes.
 *
 * keel's `SegmentedControl`, which is what this strip was: the same `--muted` track,
 * the same lift on the chosen one, at a radius and padding that were never decisions.
 * What it adds is a real radio group - the strip is one tab stop and the arrow keys
 * move between the three, neither of which a row of buttons gave.
 *
 * The chosen tab no longer takes the colour of the mode it selects. keel draws one
 * selected look for every segment, and a tone axis for this one call site is not worth
 * it: the ring's arc and the play button both still turn `--secondary` on a break, and
 * the mode's name is written under the countdown.
 */
@Composable
private fun ModeTabs(current: TimerMode, onSwitchMode: (TimerMode) -> Unit) {
    SegmentedControl(
        segments = TimerMode.entries.map { Segment(it, it.label) },
        selected = current,
        onSelect = onSwitchMode,
        ariaLabel = "Mode",
    )
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
                        "font-mono",
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
        // Both side buttons are keel's `Quiet` at `Circle`, which is the local rule
        // exactly: the same muted ink, the same `--muted` fill and full-strength ink
        // under the pointer.
        SideButton(
            ariaLabel = "Reset",
            icon = LucideIcon.RotateCcw,
            iconSize = iconSize,
            expanded = expanded,
            onClick = onReset,
        )

        // `Transport` and `Circle`: the same 3.5rem circle this drew by hand, and the
        // reason both exist in keel now. `Secondary` on a break is `--secondary` over
        // `--secondary-foreground`, which is what `--break` said.
        IconButton(
            ariaLabel = if (state.running) "Pause" else "Start",
            onClick = onToggleRunning,
            variant = if (state.mode.isBreak) ButtonVariant.Secondary else ButtonVariant.Default,
            // Expanded, this is the only thing on the screen, which is what the larger
            // transport size is for.
            size = if (expanded) ButtonSize.TransportLarge else ButtonSize.Transport,
            shape = ButtonShape.Circle,
        ) {
            Icon(
                if (state.running) LucideIcon.Pause else LucideIcon.Play,
                size = if (expanded) PLAY_ICON_EXPANDED else PLAY_ICON,
                // A triangle is visually off-centre inside a circle; the square
                // pause glyph is not, so only one of them is nudged.
                className = if (state.running) null else "timer__play-glyph",
            )
        }

        SideButton(
            ariaLabel = "Skip",
            icon = LucideIcon.SkipForward,
            iconSize = iconSize,
            expanded = expanded,
            onClick = onSkip,
        )
    }
}

/**
 * Reset or skip.
 *
 * Expanded, both grow from 2.5rem to 3.5rem, which is keel's `Transport` - so the
 * expanded side buttons and the inline play button are the same tier, which is what
 * they always were. It used to be two width-and-height overrides under
 * `.timer--expanded`, which would now mean a consumer sheet resizing keel's button
 * from outside.
 */
@Composable
private fun SideButton(
    ariaLabel: String,
    icon: LucideIcon,
    iconSize: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        ariaLabel = ariaLabel,
        onClick = onClick,
        variant = ButtonVariant.Quiet,
        size = if (expanded) ButtonSize.Transport else ButtonSize.Icon,
        shape = ButtonShape.Circle,
    ) {
        Icon(icon, size = iconSize)
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
