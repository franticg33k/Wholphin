package com.github.damontecres.wholphin.tvmode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.tvmode.core.GuideEntry
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.random.Random

/**
 * Full-screen TV mode. Up/down or channel keys change channel, digits tune by number, OK opens the guide, Info shows
 * the banner, Back closes the guide or leaves.
 */
@Composable
fun CableTvScreen(
    modifier: Modifier = Modifier,
    viewModel: CableTvViewModel = hiltViewModel(),
    onExit: () -> Unit = viewModel::exit,
) {
    val state by viewModel.state.collectAsState()
    val player by viewModel.player.collectAsState()
    val focusRequester = remember { FocusRequester() }

    BackHandler {
        if (!viewModel.closeGuide()) onExit()
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .onKeyEvent { handleKey(it, viewModel, state.showGuide) }
                .focusable(),
    ) {
        player?.let {
            PlayerSurface(
                player = it,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (state.showStatic || state.loading) {
            StaticNoise(Modifier.fillMaxSize())
        }

        state.error?.let {
            Text(
                text = it,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }

        if (state.showBanner && !state.showGuide) {
            ChannelBanner(state, Modifier.align(Alignment.BottomStart).fillMaxWidth())
        }

        if (state.showGuide) {
            GuideOverlay(state, onTune = viewModel::tuneTo, modifier = Modifier.fillMaxSize())
        }
    }

    LaunchedEffect(state.showGuide) {
        if (!state.showGuide) focusRequester.requestFocus()
    }
}

private fun handleKey(
    event: KeyEvent,
    viewModel: CableTvViewModel,
    guideOpen: Boolean,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    // With the guide open, let focus move through its cells; only the guide keys are ours.
    if (guideOpen) {
        return when (event.key) {
            Key.Guide, Key.Menu -> {
                viewModel.toggleGuide()
                true
            }

            else -> {
                false
            }
        }
    }
    digitOf(event.key)?.let {
        viewModel.digit(it)
        return true
    }
    return when (event.key) {
        Key.DirectionUp, Key.ChannelUp, Key.PageUp -> {
            viewModel.channelUp()
            true
        }

        Key.DirectionDown, Key.ChannelDown, Key.PageDown -> {
            viewModel.channelDown()
            true
        }

        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Guide, Key.Menu -> {
            viewModel.toggleGuide()
            true
        }

        Key.Info, Key.DirectionLeft, Key.DirectionRight -> {
            viewModel.showInfo()
            true
        }

        Key.LastChannel -> {
            viewModel.lastChannel()
            true
        }

        else -> {
            false
        }
    }
}

private fun digitOf(key: Key): Int? =
    when (key) {
        Key.Zero, Key.NumPad0 -> 0
        Key.One, Key.NumPad1 -> 1
        Key.Two, Key.NumPad2 -> 2
        Key.Three, Key.NumPad3 -> 3
        Key.Four, Key.NumPad4 -> 4
        Key.Five, Key.NumPad5 -> 5
        Key.Six, Key.NumPad6 -> 6
        Key.Seven, Key.NumPad7 -> 7
        Key.Eight, Key.NumPad8 -> 8
        Key.Nine, Key.NumPad9 -> 9
        else -> null
    }

/** Analogue-TV static, shown while a file buffers and during filler. */
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
                if (v > 0.45f) {
                    drawRect(
                        color = Color(v, v, v),
                        topLeft =
                            androidx.compose.ui.geometry
                                .Offset(column * cell, row * cell),
                        size =
                            androidx.compose.ui.geometry
                                .Size(cell, cell),
                    )
                }
            }
        }
    }
}

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun time(ms: Long): String = timeFormat.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

@Composable
private fun ChannelBanner(
    state: CableTvUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(Color(0xCC000000))
                .padding(horizontal = 48.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.digits.ifEmpty { state.channel?.number ?: "" },
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(24.dp))
            Text(text = state.channel?.name ?: "", color = Color.White, fontSize = 24.sp)
        }
        state.now?.let { now ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = listOfNotNull(now.title, now.episode, now.episodeTitle).joinToString(" · ") + if (now.premiere) "  NEW" else "",
                color = Color.White,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val remaining = ((now.endMs - state.nowMs) / 60_000).coerceAtLeast(0)
            Text(
                text = "${time(now.startMs)} – ${time(now.endMs)} · $remaining min left",
                color = Color(0xFFBBBBBB),
                fontSize = 16.sp,
            )
        }
        state.next?.let { next ->
            Text(
                text = "Next: ${time(next.startMs)} ${next.title}",
                color = Color(0xFFBBBBBB),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val DP_PER_MINUTE = 6.dp

@Composable
private fun GuideOverlay(
    state: CableTvUiState,
    onTune: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstCell = remember { FocusRequester() }
    Column(
        modifier =
            modifier
                .background(Color(0xE6000000))
                .padding(32.dp),
    ) {
        Text(text = "Guide", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.guide, key = { it.channel.id }) { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(160.dp)) {
                        Text(text = row.channel.number, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(text = row.channel.name, color = Color(0xFFBBBBBB), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(row.entries, key = { it.guideGroup }) { entry ->
                            val isCurrentRow = row.channel.id == state.channel?.id
                            val airing = state.nowMs in entry.startMs until entry.endMs
                            GuideCell(
                                entry = entry,
                                visibleFromMs = state.guideStartMs,
                                airing = airing,
                                onClick = { onTune(row.channel.id) },
                                modifier = if (isCurrentRow && airing) Modifier.focusRequester(firstCell) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(state.guide.isNotEmpty()) {
        if (state.guide.isNotEmpty()) {
            runCatching { firstCell.requestFocus() }
        }
    }
}

@Composable
private fun GuideCell(
    entry: GuideEntry,
    visibleFromMs: Long,
    airing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val minutes = ((entry.endMs - maxOf(entry.startMs, visibleFromMs)) / 60_000).coerceIn(5, 240)
    val background =
        when {
            focused -> MaterialTheme.colorScheme.primary
            airing -> Color(0xFF2E3440)
            else -> Color(0xFF1E2128)
        }
    Column(
        modifier =
            modifier
                .width(DP_PER_MINUTE * minutes.toInt())
                .height(56.dp)
                .background(background, RoundedCornerShape(4.dp))
                .border(1.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(4.dp))
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = entry.title + if (entry.premiere) "  NEW" else "",
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = time(entry.startMs) + (entry.lineup?.let { " · $it" } ?: ""),
            color = Color(0xFFBBBBBB),
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}
