package com.github.damontecres.wholphin.tvmode

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.github.damontecres.wholphin.tvmode.core.CableTvClient
import com.github.damontecres.wholphin.tvmode.core.ChannelKind
import com.github.damontecres.wholphin.tvmode.core.TuneInPlanner
import com.github.damontecres.wholphin.tvmode.core.TunePlan
import com.github.damontecres.wholphin.tvmode.core.TvChannel
import com.github.damontecres.wholphin.tvmode.ui.ChannelLogo
import com.github.damontecres.wholphin.tvmode.ui.TvText
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** What the surfing screensaver is showing. */
data class SurfingState(
    val player: Player? = null,
    val channel: TvChannel? = null,
    /** The programme on the channel now. */
    val title: String? = null,
    /** Changes with each channel, so the channel bug can show again. */
    val tunes: Int = 0,
    /** True when there is nothing to surf (no plugin, no channels, no session): show the normal screensaver. */
    val unavailable: Boolean = false,
)

/**
 * A screensaver that surfs Cable TV: picks a channel (at random, favourites first when there are any), plays what is on
 * it with the sound off, and moves to another every so often. Everything comes from the plugin's schedule, as in the TV
 * mode, so the picture is whatever would be on at that moment.
 */
class SurfingSession(
    private val host: TvModeHost,
    private val settings: TvModeSettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(SurfingState())
    val state: StateFlow<SurfingState> = _state.asStateFlow()
    private var player: Player? = null

    fun start() {
        scope.launch {
            try {
                if (!host.ensureSignedIn()) return@launch unavailable()
                val client = CableTvClient(host.transport)
                val channels = surfable(client)
                if (channels.isEmpty()) return@launch unavailable()
                val created = host.createPlayer()
                player = created
                created.volume = 0f
                created.trackSelectionParameters =
                    created.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                created.playWhenReady = true
                _state.update { it.copy(player = created) }
                surf(client, channels, created)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "The surfing screensaver couldn't start: ${e.message}")
                unavailable()
            }
        }
    }

    fun stop() {
        scope.cancel()
        player?.let {
            runCatching { it.stop() }
            runCatching { host.releasePlayer(it) }
        }
        player = null
    }

    private fun unavailable() {
        _state.update { it.copy(unavailable = true) }
    }

    /** Channels with something to watch, favourites only when the viewer has some. */
    private suspend fun surfable(client: CableTvClient): List<TvChannel> {
        val all = client.channels().filter { it.kind == ChannelKind.STANDARD && it.poolSize > 0 }
        val favourites = settings.favourites.value
        return all.filter { it.id in favourites }.ifEmpty { all }
    }

    private suspend fun surf(
        client: CableTvClient,
        channels: List<TvChannel>,
        player: Player,
    ) {
        var last: TvChannel? = null
        var misses = 0
        while (true) {
            val candidates = if (channels.size > 1) channels.filter { it.id != last?.id } else channels
            val channel = candidates[Random.nextInt(candidates.size)]
            val title = tune(client, channel, player)
            last = channel
            if (title == null) {
                // Nothing playable on that channel now: try another soon, and give up showing a black screen after a while.
                misses++
                if (misses >= MAX_MISSES) return unavailable()
                delay(RETRY_MS)
                continue
            }
            misses = 0
            _state.update { it.copy(channel = channel, title = title, tunes = it.tunes + 1) }
            val seconds =
                settings.settings.value.surfSeconds
                    .coerceAtLeast(MIN_SECONDS)
            delay(seconds * 1_000L)
        }
    }

    /** Starts [channel] at its scheduled point; the programme's title, or null when nothing playable airs. */
    private suspend fun tune(
        client: CableTvClient,
        channel: TvChannel,
        player: Player,
    ): String? {
        val now = client.clock.nowMs()
        val schedule =
            try {
                client.schedule(listOf(channel.id), now - LOOK_BACK_MS, now + LOOK_AHEAD_MS).firstOrNull()
            } catch (e: Exception) {
                Log.w(TAG, "No schedule for ${channel.number}: ${e.message}")
                null
            } ?: return null
        val upcoming = schedule.slots.sortedBy { it.startMs }.dropWhile { it.endMs <= now }
        val plan = TuneInPlanner.plan(upcoming, client.clock.nowMs(), queueAhead = 1)
        val play = plan as? TunePlan.Play ?: return null
        val first = play.items.first().slot
        if (first.audio || first.itemId == null) return null
        player.setMediaItems(
            play.items
                .filter { it.slot.itemId != null && !it.slot.audio }
                .map { item ->
                    MediaItem
                        .Builder()
                        .setUri(host.streamUrl(item.slot.itemId!!, item.slot.mediaSourceId, false))
                        .setMediaId(item.slot.id)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration
                                .Builder()
                                .setStartPositionMs(item.clipStartMs)
                                .setEndPositionMs(item.clipEndMs)
                                .build(),
                        ).build()
                },
            0,
            play.startPositionMs,
        )
        player.prepare()
        player.play()
        return first.title ?: channel.name
    }

    private companion object {
        const val TAG = "CableTvSurf"
        const val LOOK_BACK_MS = 5 * 60_000L
        const val LOOK_AHEAD_MS = 90 * 60_000L
        const val RETRY_MS = 3_000L
        const val MAX_MISSES = 8
        const val MIN_SECONDS = 5
    }
}

