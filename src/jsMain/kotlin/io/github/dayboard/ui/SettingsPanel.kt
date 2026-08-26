package io.github.dayboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.bchmsl.keel.color.SwatchShade
import io.github.bchmsl.keel.color.Swatches
import io.github.bchmsl.keel.components.Button
import io.github.bchmsl.keel.components.ButtonSize
import io.github.bchmsl.keel.components.ButtonVariant
import io.github.bchmsl.keel.components.Slider
import io.github.bchmsl.keel.components.Switch
import io.github.bchmsl.keel.components.TextField
import io.github.bchmsl.keel.dom.classNames
import io.github.bchmsl.keel.icons.Icon
import io.github.bchmsl.keel.icons.LucideIcon
import io.github.bchmsl.keel.theme.ColorMode
import io.github.bchmsl.keel.theme.ThemeController
import io.github.dayboard.data.NotificationController
import io.github.dayboard.data.SettingsController
import io.github.dayboard.data.TagsController
import io.github.dayboard.domain.model.NotificationPermission
import io.github.dayboard.domain.model.SettingRange
import io.github.dayboard.domain.model.Settings
import io.github.dayboard.domain.model.TAG_EMOJI_MAX_LENGTH
import io.github.dayboard.domain.model.Tag
import io.github.dayboard.domain.model.background
import io.github.dayboard.ui.cards.ICON_SMALL
import io.github.dayboard.ui.cards.ICON_TINY
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.maxLength
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLInputElement
import org.jetbrains.compose.web.dom.Button as RawButton

/**
 * Everything the user can configure, in a panel that slides in from the right.
 *
 * There is no Save button anywhere in it, and that is the point: every control
 * writes as it is moved. A settings screen you can leave without committing is a
 * settings screen you can leave in a state you did not mean.
 *
 * Sections that configure something switched off are not shown. The timer durations
 * are meaningless with the timer card hidden, and offering them would be offering a
 * setting with no effect.
 */
@Composable
fun SettingsPanel(
    settings: SettingsController,
    theme: ThemeController,
    tags: TagsController,
    notifications: NotificationController,
    onDeleteTag: (String) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val current = settings.settings

    Div({
        classes("panel__scrim")
        onClick { onDismiss() }
    })

    Div({
        classes("panel")
        attr("role", "dialog")
        attr("aria-modal", "true")
        attr("aria-label", "Settings")
        // The scrim is behind this, so a click inside would otherwise bubble out to
        // it and close the panel being used.
        onClick { event -> event.stopPropagation() }
    }) {
        Div({ classes("panel__header") }) {
            H2({ classes("panel__title") }) { Text("Settings") }
            RawButton({
                classes("panel__close")
                attr("aria-label", "Close")
                onClick { onDismiss() }
            }) {
                Icon(LucideIcon.X, size = ICON_SMALL)
            }
        }

        Div({ classes("panel__body") }) {
            WidgetsSection(current, settings)
            ClockSection(current, settings)
            WeatherSection(current, settings)

            if (current.showPomodoro) {
                DurationsSection(current, settings)
                AutomationSection(current, settings)
            }

            SoundSection(current, settings)

            if (tags.all.isNotEmpty()) {
                TagsSettingsSection(tags = tags, onDeleteTag = onDeleteTag)
            }

            AppearanceSection(theme = theme, settings = settings)

            // Hidden rather than disabled where the browser cannot show a
            // notification at all: a control that explains why it will not work is
            // worse than one that was never offered.
            if (notifications.supported) {
                NotificationsSection(notifications)
            }

            Div({ classes("panel__sign-out") }) {
                RawButton({
                    classes("panel__sign-out-button")
                    onClick { onSignOut() }
                }) {
                    Icon(LucideIcon.LogOut, size = ICON_TINY)
                    Text("Sign out")
                }
            }
        }
    }
}

// ------------------------------------------------------------------- sections

@Composable
private fun WidgetsSection(current: Settings, settings: SettingsController) {
    Section(LucideIcon.Monitor, "Widgets") {
        ToggleRow(
            label = "Pomodoro Timer",
            description = "Show the Pomodoro timer card",
            checked = current.showPomodoro,
        ) { settings.update { it.copy(showPomodoro = !it.showPomodoro) } }

        ToggleRow(
            label = "Tasks",
            description = "Show the tasks card",
            checked = current.showTasks,
        ) { settings.update { it.copy(showTasks = !it.showTasks) } }

        ToggleRow(
            label = "Notes",
            description = "Show the notes card",
            checked = current.showNotes,
        ) { settings.update { it.copy(showNotes = !it.showNotes) } }
    }
}

