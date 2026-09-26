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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.damontecres.wholphin.tvmode.Corner
import com.github.damontecres.wholphin.tvmode.ProgramStart
import com.github.damontecres.wholphin.tvmode.Scanlines
import com.github.damontecres.wholphin.tvmode.WatermarkMode
import com.github.damontecres.wholphin.tvmode.core.GuideEntry
import com.github.damontecres.wholphin.tvmode.core.TvChannel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val WATERMARK_PERIOD_MS = 5 * 60_000L
private const val WATERMARK_SHOWN_MS = 12_000L
private const val FEATURE_MS = 4_500L
private const val RATING_MS = 6_000L
private const val UP_NEXT_MS = 60_000L

private fun Corner.alignment() =
    when (this) {
        Corner.TOP_RIGHT -> Alignment.TopEnd
        Corner.TOP_LEFT -> Alignment.TopStart
        Corner.BOTTOM_LEFT -> Alignment.BottomStart
        Corner.BOTTOM_RIGHT -> Alignment.BottomEnd
    }

private fun countdown(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) + 999) / 1000
    return "%d:%02d".format(Locale.US, seconds / 60, seconds % 60)
}

/** The channel logo in a corner of the picture, always or for a few seconds every five minutes. */
@Composable
fun Watermark(
    channel: TvChannel?,
    mode: WatermarkMode,
    corner: Corner,
    opacity: Int,
    clock: State<Long>,
) {
    if (mode == WatermarkMode.OFF || channel?.logoUrl == null) return
    if (mode == WatermarkMode.PERIODIC && clock.value % WATERMARK_PERIOD_MS >= WATERMARK_SHOWN_MS) return
    Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) {
        ChannelLogo(channel, Modifier.align(corner.alignment()).size(width = 110.dp, height = 52.dp), alpha = opacity / 100f)
    }
}

