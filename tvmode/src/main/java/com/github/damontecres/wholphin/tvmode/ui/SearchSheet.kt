package com.github.damontecres.wholphin.tvmode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.damontecres.wholphin.tvmode.GuideRow
import java.text.Normalizer
import java.util.Locale

/** One search hit: a channel, and what it's airing when that's what matched. */
data class SearchHit(
    val row: GuideRow,
    val matchedProgramme: String?,
)

private fun normalize(text: String): String =
    Normalizer
        .normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)

/** Channels whose number starts with, or whose name contains, the query; then channels airing a matching title now. */
fun searchChannels(
    rows: List<GuideRow>,
    query: String,
    nowMs: Long,
): List<SearchHit> {
    val q = normalize(query.trim())
    if (q.isEmpty()) return rows.map { SearchHit(it, null) }
    val byChannel = rows.filter { it.channel.number.startsWith(q) || normalize(it.channel.name).contains(q) }.map { SearchHit(it, null) }
    val ids = byChannel.map { it.row.channel.id }.toSet()
    val byProgramme =
        rows
            .filter { it.channel.id !in ids }
            .mapNotNull { row ->
                row.entries
                    .firstOrNull { nowMs in it.startMs until it.endMs && normalize(it.title).contains(q) }
                    ?.let { SearchHit(row, it.title) }
            }
    return byChannel + byProgramme
}

/** Channel search: type to filter channels (or titles on now), OK tunes. */
@Composable
fun SearchSheet(
    rows: List<GuideRow>,
    nowMs: Long,
    onTune: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTvTheme.current
    var query by remember { mutableStateOf("") }
    val input = remember { FocusRequester() }
    val hits = remember(rows, query, nowMs / 60_000) { searchChannels(rows, query, nowMs) }
    Box(modifier.fillMaxSize().background(Color(0x66000000))) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(420.dp)
                .background(theme.panel)
                .border(1.dp, theme.divider)
                .padding(20.dp),
        ) {
            TvText("CHANNEL SEARCH", size = 18.sp, bold = true, color = theme.accent)
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = theme.text, fontSize = 20.sp, fontFamily = theme.font),
                cursorBrush = SolidColor(theme.accent),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(input)
                        .background(theme.channelCell, RoundedCornerShape(4.dp))
                        .border(1.dp, theme.chipBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { field ->
                    if (query.isEmpty()) TvText("Name, number or a title on now", size = 16.sp, color = theme.textSecondary)
                    field()
                },
            )
            Spacer(Modifier.height(12.dp))
            if (hits.isEmpty()) {
                TvText("No channels match", size = 16.sp, color = theme.textSecondary)
            }
            LazyColumn {
                items(hits, key = { it.row.channel.id }) { hit -> HitRow(hit, nowMs) { onTune(hit.row.channel.id) } }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { input.requestFocus() } }
}

@Composable
private fun HitRow(
    hit: SearchHit,
    nowMs: Long,
    onClick: () -> Unit,
) {
    val theme = LocalTvTheme.current
    var focused by remember { mutableStateOf(false) }
    val airing = hit.row.entries.firstOrNull { nowMs in it.startMs until it.endMs }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(if (focused) theme.focusedCell else Color.Transparent, RoundedCornerShape(4.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(hit.row.channel, Modifier.size(width = 56.dp, height = 30.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            TvText(
                "${hit.row.channel.number}  ${hit.row.channel.name}",
                size = 15.sp,
                bold = true,
                color = if (focused) theme.focusedText else theme.text,
            )
            (hit.matchedProgramme ?: airing?.title)?.let {
                TvText("On now: $it", size = 12.sp, color = if (focused) theme.focusedText else theme.textSecondary)
            }
        }
    }
}
