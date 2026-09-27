package com.github.damontecres.wholphin.tvmode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.github.damontecres.wholphin.tvmode.SUBTITLE_LANGUAGES
import com.github.damontecres.wholphin.tvmode.TvModeSettings

/** TV mode settings: OK on a row cycles its value. */
@Composable
fun SettingsDialog(
    settings: TvModeSettings,
    onChange: ((TvModeSettings) -> TvModeSettings) -> Unit,
    onDismiss: () -> Unit,
    customThemes: List<com.github.damontecres.wholphin.tvmode.CustomTheme> = emptyList(),
    onEditThemes: () -> Unit = {},
) {
    val theme = LocalTvTheme.current
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(560.dp)
                .background(theme.panel, RoundedCornerShape(8.dp))
                .border(1.dp, theme.divider, RoundedCornerShape(8.dp))
                .padding(20.dp),
        ) {
            TvText("TV MODE SETTINGS", size = 20.sp, bold = true, color = theme.accent)
            Spacer(Modifier.height(12.dp))
            LazyColumn {
                item {
                    val custom = customThemes.firstOrNull { it.id == settings.customTheme }
                    SettingRow("Theme", custom?.name ?: settings.theme.label, Modifier.focusRequester(first)) {
                        // Built-in themes, then custom ones, then round again.
                        onChange {
                            val index = customThemes.indexOfFirst { c -> c.id == it.customTheme }
                            when {
                                index >= 0 && index < customThemes.size - 1 -> {
                                    it.copy(customTheme = customThemes[index + 1].id)
                                }

                                index >= 0 -> {
                                    it.copy(
                                        customTheme = -1,
                                        theme =
                                            com.github.damontecres.wholphin.tvmode.TvThemeId.entries
                                                .first(),
                                    )
                                }

                                it.theme.ordinal < com.github.damontecres.wholphin.tvmode.TvThemeId.entries.size - 1 -> {
                                    it.copy(
                                        theme = it.theme.next(),
                                    )
                                }

                                customThemes.isNotEmpty() -> {
                                    it.copy(customTheme = customThemes.first().id)
                                }

                                else -> {
                                    it.copy(theme = it.theme.next())
                                }
                            }
                        }
                    }
                }
                item { SettingRow("Edit themes", "Open ›") { onEditThemes() } }
                item { SettingRow("Interface sounds", onOff(settings.uiSounds)) { onChange { it.copy(uiSounds = !it.uiSounds) } } }
                item { SettingRow("Subtitles (CC key)", onOff(settings.subtitles)) { onChange { it.copy(subtitles = !it.subtitles) } } }
                item {
                    val languages = SUBTITLE_LANGUAGES
                    val at = languages.indexOfFirst { it.first == settings.subtitleLanguage }.coerceAtLeast(0)
                    SettingRow("Subtitle language", languages[at].second) {
                        onChange { it.copy(subtitleLanguage = languages[(at + 1) % languages.size].first) }
                    }
                }
                item {
                    SettingRow(
                        "Player overlay",
                        settings.overlayStyle.label,
                    ) { onChange { it.copy(overlayStyle = it.overlayStyle.next()) } }
                }
                item {
                    SettingRow(
                        "Tuning screen",
                        settings.tuningStyle.label,
                    ) { onChange { it.copy(tuningStyle = it.tuningStyle.next()) } }
                }
                item {
                    SettingRow(
                        "Channel logo while tuning",
                        onOff(settings.tuningLogo),
                    ) { onChange { it.copy(tuningLogo = !it.tuningLogo) } }
                }
                item {
                    SettingRow(
                        "Channel column",
                        settings.channelColumn.label,
                    ) { onChange { it.copy(channelColumn = it.channelColumn.next()) } }
                }
                item {
                    SettingRow(
                        "Detailed guide (two-line cells)",
                        onOff(settings.detailedGuide),
                    ) { onChange { it.copy(detailedGuide = !it.detailedGuide) } }
                }
                item {
                    SettingRow("Colour cells by type (movie, show, kids)", onOff(settings.contentTypeColors)) {
                        onChange { it.copy(contentTypeColors = !it.contentTypeColors) }
                    }
                }
                item { SettingRow("Dim ended programmes", onOff(settings.dimEnded)) { onChange { it.copy(dimEnded = !it.dimEnded) } } }
                item {
                    SettingRow(
                        "Current time line",
                        onOff(settings.timeIndicator),
                    ) { onChange { it.copy(timeIndicator = !it.timeIndicator) } }
                }
                item { SettingRow("24-hour clock", onOff(settings.clock24h)) { onChange { it.copy(clock24h = !it.clock24h) } } }
                item { SettingRow("Media info bubbles", onOff(settings.mediaInfo)) { onChange { it.copy(mediaInfo = !it.mediaInfo) } } }
                item { SettingRow("Accent colour", settings.accent.label) { onChange { it.copy(accent = it.accent.next()) } } }
                item { SettingRow("Focus colour", settings.focus.label) { onChange { it.copy(focus = it.focus.next()) } } }
                item {
                    SettingRow(
                        "Channel logo on screen",
                        settings.watermark.label,
                    ) { onChange { it.copy(watermark = it.watermark.next()) } }
                }
                item {
                    SettingRow(
                        "Logo position",
                        settings.watermarkCorner.label,
                    ) { onChange { it.copy(watermarkCorner = it.watermarkCorner.next()) } }
                }
                item {
                    SettingRow("Logo opacity", "${settings.watermarkOpacity}%") {
                        onChange { it.copy(watermarkOpacity = OPACITIES[(OPACITIES.indexOf(it.watermarkOpacity) + 1) % OPACITIES.size]) }
                    }
                }
                item {
                    SettingRow(
                        "Rating badge when a programme starts",
                        onOff(settings.ratingBug),
                    ) { onChange { it.copy(ratingBug = !it.ratingBug) } }
                }
                item {
                    SettingRow("\"Feature presentation\" before movies", onOff(settings.featurePresentation)) {
                        onChange { it.copy(featurePresentation = !it.featurePresentation) }
                    }
                }
                item { SettingRow("Up next card", onOff(settings.upNextCard)) { onChange { it.copy(upNextCard = !it.upNextCard) } } }
                item {
                    SettingRow(
                        "Break screen between programmes",
                        onOff(settings.breakScreens),
                    ) { onChange { it.copy(breakScreens = !it.breakScreens) } }
                }
                item { SettingRow("Picture", settings.displayMode.label) { onChange { it.copy(displayMode = it.displayMode.next()) } } }
                item { SettingRow("Scanlines", settings.scanlines.label) { onChange { it.copy(scanlines = it.scanlines.next()) } } }
                item { SettingRow("CRT corner shading", onOff(settings.vignette)) { onChange { it.copy(vignette = !it.vignette) } } }
                item {
                    SettingRow(
                        "Sign off when idle",
                        if (settings.autoSignOffHours ==
                            0
                        ) {
                            "Never"
                        } else {
                            "After ${settings.autoSignOffHours} h"
                        },
                    ) {
                        onChange {
                            it.copy(
                                autoSignOffHours =
                                    SIGN_OFF_HOURS[
                                        (SIGN_OFF_HOURS.indexOf(it.autoSignOffHours) + 1) %
                                            SIGN_OFF_HOURS.size,
                                    ],
                            )
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

private val OPACITIES = listOf(40, 55, 70, 85, 100)
private val SIGN_OFF_HOURS = listOf(0, 2, 3, 4, 6, 8)

private fun onOff(value: Boolean) = if (value) "On" else "Off"

private inline fun <reified E : Enum<E>> E.next(): E = enumValues<E>().let { it[(ordinal + 1) % it.size] }

@Composable
private fun SettingRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val theme = LocalTvTheme.current
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(if (focused) theme.focusedCell else Color.Transparent, RoundedCornerShape(4.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvText(label, size = 15.sp, color = if (focused) theme.focusedText else theme.text, modifier = Modifier.weight(1f))
        TvText("‹ $value ›", size = 14.sp, bold = true, color = if (focused) theme.focusedText else theme.accent)
    }
}
