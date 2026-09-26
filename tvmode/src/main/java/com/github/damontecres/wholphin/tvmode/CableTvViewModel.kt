package com.github.damontecres.wholphin.tvmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.github.damontecres.wholphin.tvmode.core.CableTvClient
import com.github.damontecres.wholphin.tvmode.core.ChannelNavigator
import com.github.damontecres.wholphin.tvmode.core.Guide
import com.github.damontecres.wholphin.tvmode.core.GuideEntry
import com.github.damontecres.wholphin.tvmode.core.PlayItem
import com.github.damontecres.wholphin.tvmode.core.ScheduleCache
import com.github.damontecres.wholphin.tvmode.core.TuneInPlanner
import com.github.damontecres.wholphin.tvmode.core.TunePlan
import com.github.damontecres.wholphin.tvmode.core.TvChannel
import com.github.damontecres.wholphin.tvmode.core.TvSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

/** One guide row. */
data class GuideRow(
    val channel: TvChannel,
    val entries: List<GuideEntry>,
)

data class CableTvUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val channel: TvChannel? = null,
    val now: GuideEntry? = null,
    val next: GuideEntry? = null,
    val showBanner: Boolean = false,
    val showStatic: Boolean = true,
    val digits: String = "",
    val showGuide: Boolean = false,
    val guide: List<GuideRow> = emptyList(),
    val guideStartMs: Long = 0,
    val nowMs: Long = 0,
)

/**
 * Runs the TV: tunes channels from the cached schedule (no network call on a channel change), keeps the player's
 * queue a few items ahead so the next show or break is preloaded, and shows static while a new file buffers or when
 * the schedule says filler.
 */
