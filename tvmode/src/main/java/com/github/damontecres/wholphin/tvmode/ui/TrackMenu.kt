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
import com.github.damontecres.wholphin.tvmode.TrackMenu
import com.github.damontecres.wholphin.tvmode.TrackOption

/** Audio and subtitle tracks of what is playing: OK on a track plays it. Back closes. */
@Composable
fun TrackMenuDialog(
    menu: TrackMenu,
    onAudio: (String) -> Unit,
    onSubtitle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalTvTheme.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(620.dp)
                .background(theme.panel, RoundedCornerShape(8.dp))
                .border(1.dp, theme.divider, RoundedCornerShape(8.dp))
                .padding(20.dp),
        ) {
            TvText("AUDIO & SUBTITLES", size = 20.sp, bold = true, color = theme.accent)
            Spacer(Modifier.height(10.dp))
            LazyColumn {
                if (menu.audio.isNotEmpty()) {
                    item { TvText("Audio", size = 14.sp, bold = true, color = theme.accent, modifier = Modifier.padding(vertical = 4.dp)) }
                    val start = menu.audio.indexOfFirst { it.selected }.coerceAtLeast(0)
                    menu.audio.forEachIndexed { i, option ->
                        item { TrackRow(option, if (i == start) Modifier.focusRequester(first) else Modifier) { onAudio(option.id) } }
                    }
                }
                item {
                    TvText(
                        "Subtitles",
                        size = 14.sp,
                        bold = true,
                        color = theme.accent,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                }
                menu.subtitles.forEachIndexed { i, option ->
                    item {
                        val modifier = if (menu.audio.isEmpty() && option.selected) Modifier.focusRequester(first) else Modifier
                        TrackRow(option, modifier) { onSubtitle(option.id) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TvText("OK to choose · Back to close. Applies to this programme; Settings sets the defaults.", size = 12.sp, color = theme.text)
        }
    }
}

@Composable
private fun TrackRow(
    option: TrackOption,
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
        TvText(
            if (option.selected) "●" else "○",
            size = 14.sp,
            color = if (focused) theme.focusedText else theme.accent,
            modifier = Modifier.width(24.dp),
        )
        TvText(option.label, size = 15.sp, color = if (focused) theme.focusedText else theme.text, modifier = Modifier.weight(1f))
    }
}
