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
import com.github.damontecres.wholphin.tvmode.TvModeSettings

/** TV mode settings: OK on a row cycles its value. */
@Composable
fun SettingsDialog(
    settings: TvModeSettings,
    onChange: ((TvModeSettings) -> TvModeSettings) -> Unit,
    onDismiss: () -> Unit,
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
                    SettingRow("Theme", settings.theme.label, Modifier.focusRequester(first)) {
                        onChange { it.copy(theme = it.theme.next()) }
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
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

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