@Composable
private fun ClockSection(current: Settings, settings: SettingsController) {
    Section(LucideIcon.Clock, "Clock Settings") {
        ToggleRow(
            label = "Show Seconds",
            description = "Display seconds in the clock (HH:mm:ss)",
            checked = current.showSeconds,
        ) { settings.update { it.copy(showSeconds = !it.showSeconds) } }
    }
}

@Composable
private fun WeatherSection(current: Settings, settings: SettingsController) {
    Section(LucideIcon.CloudSun, "Weather") {
        ToggleRow(
            label = "Show Weather",
            description = "Display weather info on the clock card",
            checked = current.showWeather,
        ) { settings.update { it.copy(showWeather = !it.showWeather) } }

        // Only when there is weather to place. A city for a hidden card is a setting
        // with nothing to act on.
        if (current.showWeather) {
            CityField(
                city = current.weatherCity,
                onCommit = { city -> settings.update { it.copy(weatherCity = city) } },
            )
        }
    }
}

/**
 * The city the weather is looked up for, or nothing for "wherever this is".
 *
 * Uncontrolled and committed on blur or Enter, rather than on every keystroke:
 * typing "London" one letter at a time would otherwise send five lookups, four of
 * them for places nobody asked about.
 */
@Composable
private fun CityField(city: String?, onCommit: (String?) -> Unit) {
    key(city) {
        Div({ classes("panel__field") }) {
            Div({ classes("panel__field-row") }) {
                Input(InputType.Text) {
                    classes("input")
                    placeholder("e.g. Tokyo, London...")
                    attr("aria-label", "Weather city")
                    ref { element ->
                        element.value = city.orEmpty()
                        onDispose { }
                    }
                    onKeyDown { event ->
                        if (event.key == "Enter") {
                            event.preventDefault()
                            (event.target as? HTMLInputElement)?.blur()
                        }
                    }
                    onBlur { event ->
                        val typed = (event.target as? HTMLInputElement)?.value.orEmpty()
                        onCommit(typed.trim().ifEmpty { null })
                    }
                }

                // Only worth offering when there is something to clear.
                if (city != null) {
                    RawButton({
                        classes("panel__auto-button")
                        attr("aria-label", "Detect my location")
                        onClick { onCommit(null) }
                    }) {
                        Icon(LucideIcon.MapPin, size = ICON_TINY)
                        Text("Auto")
                    }
                }
            }

            Div({ classes("panel__hint") }) { Text("Leave empty for auto-detect") }
        }
    }
}

@Composable
private fun DurationsSection(current: Settings, settings: SettingsController) {
    Section(LucideIcon.Clock, "Timer Durations") {
        SliderRow("Focus", current.focusDuration, SettingRange.FocusDuration) { value ->
            settings.update { it.copy(focusDuration = value) }
        }
        SliderRow(
            "Short Break",
            current.shortBreakDuration,
            SettingRange.ShortBreakDuration,
        ) { value ->
            settings.update { it.copy(shortBreakDuration = value) }
        }
        SliderRow(
            "Long Break",
            current.longBreakDuration,
            SettingRange.LongBreakDuration,
        ) { value ->
            settings.update { it.copy(longBreakDuration = value) }
        }
        SliderRow(
            "Long Break Every",
            current.longBreakInterval,
            SettingRange.LongBreakInterval,
        ) { value ->
            settings.update { it.copy(longBreakInterval = value) }
        }
    }
}

@Composable
private fun AutomationSection(current: Settings, settings: SettingsController) {
    Section(LucideIcon.Zap, "Automation") {
        ToggleRow(
            label = "Auto-start Breaks",
            description = "Automatically start break timer after focus ends",
            checked = current.autoStartBreaks,
        ) { settings.update { it.copy(autoStartBreaks = !it.autoStartBreaks) } }

        ToggleRow(
            label = "Auto-start Focus",
            description = "Automatically start focus timer after break ends",
            checked = current.autoStartFocus,
        ) { settings.update { it.copy(autoStartFocus = !it.autoStartFocus) } }
    }
}

@Composable
private fun SoundSection(current: Settings, settings: SettingsController) {
    // The icon says which state the section is in before any of it is read.
    Section(if (current.soundEnabled) LucideIcon.Volume2 else LucideIcon.VolumeX, "Sound") {
        ToggleRow(
            label = "Notification Sound",
            description = "Play a sound when a timer session ends",
            checked = current.soundEnabled,
        ) { settings.update { it.copy(soundEnabled = !it.soundEnabled) } }

        if (current.soundEnabled) {
            SliderRow("Volume", current.soundVolume, SettingRange.SoundVolume) { value ->
                settings.update { it.copy(soundVolume = value) }
            }
        }
    }
}