/** Makes [SurfingSession]s. */
@Singleton
class SurfingSessionFactory
    @Inject
    constructor(
        private val host: TvModeHost,
        private val settings: TvModeSettingsStore,
    ) {
        fun create(): SurfingSession = SurfingSession(host, settings)

        /** True when the viewer chose the Cable TV screensaver. */
        fun wanted(): Boolean = settings.settings.value.screensaver == ScreensaverKind.CABLE_TV
    }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SurfingEntryPoint {
    fun surfing(): SurfingSessionFactory
}

/**
 * The screensaver the app shows when idle: Cable TV channel surfing when the viewer chose it (and there are channels to
 * surf), otherwise [fallback], the app's own screensaver. Used by both the in-app screensaver and the system dream.
 */
@Composable
fun CableTvScreensaverOr(
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val factory = remember { EntryPointAccessors.fromApplication(context, SurfingEntryPoint::class.java).surfing() }
    if (!remember { factory.wanted() }) {
        fallback()
        return
    }
    val session = remember { factory.create() }
    DisposableEffect(session) {
        session.start()
        onDispose { session.stop() }
    }
    val state by session.state.collectAsState()
    if (state.unavailable) {
        fallback()
        return
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        state.player?.let { Picture(it) }
        ChannelBug(state)
    }
}

@Composable
private fun Picture(player: Player) {
    val presentation = rememberPresentationState(player)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.resizeWithContentScale(ContentScale.Fit, presentation.videoSizeDp)) {
            PlayerSurface(player = player, surfaceType = SURFACE_TYPE_SURFACE_VIEW, modifier = Modifier.fillMaxSize())
        }
    }
}

/** The channel number, logo and programme in a corner for a few seconds after each change. */
@Composable
private fun ChannelBug(state: SurfingState) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.tunes) {
        if (state.channel == null) return@LaunchedEffect
        visible = true
        delay(BUG_MS)
        visible = false
    }
    val channel = state.channel ?: return
    if (!visible) return
    Box(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp)) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .background(Color(0xB3000000), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            TvText("${channel.number}  ${channel.name.uppercase()}", size = 20.sp, bold = true, color = Color.White)
            state.title?.let { TvText(it, size = 16.sp, color = Color(0xFFD0DAF0)) }
        }
        if (channel.logoUrl != null) {
            ChannelLogo(channel, Modifier.align(Alignment.TopEnd).size(width = 120.dp, height = 56.dp))
        }
    }
}

private const val BUG_MS = 6_000L
