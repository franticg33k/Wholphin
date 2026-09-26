package com.github.damontecres.wholphin.tvmode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.tvmode.ChannelColumnStyle
import com.github.damontecres.wholphin.tvmode.GuideRow
import com.github.damontecres.wholphin.tvmode.TvModeSettings
import com.github.damontecres.wholphin.tvmode.core.GuideEntry
import com.github.damontecres.wholphin.tvmode.core.ItemDetails
import com.github.damontecres.wholphin.tvmode.core.TvChannel

private const val HALF_HOUR_MS = 30 * 60_000L
private const val WINDOW_SLOTS = 4
private val CHANNEL_COLUMN = 170.dp

/** What the guide shows and what it can do. */
class GuideModel(
    val rows: List<GuideRow>,
    val categories: List<String>,
    val category: String?,
    val current: TvChannel?,
    val windowStartMs: Long,
    val nowMs: Long,
    val details: Map<String, ItemDetails>,
    val settings: TvModeSettings,
    val images: ImageUrls,
    val onCategory: (String?) -> Unit,
    val onTune: (String) -> Unit,
    val onShift: (Int) -> Boolean,
    val onFocusItem: (String?) -> Unit,
    val onFullscreen: () -> Unit,
    val onSettings: () -> Unit,
    val onExit: () -> Unit,
)

/**
 * The cable guide: top bar and the focused programme's details on the left, the live channel on the right, category
 * tabs, a time row with a "now" marker, and a grid whose cells are as wide as the programmes are long.
 */
@Composable
fun RetroGuide(
    model: GuideModel,
    preview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTvTheme.current
    // Until focus lands, describe what the current channel is airing.
    var focused by remember {
        mutableStateOf(
            model.rows.firstOrNull { it.channel.id == model.current?.id }?.let { row ->
                row.entries.firstOrNull { model.nowMs in it.startMs until it.endMs }?.let { row.channel to it }
            },
        )
    }
    var notice by remember { mutableStateOf<String?>(null) }
    val initialCell = remember { FocusRequester() }
    val watchButton = remember { FocusRequester() }

    Column(modifier.fillMaxSize().background(theme.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .weight(0.47f),
        ) {
            Column(
                Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
                    .background(theme.panel),
            ) {
                TopBar(model, watchButton)
                InfoPanel(focused, notice, model, Modifier.weight(1f))
            }
            Box(
                Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
                    .padding(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(2.dp, theme.divider, RoundedCornerShape(6.dp))
                    .background(Color.Black),
            ) {
                preview()
                ChannelLogo(
                    model.current,
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(width = 84.dp, height = 40.dp),
                    alpha = 0.7f,
                )
            }
        }
        if (model.categories.isNotEmpty()) CategoryTabs(model)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(0.53f),
        ) {
            val slotWidth = (maxWidth - CHANNEL_COLUMN) / WINDOW_SLOTS
            val dpPerMinute = slotWidth / 30f
            val windowEnd = model.windowStartMs + WINDOW_SLOTS * HALF_HOUR_MS
            Column(Modifier.fillMaxSize()) {
                TimeRow(model, slotWidth)
                val startIndex = model.rows.indexOfFirst { it.channel.id == model.current?.id }.coerceAtLeast(0)
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(model.rows, key = { it.channel.id }) { row ->
                        GuideRowView(
                            row = row,
                            model = model,
                            windowEnd = windowEnd,
                            dpPerMinute = dpPerMinute,
                            focusedRow = focused?.first?.id == row.channel.id,
                            initialCell = initialCell,
                            onFocus = { entry ->
                                focused = row.channel to entry
                                notice = null
                                model.onFocusItem(entry.itemId)
                            },
                            onSelect = { entry ->
                                // Only what's on now can be watched; a later programme says when it starts.
                                if (model.nowMs in entry.startMs until entry.endMs) {
                                    model.onTune(row.channel.id)
                                } else if (entry.startMs > model.nowMs) {
                                    notice =
                                        "Starts at ${formatTime(
                                            entry.startMs,
                                            model.settings.clock24h,
                                        )} on ${row.channel.number}. Tune in then to watch."
                                }
                            },
                        )
                    }
                }
            }
            if (model.settings.timeIndicator && model.nowMs in model.windowStartMs until windowEnd) {
                val x = CHANNEL_COLUMN + dpPerMinute * ((model.nowMs - model.windowStartMs) / 60_000f)
                Box(
                    Modifier
                        .offset(x = x - 1.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(theme.nowLine.copy(alpha = 0.55f)),
                )
                TvText("▼", size = 12.sp, color = theme.nowLine, modifier = Modifier.offset(x = x - 5.dp, y = 18.dp))
            }
        }
    }

    LaunchedEffect(Unit) {
        model.onFocusItem(focused?.second?.itemId)
        val shown = model.rows.any { it.channel.id == model.current?.id }
        if (!shown || runCatching { initialCell.requestFocus() }.isFailure) runCatching { watchButton.requestFocus() }
    }
}