@Composable
private fun AppearanceSection(theme: ThemeController, settings: SettingsController) {
    Section(LucideIcon.Palette, "Appearance") {
        Div({ classes("panel__label") }) { Text("Theme") }

        Div({ classes("panel__themes") }) {
            theme.catalog.themes.forEach { option ->
                val on = option == theme.theme

                RawButton({
                    classes(*listOfNotNull("theme", "theme--on".takeIf { on }).toTypedArray())
                    attr("aria-pressed", on.toString())
                    onClick {
                        // Written to both: the browser's copy paints instantly on the
                        // next load, before there is an account to ask, and the
                        // account's copy is what another device reads.
                        theme.setTheme(option)
                        settings.update { it.copy(themeId = option.id) }
                    }
                }) {
                    Span({
                        classes("theme__dot")
                        style { property("background-color", option.accentHex) }
                    })
                    Text(option.label)
                }
            }
        }

        Div({ classes("panel__label") }) { Text("Mode") }

        Div({ classes("panel__modes") }) {
            ColorMode.entries.forEach { option ->
                val on = option == theme.colorMode

                RawButton({
                    classes(*listOfNotNull("mode", "mode--on".takeIf { on }).toTypedArray())
                    attr("aria-pressed", on.toString())
                    onClick {
                        theme.setColorMode(option)
                        settings.update { it.copy(colorMode = option) }
                    }
                }) {
                    Icon(option.icon, size = ICON_TINY)
                    Text(option.label)
                }
            }
        }
    }
}

/**
 * Turning timer notifications on.
 *
 * There is no way to turn them off here, and that is deliberate: permission belongs
 * to the browser, and a switch that appeared to revoke it while the browser went on
 * allowing notifications would be a lie. Browsers put the real control in the site
 * settings, and say so.
 */
@Composable
private fun NotificationsSection(notifications: NotificationController) {
    Section(LucideIcon.Bell, "Notifications") {
        when (notifications.permission) {
            NotificationPermission.Granted -> Div({ classes("panel__enabled") }) {
                Icon(LucideIcon.Bell, size = ICON_TINY)
                Text("Notifications enabled")
            }

            // Asking again does nothing - a browser will not prompt twice - so this
            // says where the control actually is rather than offering a dead button.
            NotificationPermission.Denied -> Div({ classes("panel__row-description") }) {
                Text("Blocked by this browser. Allow notifications in its site settings.")
            }

            NotificationPermission.Default -> RawButton({
                classes("panel__enable-button")
                onClick { notifications.enable() }
            }) {
                Icon(LucideIcon.BellOff, size = ICON_TINY)
                Text("Enable notifications")
            }
        }
    }
}

// ---------------------------------------------------------------------- tags

/**
 * Renaming and removing tags.
 *
 * Not creating them: a tag is made where it is first needed, on a task or a note,
 * and a tag with nothing on it would be a tag nobody could find a use for.
 */