@HiltViewModel
class CableTvViewModel
    @Inject
    constructor(
        private val host: TvModeHost,
    ) : ViewModel() {
        private val client = CableTvClient(host.transport)
        private val cache = ScheduleCache()
        private val navigator = ChannelNavigator(emptyList())

        private val _state = MutableStateFlow(CableTvUiState())
        val state: StateFlow<CableTvUiState> = _state.asStateFlow()

        private val _player = MutableStateFlow<Player?>(null)
        val player: StateFlow<Player?> = _player.asStateFlow()

        private var queued: List<PlayItem> = emptyList()
        private var staticUntilMs: Long? = null
        private var bannerHideAtMs = 0L
        private var digitsCommitAtMs: Long? = null
        private var lastDriftCheckMs = 0L
        private var lastChannelRefreshMs = 0L
        private var tuneJob: Job? = null

        private val listener =
            object : Player.Listener {
                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    val slot = mediaItem?.slot() ?: return
                    showNowAndNext(slot)
                    extendQueue(slot)
                }

                override fun onRenderedFirstFrame() {
                    _state.update { it.copy(showStatic = false) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        // The queue ran out: filler follows, or the schedule moved on. Work out what airs now.
                        Player.STATE_ENDED -> retune()

                        Player.STATE_BUFFERING -> _state.update { it.copy(showStatic = true) }

                        Player.STATE_READY -> _state.update { it.copy(showStatic = false) }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    // A file that won't play: cover it with static and try again when the next slot starts.
                    val current = queued.firstOrNull { it.slot.covers(client.clock.nowMs()) }?.slot
                    staticUntilMs = current?.endMs ?: (client.clock.nowMs() + 10_000)
                    _state.update { it.copy(showStatic = true) }
                }
            }

        init {
            viewModelScope.launch { start() }
            viewModelScope.launch { tick() }
        }

        private suspend fun start() {
            try {
                val channels = withContext(Dispatchers.IO) { client.channels() }
                lastChannelRefreshMs = client.clock.nowMs()
                if (channels.isEmpty()) {
                    _state.update { it.copy(loading = false, error = "The server has no Cable TV channels yet.") }
                    return
                }
                navigator.update(channels)
                val player = host.createPlayer()
                player.addListener(listener)
                player.playWhenReady = true
                _player.value = player
                _state.update { it.copy(loading = false) }
                navigator.current?.let { tune(it) }
                refreshGuide()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Couldn't reach the Cable TV plugin: ${e.message}") }
            }
        }

        fun channelUp() = navigator.up()?.let(::tune)

        fun channelDown() = navigator.down()?.let(::tune)

        fun lastChannel() = navigator.last()?.let(::tune)

        fun tuneTo(channelId: String) {
            _state.update { it.copy(showGuide = false) }
            navigator.tuneTo(channelId)?.let(::tune)
        }

        fun digit(value: Int) {
            val channel = navigator.typeDigit(value)
            if (channel != null) {
                digitsCommitAtMs = null
                tune(channel)
            } else {
                digitsCommitAtMs = client.clock.nowMs() + DIGIT_TIMEOUT_MS
                _state.update { it.copy(digits = navigator.pendingDigits, showBanner = true) }
                bannerHideAtMs = client.clock.nowMs() + BANNER_MS
            }
        }

        fun exit() = host.exit()

        fun showInfo() {
            bannerHideAtMs = client.clock.nowMs() + BANNER_MS
            _state.update { it.copy(showBanner = true) }
        }

        fun toggleGuide() {
            val show = !_state.value.showGuide
            _state.update { it.copy(showGuide = show) }
            if (show) viewModelScope.launch { refreshGuide() }
        }

        fun closeGuide(): Boolean {
            if (!_state.value.showGuide) return false
            _state.update { it.copy(showGuide = false) }
            return true
        }

        private fun tune(channel: TvChannel) {
            tuneJob?.cancel()
            staticUntilMs = null
            bannerHideAtMs = client.clock.nowMs() + BANNER_MS
            _state.update { it.copy(channel = channel, digits = "", showBanner = true, showStatic = true) }
            tuneJob =
                viewModelScope.launch {
                    val player = _player.value ?: return@launch
                    val now = client.clock.nowMs()
                    if (!cache.covers(channel.id, now, MIN_AHEAD_MS)) {
                        fetch(listOf(channel.id), now)
                    }
                    when (val plan = TuneInPlanner.plan(cache.slotsFrom(channel.id, client.clock.nowMs()), client.clock.nowMs())) {
                        is TunePlan.Play -> {
                            queued = plan.items
                            player.setMediaItems(plan.items.map(::toMediaItem), 0, plan.startPositionMs)
                            player.prepare()
                            player.play()
                            showNowAndNext(plan.items.first().slot)
                        }

                        is TunePlan.Static -> {
                            queued = emptyList()
                            player.stop()
                            player.clearMediaItems()
                            staticUntilMs = plan.untilMs
                            showNowAndNext(plan.slot)
                            _state.update { it.copy(showStatic = true) }
                        }

                        TunePlan.NeedsSchedule -> {
                            staticUntilMs = client.clock.nowMs() + 5_000
                            _state.update { it.copy(showStatic = true) }
                        }
                    }
                }
        }

        private fun retune() {
            navigator.current?.let(::tune)
        }

        private fun extendQueue(playing: TvSlot) {
            val channel = navigator.current ?: return
            val player = _player.value ?: return
            val last = queued.lastOrNull()?.slot ?: return
            val more = TuneInPlanner.extend(cache.slotsFrom(channel.id, playing.startMs), playing, last)
            if (more.isNotEmpty()) {
                queued = queued.dropWhile { it.slot.endMs <= playing.startMs } + more
                player.addMediaItems(more.map(::toMediaItem))
            }
        }

        private fun toMediaItem(item: PlayItem): MediaItem =
            MediaItem
                .Builder()
                .setUri(host.streamUrl(item.slot.itemId!!, item.slot.mediaSourceId))
                .setMediaId(item.slot.id)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration
                        .Builder()
                        .setStartPositionMs(item.clipStartMs)
                        .setEndPositionMs(item.clipEndMs)
                        .build(),
                ).setTag(item.slot)
                .build()

        private fun MediaItem.slot(): TvSlot? = localConfiguration?.tag as? TvSlot

        private fun showNowAndNext(slot: TvSlot) {
            val channel = navigator.current ?: return
            val entries = Guide.entries(cache.slotsBetween(channel.id, slot.startMs - GUIDE_BACK_MS, slot.endMs + GUIDE_AHEAD_MS))
            val (now, next) = Guide.nowAndNext(entries, client.clock.nowMs())
            _state.update { it.copy(now = now, next = next) }
        }

        private suspend fun fetch(
            channelIds: List<String>,
            nowMs: Long,
        ) {
            val from = nowMs - FETCH_BACK_MS
            val to = nowMs + FETCH_AHEAD_MS
            try {
                val schedules = withContext(Dispatchers.IO) { client.schedule(channelIds, from, to) }
                schedules.forEach { cache.merge(it, from, to) }
            } catch (e: Exception) {
                // Keep playing from what's cached; the next tick tries again.
            }
        }

        private suspend fun refreshGuide() {
            val channels = navigator.channels
            if (channels.isEmpty()) return
            val now = client.clock.nowMs()
            val missing = channels.filter { !cache.covers(it.id, now, GUIDE_AHEAD_MS) }.map { it.id }
            if (missing.isNotEmpty()) fetch(missing, now)
            val start = now - (now % HALF_HOUR_MS)
            val rows = channels.map { GuideRow(it, Guide.entries(cache.slotsBetween(it.id, start, start + GUIDE_AHEAD_MS))) }
            _state.update { it.copy(guide = rows, guideStartMs = start) }
        }

        private suspend fun refreshChannels() {
            try {
                val channels = withContext(Dispatchers.IO) { client.channels() }
                channels.forEach { channel ->
                    // A new version means the plugin rebuilt the channel: drop what's cached and re-tune if watching it.
                    if (cache.version(channel.id) != null && cache.version(channel.id) != channel.scheduleVersion) {
                        cache.invalidate(channel.id)
                        if (navigator.current?.id == channel.id) retune()
                    }
                }
                navigator.update(channels)
            } catch (e: Exception) {
                // Try again next time.
            }
        }

        private suspend fun tick() {
            while (viewModelScope.isActive) {
                delay(TICK_MS)
                val now = client.clock.nowMs()
                _state.update { it.copy(nowMs = now) }

                digitsCommitAtMs?.let { at ->
                    if (now >= at) {
                        digitsCommitAtMs = null
                        val channel = navigator.commitDigits()
                        if (channel != null) tune(channel) else _state.update { it.copy(digits = "") }
                    }
                }

                if (_state.value.showBanner && now >= bannerHideAtMs && _state.value.digits.isEmpty()) {
                    _state.update { it.copy(showBanner = false) }
                }

                staticUntilMs?.let { until ->
                    if (now >= until) {
                        staticUntilMs = null
                        retune()
                    }
                }

                val channel = navigator.current ?: continue
                if (!cache.covers(channel.id, now, REFRESH_WHEN_LESS_THAN_MS)) {
                    fetch(listOf(channel.id), now)
                }

                if (now - lastDriftCheckMs >= DRIFT_CHECK_MS) {
                    lastDriftCheckMs = now
                    checkDrift(now)
                }

                if (now - lastChannelRefreshMs >= CHANNEL_REFRESH_MS) {
                    lastChannelRefreshMs = now
                    refreshChannels()
                }
            }
        }

        /** Re-tunes when playback has wandered from the schedule, for example after a long stall. */
        private fun checkDrift(now: Long) {
            val player = _player.value ?: return
            if (player.playbackState != Player.STATE_READY || !player.isPlaying) return
            val slot = player.currentMediaItem?.slot() ?: return
            if (abs(TuneInPlanner.drift(slot, player.currentPosition, now)) > MAX_DRIFT_MS) retune()
        }

        override fun onCleared() {
            _player.value?.let {
                it.removeListener(listener)
                host.releasePlayer(it)
            }
            _player.value = null
            super.onCleared()
        }

        private companion object {
            const val TICK_MS = 500L
            const val BANNER_MS = 4_000L
            const val DIGIT_TIMEOUT_MS = 2_000L
            const val DRIFT_CHECK_MS = 10_000L
            const val MAX_DRIFT_MS = 5_000L
            const val CHANNEL_REFRESH_MS = 10 * 60_000L
            const val MIN_AHEAD_MS = 30 * 60_000L
            const val REFRESH_WHEN_LESS_THAN_MS = 2 * 60 * 60_000L
            const val FETCH_BACK_MS = 60 * 60_000L
            const val FETCH_AHEAD_MS = 6 * 60 * 60_000L
            const val GUIDE_BACK_MS = 60 * 60_000L
            const val GUIDE_AHEAD_MS = 3 * 60 * 60_000L
            const val HALF_HOUR_MS = 30 * 60_000L
        }
    }
