package com.github.damontecres.wholphin.tvmode

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import com.github.damontecres.wholphin.tvmode.core.ChannelKind
import com.github.damontecres.wholphin.tvmode.ui.BreakCard
import com.github.damontecres.wholphin.tvmode.ui.ClassicOverlay
import com.github.damontecres.wholphin.tvmode.ui.CrtOverlay
import com.github.damontecres.wholphin.tvmode.ui.DigitOverlay
import com.github.damontecres.wholphin.tvmode.ui.FeaturePresentationCard
import com.github.damontecres.wholphin.tvmode.ui.FourThreeFrame
import com.github.damontecres.wholphin.tvmode.ui.GuideModel
import com.github.damontecres.wholphin.tvmode.ui.ImageUrls
import com.github.damontecres.wholphin.tvmode.ui.LineupOverlay
import com.github.damontecres.wholphin.tvmode.ui.LocalTvTheme
import com.github.damontecres.wholphin.tvmode.ui.MusicScreen
import com.github.damontecres.wholphin.tvmode.ui.OverlayData
import com.github.damontecres.wholphin.tvmode.ui.PausedScreensaver
import com.github.damontecres.wholphin.tvmode.ui.RatingBug
import com.github.damontecres.wholphin.tvmode.ui.RetroGuide
import com.github.damontecres.wholphin.tvmode.ui.SatelliteOverlay
import com.github.damontecres.wholphin.tvmode.ui.SearchSheet
import com.github.damontecres.wholphin.tvmode.ui.SettingsDialog
import com.github.damontecres.wholphin.tvmode.ui.SignedOffCard
import com.github.damontecres.wholphin.tvmode.ui.SoundFx
import com.github.damontecres.wholphin.tvmode.ui.StillWatchingPrompt
import com.github.damontecres.wholphin.tvmode.ui.ThemeEditorDialog
import com.github.damontecres.wholphin.tvmode.ui.ThemeRoles
import com.github.damontecres.wholphin.tvmode.ui.TrailerPanel
import com.github.damontecres.wholphin.tvmode.ui.TuningSplash
import com.github.damontecres.wholphin.tvmode.ui.TvText
import com.github.damontecres.wholphin.tvmode.ui.TvTheme
import com.github.damontecres.wholphin.tvmode.ui.UpNextCard
import com.github.damontecres.wholphin.tvmode.ui.Watermark
import com.github.damontecres.wholphin.tvmode.ui.WeatherScreen

