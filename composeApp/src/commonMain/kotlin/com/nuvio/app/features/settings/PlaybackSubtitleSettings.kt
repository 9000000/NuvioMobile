package com.nuvio.app.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.player.AndroidPlaybackEngine
import com.nuvio.app.features.player.AvailableLanguageOptions
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.SubtitleBackgroundColorSwatches
import com.nuvio.app.features.player.SubtitleColorSwatches
import com.nuvio.app.features.player.SubtitleLanguageOption
import com.nuvio.app.features.player.languageLabelForCode
import com.nuvio.app.features.player.subtitleFontSizeRangeSp
import com.nuvio.app.features.player.toStorageHexString
import com.nuvio.app.isIos
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.nuvio.app.features.player.PlayerSettingsUiState

@Composable
internal fun PlaybackSubtitleSettings(
    isTablet: Boolean,
    settings: PlayerSettingsUiState,
) {
    var showPreferredSubtitleDialog by remember { mutableStateOf(false) }
    var showSecondarySubtitleDialog by remember { mutableStateOf(false) }
    var showSubtitleTextColorDialog by remember { mutableStateOf(false) }
    var showSubtitleBackgroundColorDialog by remember { mutableStateOf(false) }
    var showSubtitleOutlineColorDialog by remember { mutableStateOf(false) }
    var showLibassRenderScaleDialog by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(if (isTablet) 18.dp else 12.dp)) {
        SettingsSection(
            title = stringResource(Res.string.settings_playback_page_subtitles),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_playback_preferred_subtitle_language),
                    description = when (settings.preferredSubtitleLanguage) {
                        SubtitleLanguageOption.NONE -> stringResource(Res.string.settings_playback_option_none)
                        SubtitleLanguageOption.DEVICE -> stringResource(Res.string.settings_playback_option_device_language)
                        SubtitleLanguageOption.FORCED -> stringResource(Res.string.settings_playback_option_forced)
                        else -> languageLabelForCode(settings.preferredSubtitleLanguage)
                    },
                    enabled = !settings.externalPlayerEnabled || settings.externalPlayerForwardSubtitles,
                    isTablet = isTablet,
                    onClick = { showPreferredSubtitleDialog = true },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_playback_secondary_subtitle_language),
                    description = languageLabelForCode(settings.secondaryPreferredSubtitleLanguage),
                    enabled = !settings.externalPlayerEnabled || settings.externalPlayerForwardSubtitles,
                    isTablet = isTablet,
                    onClick = { showSecondarySubtitleDialog = true },
                )
                if (!isIos) {
                    SettingsGroupDivider(isTablet = isTablet)
                    AutoSyncPlaybackSettingsRows(
                        isTablet = isTablet,
                        enabled = !settings.externalPlayerEnabled,
                        preferredSubtitleLanguage = settings.preferredSubtitleLanguage,
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_playback_subtitle_strip_sdh),
                    description = stringResource(Res.string.settings_playback_subtitle_strip_sdh_description),
                    checked = settings.subtitleStyle.stripSdh,
                    enabled = !settings.externalPlayerEnabled,
                    isTablet = isTablet,
                    onCheckedChange = { enabled ->
                        PlayerSettingsRepository.setSubtitleStyle(
                            settings.subtitleStyle.copy(stripSdh = enabled),
                        )
                    },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_playback_subtitle_use_forced),
                    description = stringResource(Res.string.settings_playback_subtitle_use_forced_description),
                    checked = settings.subtitleStyle.useForcedSubtitles,
                    enabled = !settings.externalPlayerEnabled,
                    isTablet = isTablet,
                    onCheckedChange = { enabled ->
                        PlayerSettingsRepository.setSubtitleStyle(
                            settings.subtitleStyle.copy(useForcedSubtitles = enabled),
                        )
                    },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_playback_subtitle_show_preferred_only),
                    description = stringResource(Res.string.settings_playback_subtitle_show_preferred_only_description),
                    checked = settings.subtitleStyle.showOnlyPreferredLanguages,
                    enabled = !settings.externalPlayerEnabled,
                    isTablet = isTablet,
                    onCheckedChange = { enabled ->
                        PlayerSettingsRepository.setSubtitleStyle(
                            settings.subtitleStyle.copy(showOnlyPreferredLanguages = enabled),
                        )
                    },
                )
            }
        }

        SettingsSection(
            title = stringResource(Res.string.settings_playback_section_subtitle_rendering),
            isTablet = isTablet,
        ) {
            val subtitleRenderingEnabled = !settings.externalPlayerEnabled
            SettingsGroup(isTablet = isTablet) {
                val subtitleStyle = settings.subtitleStyle
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_playback_subtitle_size),
                    value = subtitleStyle.fontSizeSp,
                    valueText = { size -> stringResource(Res.string.compose_player_font_size_value, size) },
                    valueRange = subtitleFontSizeRangeSp,
                    step = 2,
                    isTablet = isTablet,
                    enabled = subtitleRenderingEnabled,
                    onValueChange = { value ->
                        PlayerSettingsRepository.setSubtitleStyle(subtitleStyle.copy(fontSizeSp = value))
                    },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_playback_subtitle_vertical_offset),
                    value = subtitleStyle.bottomOffset,
                    valueText = { offset -> offset.toString() },
                    valueRange = 0..200,
                    step = 5,
                    isTablet = isTablet,
                    enabled = subtitleRenderingEnabled,
                    onValueChange = { value ->
                        PlayerSettingsRepository.setSubtitleStyle(subtitleStyle.copy(bottomOffset = value))
                    },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_playback_subtitle_bold),
                    description = stringResource(Res.string.settings_playback_subtitle_bold_description),
                    checked = subtitleStyle.bold,
                    enabled = subtitleRenderingEnabled,
                    isTablet = isTablet,
                    onCheckedChange = { enabled ->
                        PlayerSettingsRepository.setSubtitleStyle(subtitleStyle.copy(bold = enabled))
                    },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_playback_subtitle_text_color),
                    description = subtitleColorLabel(subtitleStyle.textColor),
                    enabled = subtitleRenderingEnabled,
                    isTablet = isTablet,
                    onClick = { showSubtitleTextColorDialog = true },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_playback_subtitle_background_color),
                    description = subtitleColorLabel(subtitleStyle.backgroundColor),
                    enabled = subtitleRenderingEnabled,
                    isTablet = isTablet,
                    onClick = { showSubtitleBackgroundColorDialog = true },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_playback_subtitle_outline),
                    description = stringResource(Res.string.settings_playback_subtitle_outline_description),
                    checked = subtitleStyle.outlineEnabled,
                    enabled = subtitleRenderingEnabled,
                    isTablet = isTablet,
                    onCheckedChange = { enabled ->
                        PlayerSettingsRepository.setSubtitleStyle(subtitleStyle.copy(outlineEnabled = enabled))
                    },
                )
                if (subtitleStyle.outlineEnabled) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_playback_subtitle_outline_color),
                        description = subtitleColorLabel(subtitleStyle.outlineColor),
                        enabled = subtitleRenderingEnabled,
                        isTablet = isTablet,
                        onClick = { showSubtitleOutlineColorDialog = true },
                    )
                }
                val showLibassSettings = !isIos && settings.androidPlaybackEngine != AndroidPlaybackEngine.Libmpv
                if (showLibassSettings) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_playback_enable_libass),
                        description = stringResource(Res.string.settings_playback_enable_libass_description),
                        checked = settings.useLibass,
                        enabled = subtitleRenderingEnabled,
                        isTablet = isTablet,
                        onCheckedChange = PlayerSettingsRepository::setUseLibass,
                    )
                    if (settings.useLibass) {
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.settings_playback_render_scale),
                            description = settings.libassRenderScale,
                            enabled = subtitleRenderingEnabled,
                            isTablet = isTablet,
                            onClick = { showLibassRenderScaleDialog = true },
                        )
                    }
                }
            }
        }
    }

    if (showPreferredSubtitleDialog) {
        LanguageSelectionDialog(
            title = stringResource(Res.string.settings_playback_preferred_subtitle_language),
            options = listOf(
                LanguageSelectionOption(SubtitleLanguageOption.NONE, stringResource(Res.string.settings_playback_option_none)),
                LanguageSelectionOption(SubtitleLanguageOption.DEVICE, stringResource(Res.string.settings_playback_option_device_language)),
                LanguageSelectionOption(SubtitleLanguageOption.FORCED, stringResource(Res.string.settings_playback_option_forced)),
            ) + AvailableLanguageOptions.map { option ->
                LanguageSelectionOption(option.code, stringResource(option.labelRes))
            },
            selectedValue = settings.preferredSubtitleLanguage,
            onSelect = { value ->
                PlayerSettingsRepository.setPreferredSubtitleLanguage(value ?: SubtitleLanguageOption.NONE)
                showPreferredSubtitleDialog = false
            },
            onDismiss = { showPreferredSubtitleDialog = false },
        )
    }

    if (showSecondarySubtitleDialog) {
        LanguageSelectionDialog(
            title = stringResource(Res.string.settings_playback_secondary_subtitle_language),
            options = listOf(
                LanguageSelectionOption(null, stringResource(Res.string.settings_playback_option_none)),
                LanguageSelectionOption(SubtitleLanguageOption.FORCED, stringResource(Res.string.settings_playback_option_forced)),
            ) + AvailableLanguageOptions.map { option ->
                LanguageSelectionOption(option.code, stringResource(option.labelRes))
            },
            selectedValue = settings.secondaryPreferredSubtitleLanguage,
            onSelect = { value ->
                PlayerSettingsRepository.setSecondaryPreferredSubtitleLanguage(value)
                showSecondarySubtitleDialog = false
            },
            onDismiss = { showSecondarySubtitleDialog = false },
        )
    }

    if (showSubtitleTextColorDialog) {
        SubtitleColorDialog(
            title = stringResource(Res.string.settings_playback_subtitle_text_color),
            colors = SubtitleColorSwatches,
            selectedColor = settings.subtitleStyle.textColor,
            onColorSelected = { color ->
                PlayerSettingsRepository.setSubtitleStyle(settings.subtitleStyle.copy(textColor = color))
                showSubtitleTextColorDialog = false
            },
            onDismiss = { showSubtitleTextColorDialog = false },
        )
    }

    if (showSubtitleBackgroundColorDialog) {
        SubtitleColorDialog(
            title = stringResource(Res.string.settings_playback_subtitle_background_color),
            colors = SubtitleBackgroundColorSwatches,
            selectedColor = settings.subtitleStyle.backgroundColor,
            onColorSelected = { color ->
                PlayerSettingsRepository.setSubtitleStyle(settings.subtitleStyle.copy(backgroundColor = color))
                showSubtitleBackgroundColorDialog = false
            },
            onDismiss = { showSubtitleBackgroundColorDialog = false },
        )
    }

    if (showSubtitleOutlineColorDialog) {
        SubtitleColorDialog(
            title = stringResource(Res.string.settings_playback_subtitle_outline_color),
            colors = SubtitleColorSwatches,
            selectedColor = settings.subtitleStyle.outlineColor,
            onColorSelected = { color ->
                PlayerSettingsRepository.setSubtitleStyle(settings.subtitleStyle.copy(outlineColor = color))
                showSubtitleOutlineColorDialog = false
            },
            onDismiss = { showSubtitleOutlineColorDialog = false },
        )
    }

    if (showLibassRenderScaleDialog) {
        LibassRenderScaleDialog(
            selectedScale = settings.libassRenderScale,
            onScaleSelected = { scale ->
                PlayerSettingsRepository.setLibassRenderScale(scale)
                showLibassRenderScaleDialog = false
            },
            onDismiss = { showLibassRenderScaleDialog = false },
        )
    }
}

