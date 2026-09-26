package com.github.damontecres.wholphin.tvmode.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.tvmode.core.TvChannel
import com.github.damontecres.wholphin.tvmode.core.TvSlot
import kotlin.math.abs
import kotlin.math.sin

private const val BARS = 24

/**
 * What a music channel shows: the album art, the song, and a bar visualiser. The bars are an animation, not an
 * analysis of the audio (which would need the microphone permission).
 */
@Composable
fun MusicScreen(
    channel: TvChannel?,
    slot: TvSlot,
    images: ImageUrls,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTvTheme.current
    val scale = if (compact) 0.45f else 1f
    val transition = rememberInfiniteTransition(label = "bars")
    val phase by transition.animateFloat(
        0f,
        1000f,
        infiniteRepeatable(tween(400_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val seeds = remember(slot.id) { List(BARS) { i -> 0.7f + ((slot.id.hashCode() shr (i % 16)) and 7) / 10f } }
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(theme.topBar, theme.background)))) {
        Row(
            Modifier.fillMaxSize().padding((48 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((40 * scale).dp),
        ) {
            Box(
                Modifier
                    .size((360 * scale).dp)
                    .clip(RoundedCornerShape((8 * scale).dp))
                    .background(theme.channelCell)
                    .border(2.dp, theme.divider, RoundedCornerShape((8 * scale).dp)),
                contentAlignment = Alignment.Center,
            ) {
                ChannelLogo(channel, Modifier.fillMaxSize().padding((40 * scale).dp))
                slot.itemId?.let {
                    AsyncImage(
                        model = images.url(it, "Primary", 480),
                        contentDescription = slot.album,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                TvText("NOW PLAYING", size = (16 * scale).sp, bold = true, color = theme.accent)
                TvText(slot.title ?: "", size = (40 * scale).sp, bold = true, maxLines = 2)
                slot.artist?.let { TvText(it, size = (28 * scale).sp, color = theme.text) }
                slot.album?.let { TvText(it, size = (22 * scale).sp, color = theme.textSecondary) }
                Spacer(Modifier.height((32 * scale).dp))
                Canvas(Modifier.fillMaxWidth().height((140 * scale).dp)) {
                    val gap = size.width / BARS
                    for (i in 0 until BARS) {
                        val wave = abs(sin(phase * seeds[i] + i * 0.7f)) * 0.8f + abs(sin(phase * 0.37f + i)) * 0.2f
                        val h = (0.12f + wave * 0.88f) * size.height
                        drawRoundRect(
                            color = if (i % 2 == 0) theme.accent else theme.accent.copy(alpha = 0.75f),
                            topLeft = Offset(i * gap + gap * 0.15f, size.height - h),
                            size = Size(gap * 0.7f, h),
                            cornerRadius = CornerRadius(3f, 3f),
                        )
                    }
                }
            }
        }
        TvText(
            "${channel?.number ?: ""}  ${channel?.name ?: ""}",
            size = (14 * scale).sp,
            color = theme.textSecondary,
            modifier = Modifier.align(Alignment.BottomEnd).padding((24 * scale).dp),
        )
    }
}

/** On a trailer: what it's for, and where that airs (OK tunes there when it's on now). */
@Composable
fun TrailerPanel(
    slot: TvSlot,
    target: com.github.damontecres.wholphin.tvmode.TrailerTarget?,
    clock24h: Boolean,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTvTheme.current
    Box(modifier.fillMaxSize().padding(40.dp)) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .background(theme.overlayScrim, RoundedCornerShape(6.dp))
                .border(1.dp, theme.divider, RoundedCornerShape(6.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            TvText("COMING ATTRACTIONS", size = 13.sp, bold = true, color = theme.accent)
            TvText(slot.title ?: "", size = 22.sp, bold = true, color = Color.White)
            val line =
                when {
                    target == null -> "Not on the schedule in the next few hours"
                    target.airingNow -> "On now on ${target.channel.number} ${target.channel.name} · OK to watch"
                    else -> "${formatTime(target.entry.startMs, clock24h)} on ${target.channel.number} ${target.channel.name}"
                }
            TvText(line, size = 15.sp, color = if (target?.airingNow == true) theme.accent else theme.textSecondary)
        }
    }
}