@Composable
private fun TagsSettingsSection(tags: TagsController, onDeleteTag: (String) -> Unit) {
    var editingId: String? by remember { mutableStateOf(null) }
    var confirmingId: String? by remember { mutableStateOf(null) }

    Section(LucideIcon.Tag, "Tags") {
        tags.all.forEach { tag ->
            key(tag.id) {
                if (tag.id == editingId) {
                    TagEditor(
                        tag = tag,
                        onCancel = { editingId = null },
                        onSave = { name, color, emoji ->
                            tags.update(tag.id, name, color, emoji)
                            editingId = null
                        },
                    )
                } else {
                    TagRow(
                        tag = tag,
                        confirming = tag.id == confirmingId,
                        onEdit = {
                            confirmingId = null
                            editingId = tag.id
                        },
                        onAskDelete = { confirmingId = tag.id },
                        onCancelDelete = { confirmingId = null },
                        onDelete = {
                            confirmingId = null
                            onDeleteTag(tag.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagRow(
    tag: Tag,
    confirming: Boolean,
    onEdit: () -> Unit,
    onAskDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onDelete: () -> Unit,
) {
    Div({ classes("panel__tag-row") }) {
        Span({
            classes("pill")
            style {
                property("background-color", tag.background(SwatchShade.Pill))
                property("color", tag.color)
            }
        }) {
            tag.emoji?.let { Span({ classes("pill__emoji") }) { Text(it) } }
            Text(tag.name)
        }

        if (confirming) {
            RawButton({
                classes("panel__tag-danger")
                onClick { onDelete() }
            }) {
                Text("Delete")
            }
            RawButton({
                classes("panel__tag-cancel")
                onClick { onCancelDelete() }
            }) {
                Text("No")
            }
        } else {
            RawButton({
                classes("panel__tag-action")
                attr("aria-label", "Edit tag ${tag.name}")
                onClick { onEdit() }
            }) {
                Icon(LucideIcon.Pencil, size = ICON_TINY)
            }
            RawButton({
                classes("panel__tag-action", "panel__tag-action--danger")
                attr("aria-label", "Delete tag ${tag.name}")
                onClick { onAskDelete() }
            }) {
                Icon(LucideIcon.Trash2, size = ICON_TINY)
            }
        }
    }
}

@Composable
private fun TagEditor(
    tag: Tag,
    onCancel: () -> Unit,
    onSave: (name: String, color: String, emoji: String?) -> Unit,
) {
    var name by remember { mutableStateOf(tag.name) }
    var emoji by remember { mutableStateOf(tag.emoji.orEmpty()) }
    var color by remember { mutableStateOf(tag.color) }

    Div({ classes("creator") }) {
        Div({ classes("creator__row") }) {
            TextField(
                value = emoji,
                onValueChange = { emoji = it },
                placeholder = "\uD83D\uDE0A",
                ariaLabel = "Tag emoji",
                attrs = {
                    classNames("creator__emoji")
                    maxLength(TAG_EMOJI_MAX_LENGTH)
                },
            )

            TextField(
                value = name,
                onValueChange = { name = it },
                ariaLabel = "Tag name",
                attrs = {
                    onKeyDown { event ->
                        when (event.key) {
                            "Enter" -> if (name.isNotBlank()) {
                                event.preventDefault()
                                onSave(name, color, emoji)
                            }

                            "Escape" -> onCancel()
                        }
                    }
                },
            )
        }

        Div({ classes("creator__colors") }) {
            Swatches.All.forEach { swatch ->
                // `RawButton` is Compose HTML's own `Button`, aliased because keel's is
                // imported here too. A colour swatch is a 1rem circle with no label and
                // no ink, which keel has no component for. The panel's other raw buttons
                // above are not that - they are simply not on keel yet.
                RawButton({
                    classes(
                        *listOfNotNull("swatch", "swatch--on".takeIf { swatch == color })
                            .toTypedArray(),
                    )
                    attr("aria-label", "Colour $swatch")
                    attr("aria-pressed", (swatch == color).toString())
                    style { property("background-color", swatch) }
                    onClick { color = swatch }
                })
            }
        }

        Div({ classes("creator__row") }) {
            Button(
                label = "Save",
                onClick = { onSave(name, color, emoji) },
                size = ButtonSize.ExtraSmall,
                // A tag with no name is a pill nobody could tell from another.
                enabled = name.isNotBlank(),
            )
            Button(
                label = "Cancel",
                onClick = onCancel,
                variant = ButtonVariant.Quiet,
                size = ButtonSize.ExtraSmall,
            )
        }
    }
}

// ------------------------------------------------------------------- pieces

@Composable
private fun Section(icon: LucideIcon, title: String, content: @Composable () -> Unit) {
    Div({ classes("panel__section") }) {
        Div({ classes("panel__section-title") }) {
            Icon(icon, size = ICON_TINY, className = "panel__section-icon")
            Text(title)
        }
        content()
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Div({ classes("panel__row") }) {
        Div({ classes("panel__row-text") }) {
            Div({ classes("panel__row-label") }) { Text(label) }
            Div({ classes("panel__row-description") }) { Text(description) }
        }
        Switch(checked = checked, onCheckedChange = { onToggle() }, ariaLabel = label)
    }
}

@Composable
private fun SliderRow(label: String, value: Int, range: SettingRange, onChange: (Int) -> Unit) {
    Div({ classes("panel__slider-row") }) {
        Div({ classes("panel__slider-head") }) {
            Span({ classes("panel__row-label") }) { Text(label) }
            // Tabular figures, so the number does not shift the label about as the
            // slider moves through 9, 10, 11.
            Span({ classes("panel__slider-value") }) { Text(range.label(value)) }
        }
        Slider(
            value = range.clamp(value),
            min = range.min,
            max = range.max,
            onValueChange = onChange,
            ariaLabel = label,
        )
    }
}

/** The picture for each colour mode, matching what the mode actually follows. */
private val ColorMode.icon: LucideIcon
    get() = when (this) {
        ColorMode.Light -> LucideIcon.Sun
        ColorMode.Dark -> LucideIcon.Moon
        ColorMode.System -> LucideIcon.Monitor
    }