@Composable
private fun TopBar(
    model: GuideModel,
    watchButton: FocusRequester,
) {
    val theme = LocalTvTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(theme.topBar)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton("WATCH", model.onFullscreen, Modifier.focusRequester(watchButton))
        BarButton("SETTINGS", model.onSettings)
        BarButton("EXIT", model.onExit)
        Spacer(Modifier.weight(1f))
        TvText("CABLE TV", size = 14.sp, bold = true, color = theme.accent)
    }
}

@Composable
private fun BarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTvTheme.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    TvText(
        text = label,
        size = 13.sp,
        bold = true,
        color = if (focused) theme.focusedText else theme.text,
        modifier =
            modifier
                .scale(if (focused) 1.05f else 1f)
                .background(if (focused) theme.focusedCell else theme.channelCell, shape)
                .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else theme.divider, shape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun InfoPanel(
    focused: Pair<TvChannel, GuideEntry>?,
    notice: String?,
    model: GuideModel,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTvTheme.current
    Box(modifier.fillMaxWidth()) {
        val (channel, entry) = focused ?: return@Box
        val details = entry.itemId?.let { model.details[it] }
        details?.backdropItemId?.let {
            AsyncImage(
                model = model.images.url(it, "Backdrop", 540),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.16f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    val logo = details?.logoItemId
                    if (logo != null) {
                        AsyncImage(
                            model = model.images.url(logo, "Logo", 120),
                            contentDescription = entry.title,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier.height(48.dp).widthIn(max = 300.dp),
                        )
                    } else {
                        TvText(guideTitle(entry), size = 24.sp, bold = true)
                    }
                    episodeLine(entry)?.takeIf { !entry.movie }?.let { TvText(it, size = 17.sp, color = theme.accent) }
                    TvText(
                        "${formatTime(
                            entry.startMs,
                            model.settings.clock24h,
                        )}-${formatTime(entry.endMs, model.settings.clock24h)}  ${channel.number} ${channel.name.uppercase()}",
                        size = 14.sp,
                        color = theme.textSecondary,
                    )
                }
                ChannelLogo(channel, Modifier.size(width = 96.dp, height = 48.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                metaChips(entry, details).forEach { (text, filled) -> MetaChip(text, filled) }
            }
            Spacer(Modifier.height(6.dp))
            val status =
                when {
                    notice != null -> notice

                    model.nowMs in entry.startMs until entry.endMs -> "On now · ${((entry.endMs - model.nowMs) / 60_000).coerceAtLeast(
                        0,
                    )} min left · OK to watch"

                    entry.startMs > model.nowMs -> "Starts in ${formatWait(entry.startMs - model.nowMs)}"

                    else -> "Ended"
                }
            TvText(status, size = 13.sp, color = if (notice != null) theme.accent else theme.textSecondary)
            details?.overview?.let {
                Spacer(Modifier.height(4.dp))
                TvText(it, size = 14.sp, color = theme.text, maxLines = 3)
            }
        }
    }
}

private fun formatWait(ms: Long): String {
    val minutes = (ms + 59_999) / 60_000
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

@Composable
private fun CategoryTabs(model: GuideModel) {
    val theme = LocalTvTheme.current
    LazyRow(
        Modifier
            .fillMaxWidth()
            .background(theme.topBar)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "all") { Tab("All Channels", model.category == null) { model.onCategory(null) } }
        items(model.categories, key = { it }) { name -> Tab(name, model.category == name) { model.onCategory(name) } }
    }
}

@Composable
private fun Tab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalTvTheme.current
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .background(if (focused) theme.focusedCell else Color.Transparent, RoundedCornerShape(4.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TvText(label, size = 13.sp, bold = selected, color = if (focused) theme.focusedText else theme.text)
        Box(
            Modifier
                .padding(top = 2.dp)
                .width(24.dp)
                .height(2.dp)
                .background(if (selected) theme.accent else Color.Transparent),
        )
    }
}