/** A rating badge in the top-left corner for a few seconds when a programme starts. */
@Composable
fun RatingBug(
    start: ProgramStart?,
    clock: State<Long>,
) {
    val rating = start?.entry?.rating ?: return
    // Movies show the feature presentation card first.
    val from = if (start.entry.movie) FEATURE_MS else 0L
    val elapsed = clock.value - start.atMs
    if (elapsed !in from until from + RATING_MS) return
    Box(Modifier.fillMaxSize().padding(start = 40.dp, top = 32.dp)) {
        Column(
            Modifier
                .background(Color(0xB3000000), RoundedCornerShape(3.dp))
                .border(2.dp, Color.White, RoundedCornerShape(3.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TvText(if (start.entry.movie) "RATED" else "TV", size = 10.sp, bold = true, color = Color.White)
            TvText(rating.removePrefix("TV-"), size = 22.sp, bold = true, color = Color.White)
        }
    }
}

/** "Our feature presentation" when a movie starts from the beginning. */
@Composable
fun FeaturePresentationCard(
    start: ProgramStart?,
    clock: State<Long>,
) {
    val entry = start?.entry?.takeIf { it.movie } ?: return
    val elapsed = clock.value - start.atMs
    if (elapsed !in 0 until FEATURE_MS) return
    val theme = LocalTvTheme.current
    // Fade out over the last second.
    val alpha = ((FEATURE_MS - elapsed) / 1000f).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(Brush.radialGradient(listOf(Color(0xE6101830), Color(0xF0000000)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TvText("NOW", size = 22.sp, bold = true, color = theme.accent, style = shadowed())
            TvText("OUR FEATURE PRESENTATION", size = 46.sp, bold = true, color = Color.White, style = shadowed())
            Spacer(Modifier.height(12.dp))
            TvText(guideTitle(entry), size = 24.sp, color = theme.textSecondary)
        }
    }
}

/** A small card in the last minute of a programme saying what's next. */
@Composable
fun UpNextCard(
    now: GuideEntry?,
    next: GuideEntry?,
    clock: State<Long>,
    clock24h: Boolean,
) {
    if (now == null || next == null || next.offAir) return
    val left = now.endMs - clock.value
    if (left !in 1 until UP_NEXT_MS) return
    val theme = LocalTvTheme.current
    Box(Modifier.fillMaxSize().padding(40.dp)) {
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .background(theme.overlayScrim, RoundedCornerShape(6.dp))
                .border(1.dp, theme.divider, RoundedCornerShape(6.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            TvText("UP NEXT · ${formatTime(next.startMs, clock24h)}", size = 12.sp, bold = true, color = theme.accent)
            TvText(guideTitle(next), size = 18.sp, bold = true, color = Color.White)
            episodeLine(next)?.takeIf { !next.movie }?.let { TvText(it, size = 13.sp, color = theme.textSecondary) }
        }
    }
}

/** Between programmes (a schedule gap): a card with a countdown instead of static. */
@Composable
fun BreakCard(
    channel: TvChannel?,
    untilMs: Long,
    next: GuideEntry?,
    clock: State<Long>,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTvTheme.current
    Box(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(theme.topBar, theme.background))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ChannelLogo(channel, Modifier.size(width = 200.dp, height = 96.dp))
            Spacer(Modifier.height(16.dp))
            val label = if (next?.premiere == true) "PREMIERES IN" else "UP NEXT IN"
            TvText("$label ${countdown(untilMs - clock.value)}", size = 34.sp, bold = true, color = theme.accent)
            next?.takeIf { !it.offAir }?.let {
                Spacer(Modifier.height(8.dp))
                TvText(guideTitle(it), size = 26.sp, color = Color.White)
                episodeLine(it)?.takeIf { _ -> !it.movie }?.let { line -> TvText(line, size = 16.sp, color = theme.textSecondary) }
            }
            Spacer(Modifier.height(24.dp))
            TvText("${channel?.number ?: ""}  ${channel?.name ?: ""}", size = 14.sp, color = theme.textSecondary)
        }
    }
}

/** Paused: a "broadcast paused" box drifting around the screen. */
@Composable
fun PausedScreensaver(channel: TvChannel?) {
    val theme = LocalTvTheme.current
    val transition = rememberInfiniteTransition(label = "bounce")
    val x by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(7_300, easing = LinearEasing), RepeatMode.Reverse), label = "x")
    val y by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4_900, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xE6000000))) {
        val boxWidth = 300.dp
        val boxHeight = 130.dp
        Column(
            Modifier
                .offset(x = (maxWidth - boxWidth) * x, y = (maxHeight - boxHeight) * y)
                .size(boxWidth, boxHeight)
                .border(2.dp, theme.accent, RoundedCornerShape(8.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TvText("BROADCAST", size = 26.sp, bold = true, color = theme.accent)
            TvText("PAUSED", size = 26.sp, bold = true, color = theme.accent)
            TvText("Play or OK rejoins ${channel?.number ?: "the channel"} live", size = 11.sp, color = theme.textSecondary)
        }
    }
}

/** Asks whether anyone is still watching; stops playback when nobody answers. */
@Composable
fun StillWatchingPrompt(
    deadlineMs: Long,
    clock: State<Long>,
) {
    val theme = LocalTvTheme.current
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(theme.panel, RoundedCornerShape(8.dp))
                .border(1.dp, theme.divider, RoundedCornerShape(8.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TvText("Still watching?", size = 28.sp, bold = true, color = theme.accent)
            Spacer(Modifier.height(8.dp))
            TvText("Press any button to keep watching.", size = 16.sp)
            TvText("Signing off in ${((deadlineMs - clock.value) / 1000).coerceAtLeast(0)} s", size = 14.sp, color = theme.textSecondary)
        }
    }
}

/** After an unanswered "Still watching?". */
@Composable
fun SignedOffCard(channel: TvChannel?) {
    val theme = LocalTvTheme.current
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TvText("SIGNED OFF", size = 40.sp, bold = true, color = theme.accent)
            Spacer(Modifier.height(8.dp))
            TvText("Press any button to tune back to ${channel?.number ?: "the channel"}", size = 16.sp, color = theme.textSecondary)
        }
    }
}

/** Scanlines and a corner vignette over the picture. */
@Composable
fun CrtOverlay(
    scanlines: Scanlines,
    vignette: Boolean,
    modifier: Modifier = Modifier,
) {
    if (scanlines == Scanlines.OFF && !vignette) return
    Canvas(modifier.fillMaxSize()) {
        if (scanlines != Scanlines.OFF) {
            val pitch = 3.dp.toPx().coerceAtLeast(3f)
            val color = Color.Black.copy(alpha = scanlines.alpha)
            var y = 0f
            while (y < size.height) {
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = pitch / 2)
                y += pitch
            }
        }
        if (vignette) {
            drawRect(
                Brush.radialGradient(
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.6f),
                    center = center,
                    radius = size.maxDimension * 0.62f,
                ),
            )
        }
    }
}

