package com.github.damontecres.wholphin.tvmode.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.damontecres.wholphin.tvmode.TuningStyle
import com.github.damontecres.wholphin.tvmode.core.TvChannel
import kotlinx.coroutines.delay
import kotlin.random.Random

/** What covers the picture while a channel tunes in or airs filler. */
@Composable
fun TuningSplash(
    style: TuningStyle,
    channel: TvChannel?,
    showLogo: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (style) {
            TuningStyle.STATIC -> StaticNoise(Modifier.fillMaxSize())
            TuningStyle.COLOR_BARS -> ColorBars(Modifier.fillMaxSize())
            TuningStyle.STANDBY -> StandbyCard(channel, compact, Modifier.fillMaxSize())
        }
        if (style != TuningStyle.STANDBY) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (showLogo && channel?.logoUrl != null) {
                    ChannelLogo(channel, Modifier.size(width = if (compact) 120.dp else 280.dp, height = if (compact) 68.dp else 158.dp))
                    Spacer(Modifier.height(8.dp))
                }
                if (style == TuningStyle.STATIC) {
                    TvText(
                        text = "TUNING…",
                        size = if (compact) 14.sp else 22.sp,
                        color = Color.White,
                        modifier =
                            Modifier
                                .background(
                                    Color(0x99000000),
                                    RoundedCornerShape(4.dp),
                                ).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** Analogue-TV snow. */
@Composable
fun StaticNoise(modifier: Modifier = Modifier) {
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            frame++
        }
    }
    Canvas(modifier) {
        val random = Random(frame)
        val cell = 6.dp.toPx()
        val columns = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        drawRect(Color(0xFF101010))
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val v = random.nextFloat()
                if (v > 0.45f) drawRect(Color(v, v, v), Offset(column * cell, row * cell), Size(cell, cell))
            }
        }
    }
}

private val bars =
    listOf(
        Color(0xFFC0C0C0),
        Color(0xFFC0C000),
        Color(0xFF00C0C0),
        Color(0xFF00C000),
        Color(0xFFC000C0),
        Color(0xFFC00000),
        Color(0xFF0000C0),
    )

/** Test-card colour bars. */
@Composable
fun ColorBars(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val width = size.width / bars.size
        val top = size.height * 0.67f
        val middle = size.height * 0.08f
        bars.forEachIndexed { i, color -> drawRect(color, Offset(i * width, 0f), Size(width + 1, top)) }
        // The narrow strip underneath runs the bars in reverse, with black between.
        bars.reversed().forEachIndexed { i, color ->
            drawRect(if (i % 2 == 0) color else Color(0xFF131313), Offset(i * width, top), Size(width + 1, middle))
        }
        drawRect(Color(0xFF101010), Offset(0f, top + middle), Size(size.width, size.height - top - middle))
        drawRect(Color(0xFF00214C), Offset(0f, top + middle), Size(width * 1.25f, size.height - top - middle))
        drawRect(Color.White, Offset(width * 1.25f, top + middle), Size(width * 1.25f, size.height - top - middle))
        drawRect(Color(0xFF32006A), Offset(width * 2.5f, top + middle), Size(width * 1.25f, size.height - top - middle))
    }
}

@Composable
private fun StandbyCard(
    channel: TvChannel?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTvTheme.current
    Box(modifier.background(Color(0xFF0B1E4A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (channel?.logoUrl != null) {
                ChannelLogo(channel, Modifier.size(width = if (compact) 110.dp else 240.dp, height = if (compact) 62.dp else 135.dp))
                Spacer(Modifier.height(12.dp))
            }
            TvText("PLEASE STAND BY", size = if (compact) 18.sp else 44.sp, bold = true, color = Color.White)
            channel?.let {
                Spacer(Modifier.height(8.dp))
                TvText("${it.number}  ${it.name}", size = if (compact) 12.sp else 20.sp, color = theme.accent)
            }
        }
    }
}
