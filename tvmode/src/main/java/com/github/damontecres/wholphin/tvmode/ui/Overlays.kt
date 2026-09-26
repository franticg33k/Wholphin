package com.github.damontecres.wholphin.tvmode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.tvmode.core.GuideEntry
import com.github.damontecres.wholphin.tvmode.core.ItemDetails
import com.github.damontecres.wholphin.tvmode.core.TvChannel

/** Everything the player overlays need. */
data class OverlayData(
    val channel: TvChannel?,
    val now: GuideEntry?,
    val next: GuideEntry?,
    val upcoming: List<GuideEntry>,
    val details: Map<String, ItemDetails>,
    val nowMs: Long,
    val clock24h: Boolean,
    val showMediaInfo: Boolean,
    val images: ImageUrls,
)

/**
 * Classic overlay: channel number and logo in the top-right corner; now and next with their show logos along the
 * bottom, a timeline, and media info bubbles.
 */
@Composable
fun ClassicOverlay(
    data: OverlayData,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        ChannelCorner(data.channel, Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 48.dp))
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 48.dp, end = 48.dp, top = 56.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                data.now?.let { NowNextBlock("NOW PLAYING", it, data, Alignment.Start, Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(32.dp))
                data.next?.let { NowNextBlock("COMING UP", it, data, Alignment.End, Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
            }
            data.now?.let { now ->
                Spacer(Modifier.height(10.dp))
                Timeline(now, data.nowMs, data.clock24h)
            }
            if (data.showMediaInfo) {
                Spacer(Modifier.height(10.dp))
                MediaBubbles(data.now?.itemId?.let { data.details[it] })
            }
        }
    }
}

@Composable
private fun ChannelCorner(
    channel: TvChannel?,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTvTheme.current
    channel ?: return
    Column(modifier, horizontalAlignment = Alignment.End) {
        TvText(channel.number, size = 52.sp, bold = true, color = theme.accent, style = shadowed())
        if (channel.logoUrl != null) {
            ChannelLogo(channel, Modifier.size(width = 150.dp, height = 70.dp))
        } else {
            TvText(channel.name.uppercase(), size = 18.sp, color = Color.White, style = shadowed())
        }
    }
}

@Composable
private fun NowNextBlock(
    label: String,
    entry: GuideEntry,
    data: OverlayData,
    align: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTvTheme.current
    val details = entry.itemId?.let { data.details[it] }
    Column(modifier, horizontalAlignment = align) {
        TvText(label, size = 13.sp, bold = true, color = theme.accent)
        Spacer(Modifier.height(4.dp))
        val logo = details?.logoItemId
        if (logo != null) {
            AsyncImage(
                model = data.images.url(logo, "Logo", 160),
                contentDescription = entry.title,
                contentScale = ContentScale.Fit,
                alignment = if (align == Alignment.End) Alignment.CenterEnd else Alignment.CenterStart,
                modifier = Modifier.height(if (label == "NOW PLAYING") 64.dp else 44.dp).widthIn(max = 320.dp),
            )
        } else {
            TvText(
                guideTitle(entry),
                size =
                    if (label ==
                        "NOW PLAYING"
                    ) {
                        26.sp
                    } else {
                        20.sp
                    },
                bold = true,
                color = Color.White,
                style = shadowed(),
            )
        }
        episodeLine(entry)?.takeIf { !entry.movie }?.let { TvText(it, size = 16.sp, color = Color.White, style = shadowed()) }
        TvText(formatTime(entry.startMs, data.clock24h), size = 14.sp, color = theme.textSecondary)
    }
}

@Composable
private fun Timeline(
    now: GuideEntry,
    nowMs: Long,
    clock24h: Boolean,
) {
    val theme = LocalTvTheme.current
    val progress = ((nowMs - now.startMs).toFloat() / (now.endMs - now.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TvText(formatTime(now.startMs, clock24h), size = 13.sp, color = theme.textSecondary)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .background(Color(0x55FFFFFF), RoundedCornerShape(3.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(theme.accent, RoundedCornerShape(3.dp)),
            )
        }
        Spacer(Modifier.width(12.dp))
        TvText(formatTime(now.endMs, clock24h), size = 13.sp, color = theme.textSecondary)
    }
}

/** Dark pills: how it's playing and what the file is. */
@Composable
private fun MediaBubbles(details: ItemDetails?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Bubble {
            Box(
                Modifier
                    .size(8.dp)
                    .background(Color(0xFF4CAF50), CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            TvText("Direct Play", size = 12.sp, color = Color.White)
        }
        Spacer(Modifier.weight(1f))
        listOfNotNull(
            details?.quality,
            details?.videoCodec,
            details?.audio,
        ).forEach { Bubble { TvText(it, size = 12.sp, color = Color.White) } }
    }
}

@Composable
private fun Bubble(content: @Composable () -> Unit) {
    Row(
        Modifier
            .background(Color(0xB3000000), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Lineup overlay: a bottom bar with the channel, the clock, what's on and the next few programmes. */
@Composable
fun LineupOverlay(
    data: OverlayData,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTvTheme.current
    Box(modifier.fillMaxSize()) {
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(24.dp)
                .background(Color(0xE60F172A), RoundedCornerShape(8.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(170.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ChannelLogo(data.channel, Modifier.size(width = 120.dp, height = 54.dp))
                TvText("${data.channel?.number ?: ""}  ${data.channel?.name ?: ""}", size = 14.sp, color = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1.4f)) {
                data.now?.let { now ->
                    TvText(guideTitle(now), size = 22.sp, bold = true, color = Color.White)
                    episodeLine(now)?.takeIf { !now.movie }?.let { TvText(it, size = 15.sp, color = theme.accent) }
                    val left = ((now.endMs - data.nowMs) / 60_000).coerceAtLeast(0)
                    TvText(
                        "${formatTime(now.startMs, data.clock24h)} – ${formatTime(now.endMs, data.clock24h)} · $left min left",
                        size = 13.sp,
                        color = theme.textSecondary,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                data.upcoming.forEach {
                    TvText("${formatTime(it.startMs, data.clock24h)}  ${guideTitle(it)}", size = 14.sp, color = theme.textSecondary)
                }
            }
            Spacer(Modifier.width(16.dp))
            TvText(formatTime(data.nowMs, data.clock24h), size = 26.sp, bold = true, color = Color.White)
        }
    }
}

/** Typed channel number, "1 0 _ _", red when no channel has it. */
@Composable
fun DigitOverlay(
    digits: String,
    slots: Int,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTvTheme.current
    val text = (0 until maxOf(slots, digits.length)).joinToString(" ") { digits.getOrNull(it)?.toString() ?: "_" }
    TvText(
        text = text,
        size = 56.sp,
        bold = true,
        color = if (error) Color(0xFFFF5252) else theme.accent,
        style = shadowed(),
        modifier = modifier.background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

fun shadowed() =
    androidx.compose.ui.text.TextStyle(
        shadow =
            androidx.compose.ui.graphics
                .Shadow(
                    Color.Black,
                    androidx.compose.ui.geometry
                        .Offset(2f, 2f),
                    4f,
                ),
    )