@Composable
private fun subtitleColorLabel(color: Color): String {
    return if (color.alpha == 0f) {
        stringResource(Res.string.settings_playback_subtitle_color_transparent)
    } else {
        color.toStorageHexString()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LibassRenderScaleDialog(
    selectedScale: String,
    onScaleSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        "FULL" to "1:1",
        "THREE_QUARTER" to "\u00BE",
        "HALF" to "\u00BD",
        "THIRD" to "\u2153",
        "QUARTER" to "\u00BC",
    )

    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.settings_playback_render_scale),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selectedScale
                DialogOption(
                    text = label,
                    selected = isSelected,
                    onClick = { onScaleSelected(value) },
                )
            }
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_done),
                onClick = onDismiss,
            )
        }
    }
}



@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SubtitleColorDialog(
    title: String,
    colors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    DialogSurface(
        onDismissRequest = onDismiss,
        title = title,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            colors.forEach { color ->
                DialogOption(
                    text = subtitleColorLabel(color),
                    selected = selectedColor.toStorageHexString() == color.toStorageHexString(),
                    onClick = { onColorSelected(color) },
                    leading = {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (color.alpha == 0f) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                color
                            },
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            ),
                        ) {}
                    },
                )
            }
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_done),
                onClick = onDismiss,
            )
        }
    }
}
