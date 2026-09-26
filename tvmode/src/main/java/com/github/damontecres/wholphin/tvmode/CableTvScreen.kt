package com.github.damontecres.wholphin.tvmode

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.github.damontecres.wholphin.tvmode.ui.ClassicOverlay
import com.github.damontecres.wholphin.tvmode.ui.DigitOverlay
import com.github.damontecres.wholphin.tvmode.ui.GuideModel
import com.github.damontecres.wholphin.tvmode.ui.ImageUrls
import com.github.damontecres.wholphin.tvmode.ui.LineupOverlay
import com.github.damontecres.wholphin.tvmode.ui.LocalTvTheme
import com.github.damontecres.wholphin.tvmode.ui.OverlayData
import com.github.damontecres.wholphin.tvmode.ui.RetroGuide
import com.github.damontecres.wholphin.tvmode.ui.SettingsDialog
import com.github.damontecres.wholphin.tvmode.ui.TuningSplash
import com.github.damontecres.wholphin.tvmode.ui.TvText
import com.github.damontecres.wholphin.tvmode.ui.TvTheme

/**
 * TV mode. Full screen: Up/Down or Channel +/- change channel, digits tune by number, OK or Back opens the guide,
 * Left/Right/Info show the overlay, a long press on Back returns to the previous channel. In the guide, OK on the
 * programme airing now tunes to it and Back leaves TV mode.
 */
@Composable
fun CableTvScreen(
    modifier: Modifier = Modifier,
    viewModel: CableTvViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val player by viewModel.player.collectAsState()
    val settings by viewModel.settings.collectAsState()
    // States passed down unread, so a tick or a loaded description only recomposes what shows it.
    val details = viewModel.details.collectAsState()
    val clock = viewModel.clock.collectAsState()
    val focusRequester = remember { FocusRequester() }
    var guideCategory by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val backState = remember { BackPress() }
    val images = remember(viewModel) { ImageUrls(viewModel::imageUrl) }

    // One video surface, moved between full screen and the guide's preview window without being recreated.
    val video =
        remember(player) {
            player?.let { p -> movableContentOf { VideoSurface(p) } }
        }
    val picture: @Composable (Boolean) -> Unit = { compact ->
        Box(Modifier.fillMaxSize()) {
            video?.invoke()
            if (state.showStatic || state.loading) {
                TuningSplash(settings.tuningStyle, state.channel, settings.tuningLogo, compact, Modifier.fillMaxSize())
            }
        }
    }

    CompositionLocalProvider(LocalTvTheme provides TvTheme.of(settings.theme)) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .focusRequester(focusRequester)
                    .onKeyEvent { handleKey(it, viewModel, state.showGuide, backState) }
                    .focusable(),
        ) {
            if (state.showGuide) {
                val categories = state.guide.mapNotNull { it.channel.category }.distinct()
                val selected = guideCategory?.takeIf { it in categories }
                RetroGuide(
                    model =
                        GuideModel(
                            rows = if (selected == null) state.guide else state.guide.filter { it.channel.category == selected },
                            categories = categories,
                            category = selected,
                            current = state.channel,
                            windowStartMs = state.guideWindowStartMs,
                            nowMs = state.nowMs,
                            clock = clock,
                            details = details,
                            settings = settings,
                            images = images,
                            onCategory = { guideCategory = it },
                            onTune = viewModel::tuneTo,
                            onShift = viewModel::shiftGuide,
                            onFocusItem = viewModel::requestDetails,
                            onFullscreen = viewModel::closeGuide,
                            onSettings = { showSettings = true },
                            onExit = viewModel::exit,
                        ),
                    preview = { picture(true) },
                )
            } else {
                picture(false)
                if (state.showBanner) {
                    val data =
                        OverlayData(
                            channel = state.channel,
                            now = state.now,
                            next = state.next,
                            upcoming = state.upcoming,
                            details = details,
                            nowMs = state.nowMs,
                            clock = clock,
                            clock24h = settings.clock24h,
                            showMediaInfo = settings.mediaInfo,
                            images = images,
                        )
                    when (settings.overlayStyle) {
                        OverlayStyle.CLASSIC -> ClassicOverlay(data)
                        OverlayStyle.LINEUP -> LineupOverlay(data)
                    }
                }
            }

            if (state.digits.isNotEmpty()) {
                DigitOverlay(state.digits, state.digitSlots, state.digitError, Modifier.align(Alignment.TopStart).padding(32.dp))
            }

            state.error?.let {
                TvText(it, color = Color.White, maxLines = 4, modifier = Modifier.align(Alignment.Center).padding(32.dp))
            }
        }

        if (showSettings) {
            SettingsDialog(settings, viewModel::updateSettings, onDismiss = { showSettings = false })
        }
    }

    LaunchedEffect(state.showGuide, showSettings) {
        if (!state.showGuide && !showSettings) runCatching { focusRequester.requestFocus() }
    }
}

@Composable
private fun VideoSurface(player: Player) {
    // Letterbox or pillarbox to the video's own aspect ratio (pixel aspect included) instead of stretching.
    val presentation = rememberPresentationState(player)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.resizeWithContentScale(ContentScale.Fit, presentation.videoSizeDp),
        )
    }
}

/** Tells a short Back press from a long one. */
private class BackPress {
    var longPressHandled = false
}

private fun handleKey(
    event: KeyEvent,
    viewModel: CableTvViewModel,
    guideOpen: Boolean,
    back: BackPress,
): Boolean {
    if (event.key == Key.Back) {
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (event.nativeKeyEvent.repeatCount > 0 && !back.longPressHandled && !guideOpen) {
                    back.longPressHandled = true
                    viewModel.lastChannel()
                }
            }

            KeyEventType.KeyUp -> {
                if (back.longPressHandled) back.longPressHandled = false else viewModel.back()
            }
        }
        return true
    }
    if (event.type != KeyEventType.KeyDown) return false
    // With the guide open, let focus move through it; only the guide keys are ours.
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