/**
 * TV mode. Full screen: Up/Down or Channel +/- change channel, digits tune by number, OK or Back opens the guide,
 * Left/Right/Info show the overlay, Play/Pause pauses, a long press on Back returns to the previous channel. In the
 * guide, OK on the programme airing now tunes to it and Back leaves TV mode.
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
    var showSearch by remember { mutableStateOf(false) }
    val backState = remember { BackPress() }
    val images = remember(viewModel) { ImageUrls(viewModel::imageUrl) }
    val customThemes by viewModel.customThemes.collectAsState()
    var showThemeEditor by remember { mutableStateOf(false) }
    val theme =
        remember(settings.theme, settings.accent, settings.focus, settings.customTheme, customThemes) {
            val custom = customThemes.firstOrNull { it.id == settings.customTheme }
            val base = custom?.let { ThemeRoles.apply(TvTheme.of(it.base), it.colors) } ?: TvTheme.of(settings.theme)
            base.customized(settings.accent.argb, settings.focus.argb)
        }
    val sounds = remember { SoundFx() }
    DisposableEffect(Unit) { onDispose { sounds.release() } }
    LaunchedEffect(state.channel?.id) { if (settings.uiSounds && state.channel != null) sounds.channelChange() }
    LaunchedEffect(state.showGuide) { if (settings.uiSounds && state.showGuide) sounds.guideOpen() }

    // One video surface, moved between full screen and the guide's preview window without being recreated.
    val video =
        remember(player) {
            player?.let { p -> movableContentOf { scale: ContentScale -> VideoSurface(p, scale) } }
        }
    val picture: @Composable (Boolean, ContentScale) -> Unit = { compact, scale ->
        Box(Modifier.fillMaxSize()) {
            video?.invoke(scale)
            if (state.channel?.kind == ChannelKind.WEATHER) {
                WeatherScreen(state.channel, state.weather, state.weatherError, clock, settings.clock24h, compact, Modifier.fillMaxSize())
                return@Box
            }
            state.playing?.takeIf { it.audio && !state.showStatic }?.let {
                MusicScreen(
                    state.channel,
                    it,
                    images,
                    compact,
                    Modifier.fillMaxSize(),
                )
            }
            val breakUntil = state.breakUntilMs
            if (state.showStatic || state.loading) {
                if (breakUntil != null && settings.breakScreens && !state.loading) {
                    BreakCard(state.channel, breakUntil, state.next, clock, Modifier.fillMaxSize())
                } else {
                    TuningSplash(settings.tuningStyle, state.channel, settings.tuningLogo, compact, Modifier.fillMaxSize())
                }
            }
        }
    }

    CompositionLocalProvider(LocalTvTheme provides theme) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .focusRequester(focusRequester)
                    .onKeyEvent {
                        handleKey(it, viewModel, state, backState) {
                            // Back closes the search sheet before anything else.
                            if (showSearch) {
                                showSearch = false
                                true
                            } else {
                                false
                            }
                        }
                    }.focusable(),
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
                            onSearch = { showSearch = true },
                            serviceName = state.serviceName,
                            sleepLabel = sleepLabel(state.sleepAtMs, state.nowMs),
                            onSleep = viewModel::cycleSleep,
                        ),
                    preview = { picture(true, ContentScale.Fit) },
                )
                if (showSearch) {
                    SearchSheet(state.guide, state.nowMs, onTune = {
                        showSearch = false
                        viewModel.tuneTo(it)
                    })
                }
            } else {
                FullScreen(state, settings, clock, details, images, picture)
            }

            if (state.digits.isNotEmpty()) {
                DigitOverlay(state.digits, state.digitSlots, state.digitError, Modifier.align(Alignment.TopStart).padding(32.dp))
            }
            if (state.paused) PausedScreensaver(state.channel)
            if (state.stillWatching) StillWatchingPrompt(state.stillWatchingUntilMs, clock)
            if (state.signedOff) SignedOffCard(state.channel)

            state.error?.let {
                TvText(it, color = Color.White, maxLines = 4, modifier = Modifier.align(Alignment.Center).padding(32.dp))
            }
        }

        if (showSettings) {
            SettingsDialog(
                settings,
                viewModel::updateSettings,
                onDismiss = { showSettings = false },
                customThemes = customThemes,
                onEditThemes = {
                    showSettings = false
                    showThemeEditor = true
                },
            )
        }
        if (showThemeEditor) {
            ThemeEditorDialog(
                themes = customThemes,
                currentBase = settings.theme,
                nextId = viewModel::nextThemeId,
                onSave = viewModel::saveTheme,
                onDelete = viewModel::deleteTheme,
                onUse = { id -> viewModel.updateSettings { it.copy(customTheme = id) } },
                onDismiss = { showThemeEditor = false },
            )
        }
    }

    LaunchedEffect(state.showGuide, showSettings, showThemeEditor) {
        if (!state.showGuide && !showSettings && !showThemeEditor) runCatching { focusRequester.requestFocus() }
        if (!state.showGuide) showSearch = false
    }
}

@Composable
private fun FullScreen(
    state: CableTvUiState,
    settings: TvModeSettings,
    clock: androidx.compose.runtime.State<Long>,
    details: androidx.compose.runtime.State<Map<String, com.github.damontecres.wholphin.tvmode.core.ItemDetails>>,
    images: ImageUrls,
    picture: @Composable (Boolean, ContentScale) -> Unit,
) {
    when (settings.displayMode) {
        DisplayMode.ORIGINAL -> picture(false, ContentScale.Fit)
        DisplayMode.CROP_4_3 -> FourThreeFrame(bezel = false) { picture(false, ContentScale.Crop) }
        DisplayMode.LETTERBOX_4_3 -> FourThreeFrame(bezel = false) { picture(false, ContentScale.Fit) }
        DisplayMode.BEZEL -> FourThreeFrame(bezel = true) { picture(false, ContentScale.Crop) }
    }
    CrtOverlay(settings.scanlines, settings.vignette && settings.displayMode != DisplayMode.BEZEL)

    val playing = !state.showStatic && !state.loading
    state.playing?.takeIf { it.trailer && playing && !state.showBanner }?.let { TrailerPanel(it, state.trailerTarget, settings.clock24h) }
    if (playing) {
        Watermark(state.channel, settings.watermark, settings.watermarkCorner, settings.watermarkOpacity, clock)
        if (settings.featurePresentation) FeaturePresentationCard(state.programStart, clock)
        if (settings.ratingBug) RatingBug(state.programStart, clock)
        if (settings.upNextCard && !state.showBanner) UpNextCard(state.now, state.next, clock, settings.clock24h)
    }
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
            OverlayStyle.SATELLITE -> SatelliteOverlay(data)
        }
    }
}

private fun sleepLabel(
    sleepAtMs: Long?,
    nowMs: Long,
): String = sleepAtMs?.let { "SLEEP ${((it - nowMs) / 60_000).coerceAtLeast(1)}m" } ?: "SLEEP OFF"

@Composable
private fun VideoSurface(
    player: Player,
    scale: ContentScale,
) {
    // Letterbox or pillarbox to the video's own aspect ratio (pixel aspect included) instead of stretching.
    val presentation = rememberPresentationState(player)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.resizeWithContentScale(scale, presentation.videoSizeDp),
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
    state: CableTvUiState,
    back: BackPress,
    closeOverlay: () -> Boolean,
): Boolean {
    if (event.type == KeyEventType.KeyDown) viewModel.noteInput()
    // A signed-off TV wakes on any key (noteInput re-tunes); swallow the press.
    if (state.signedOff || state.stillWatching) return true
    if (state.paused) {
        if (event.type == KeyEventType.KeyUp) viewModel.togglePause()
        return true
    }
    if (event.key == Key.Back) {
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (event.nativeKeyEvent.repeatCount > 0 && !back.longPressHandled && !state.showGuide) {
                    back.longPressHandled = true
                    viewModel.lastChannel()
                }
            }

            KeyEventType.KeyUp -> {
                if (back.longPressHandled) {
                    back.longPressHandled = false
                } else if (!closeOverlay()) {
                    viewModel.back()
                }
            }
        }
        return true
    }
    if (event.type != KeyEventType.KeyDown) return false
    // With the guide open, let focus move through it; only the guide keys are ours.
    if (state.showGuide) {
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

        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
            // On a trailer whose show is on now, OK tunes there.
            if (state.playing?.trailer == true &&
                state.trailerTarget?.airingNow == true
            ) {
                viewModel.tuneToTrailerTarget()
            } else {
                viewModel.toggleGuide()
            }
            true
        }

        Key.Guide, Key.Menu -> {
            viewModel.toggleGuide()
            true
        }

        Key.Info, Key.DirectionLeft, Key.DirectionRight -> {
            viewModel.showInfo()
            true
        }

        Key.MediaPlayPause, Key.MediaPause -> {
            viewModel.togglePause()
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