/**
 * A 4:3 picture area, for the 4:3 display modes. With [bezel], it sits in a drawn CRT set: a dark plastic body,
 * rounded screen and a side panel with knobs and a speaker grille.
 */
@Composable
fun FourThreeFrame(
    bezel: Boolean,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (!bezel) {
            val height = minOf(maxHeight, maxWidth * 3f / 4f)
            Box(Modifier.size(width = height * 4f / 3f, height = height).clip(RoundedCornerShape(2.dp))) { content() }
            return@BoxWithConstraints
        }
        val screenHeight = maxHeight * 0.8f
        val screenWidth = screenHeight * 4f / 3f
        val panelWidth = screenHeight * 0.22f
        val margin: Dp = screenHeight * 0.06f
        Row(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF3A3A3E), Color(0xFF1C1C1F))))
                .border(2.dp, Color(0xFF4A4A50), RoundedCornerShape(28.dp))
                .padding(margin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(screenWidth, screenHeight)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color.Black)
                    .border(6.dp, Color(0xFF111113), RoundedCornerShape(36.dp)),
            ) {
                content()
                CrtOverlay(Scanlines.OFF, vignette = true)
            }
            Spacer(Modifier.width(margin))
            Column(
                Modifier.width(panelWidth).height(screenHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(screenHeight * 0.05f),
            ) {
                repeat(2) { Knob(panelWidth * 0.6f) }
                Spacer(Modifier.height(screenHeight * 0.05f))
                repeat(8) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.8f)
                            .height(3.dp)
                            .background(Color(0xFF0E0E10), RoundedCornerShape(2.dp)),
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(10.dp)
                        .background(Color(0xFFE53935), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun Knob(size: Dp) {
    Box(
        Modifier
            .size(size)
            .background(Brush.radialGradient(listOf(Color(0xFF6A6A70), Color(0xFF232326))), CircleShape)
            .border(2.dp, Color(0xFF111113), CircleShape),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .padding(top = size * 0.1f)
                .width(3.dp)
                .height(size * 0.3f)
                .background(Color(0xFFBDBDBD)),
        )
    }
}

private val satelliteDate = DateTimeFormatter.ofPattern("EEE M/d/yy", Locale.US)

/** Satellite overlay: a 2000s receiver banner across the top. */
@Composable
fun SatelliteOverlay(
    data: OverlayData,
    modifier: Modifier = Modifier,
) {
    val yellow = Color(0xFFD9E04B)
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.verticalGradient(listOf(Color(0xF02C4F9E), Color(0xF012306E), Color(0xF0071433))))
                .border(1.dp, Color(0xFF5C7FD0), RoundedCornerShape(6.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvText(data.channel?.number ?: "", size = 34.sp, bold = true, color = yellow)
                Spacer(Modifier.width(12.dp))
                ChannelLogo(data.channel, Modifier.size(width = 80.dp, height = 36.dp))
                Spacer(Modifier.width(12.dp))
                TvText(data.channel?.name?.uppercase() ?: "", size = 16.sp, color = Color.White, modifier = Modifier.weight(1f))
                val now = data.clock.value
                TvText(
                    satelliteDate.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())) + "  " + formatTime(now, data.clock24h),
                    size = 15.sp,
                    color = Color.White,
                )
            }
            data.now?.let { now ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvText(guideTitle(now), size = 20.sp, bold = true, color = Color.White, modifier = Modifier.weight(1f))
                    TvText(
                        "${formatTime(now.startMs, data.clock24h)} to ${formatTime(now.endMs, data.clock24h)}",
                        size = 14.sp,
                        color = yellow,
                    )
                }
                episodeLine(now)?.takeIf { !now.movie }?.let { TvText(it, size = 14.sp, color = Color(0xFFD0DAF0)) }
                val progress = ((data.clock.value - now.startMs).toFloat() / (now.endMs - now.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .padding(vertical = 6.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0x55FFFFFF)),
                ) { Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(yellow)) }
            }
            data.next?.let { next ->
                Row {
                    TvText("NEXT: ", size = 14.sp, bold = true, color = yellow)
                    TvText(
                        "${guideTitle(next)}   ${formatTime(next.startMs, data.clock24h)} to ${formatTime(next.endMs, data.clock24h)}",
                        size = 14.sp,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