@Composable
private fun TimeRow(
    model: GuideModel,
    slotWidth: Dp,
) {
    val theme = LocalTvTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(theme.timeRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(CHANNEL_COLUMN).padding(start = 12.dp)) {
            TvText(formatClock(model.nowMs, model.settings.clock24h), size = 14.sp, bold = true, color = theme.timeRowText)
        }
        repeat(WINDOW_SLOTS) { i ->
            Row(Modifier.width(slotWidth).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(theme.divider),
                )
                TvText(
                    formatTime(model.windowStartMs + i * HALF_HOUR_MS, model.settings.clock24h),
                    size = 13.sp,
                    color = theme.timeRowText,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun GuideRowView(
    row: GuideRow,
    model: GuideModel,
    windowEnd: Long,
    dpPerMinute: Dp,
    focusedRow: Boolean,
    initialCell: FocusRequester,
    onFocus: (GuideEntry) -> Unit,
    onSelect: (GuideEntry) -> Unit,
) {
    val detailed = model.settings.detailedGuide
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (detailed) 58.dp else 42.dp)
            .padding(vertical = 1.dp),
    ) {
        ChannelCell(row.channel, focusedRow, model.settings.channelColumn)
        var cursor = model.windowStartMs
        row.entries
            .filter { it.endMs > model.windowStartMs && it.startMs < windowEnd }
            .forEachIndexed { index, entry ->
                val start = maxOf(entry.startMs, model.windowStartMs)
                val end = minOf(entry.endMs, windowEnd)
                if (start > cursor) Spacer(Modifier.width(dpPerMinute * ((start - cursor) / 60_000f)))
                cursor = end
                key(entry.guideGroup) {
                    val airing = model.nowMs in entry.startMs until entry.endMs
                    ProgramCell(
                        entry = entry,
                        index = index + row.channel.id.hashCode(),
                        width = dpPerMinute * ((end - start) / 60_000f),
                        airing = airing,
                        model = model,
                        windowEnd = windowEnd,
                        onFocus = { onFocus(entry) },
                        onClick = { onSelect(entry) },
                        modifier = if (airing && row.channel.id == model.current?.id) Modifier.focusRequester(initialCell) else Modifier,
                    )
                }
            }
    }
}

@Composable
private fun ChannelCell(
    channel: TvChannel,
    focusedRow: Boolean,
    style: ChannelColumnStyle,
) {
    val theme = LocalTvTheme.current
    Row(
        Modifier
            .width(CHANNEL_COLUMN)
            .fillMaxHeight()
            .padding(end = 2.dp)
            .background(if (focusedRow) theme.channelCellFocused else theme.channelCell),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (focusedRow) theme.accent else Color.Transparent),
        )
        val showLogo = style != ChannelColumnStyle.NUMBERS && channel.logoUrl != null
        if (style == ChannelColumnStyle.LOGOS && showLogo) {
            ChannelLogo(channel, Modifier.fillMaxSize().padding(6.dp))
            return@Row
        }
        if (showLogo) ChannelLogo(channel, Modifier.padding(start = 6.dp).size(width = 50.dp, height = 30.dp))
        Column(Modifier.padding(horizontal = 8.dp)) {
            TvText(channel.number, size = 15.sp, bold = true, color = theme.channelNumber)
            TvText(channel.name, size = 12.sp, color = theme.channelName)
        }
    }
}

@Composable
private fun ProgramCell(
    entry: GuideEntry,
    index: Int,
    width: Dp,
    airing: Boolean,
    model: GuideModel,
    windowEnd: Long,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTvTheme.current
    var focused by remember { mutableStateOf(false) }
    val ended = entry.endMs <= model.nowMs && model.settings.dimEnded
    val base = theme.cellColor(entry.contentType, index, model.settings.contentTypeColors)
    val background =
        if (focused) {
            theme.focusedCell
        } else if (ended) {
            base.copy(alpha = 0.45f)
        } else {
            base
        }
    val textColor =
        if (focused) {
            theme.focusedText
        } else if (ended) {
            theme.textSecondary
        } else {
            theme.text
        }
    val shape = if (theme.roundedCells) RoundedCornerShape(4.dp) else RectangleShape
    Box(
        modifier
            .width(width)
            .fillMaxHeight()
            .padding(horizontal = 1.dp)
            .background(background, shape)
            .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }.onPreviewKeyEvent { event ->
                // Page the grid in half hours when focus would leave its visible window.
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> entry.endMs >= windowEnd && model.onShift(1)
                    Key.DirectionLeft -> entry.startMs <= model.windowStartMs && model.onShift(-1)
                    else -> false
                }
            }.clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvText(guideTitle(entry), size = 14.sp, bold = true, color = textColor, modifier = Modifier.weight(1f, fill = false))
                if (entry.premiere) {
                    Spacer(Modifier.width(4.dp))
                    TvText("NEW", size = 10.sp, bold = true, color = if (focused) theme.focusedText else theme.accent)
                }
            }
            if (model.settings.detailedGuide) {
                if (entry.movie || entry.episode == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        entry.rating?.let { CellPill(it, focused) }
                        entry.lineup?.let { TvText(it, size = 11.sp, color = textColor) }
                    }
                } else {
                    episodeLine(entry)?.let { TvText(it, size = 12.sp, color = textColor) }
                }
            }
        }
        if (airing) {
            val progress = ((model.nowMs - entry.startMs).toFloat() / (entry.endMs - entry.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(if (focused) theme.focusedText else theme.accent),
            )
        }
    }
}

@Composable
private fun CellPill(
    text: String,
    focused: Boolean,
) {
    val theme = LocalTvTheme.current
    val color = if (focused) theme.focusedText else theme.text
    TvText(
        text = text,
        size = 10.sp,
        color = color,
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp),
    )
}
