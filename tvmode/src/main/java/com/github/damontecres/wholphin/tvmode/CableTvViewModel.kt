package com.github.damontecres.wholphin.tvmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.github.damontecres.wholphin.tvmode.core.CableTvClient
import com.github.damontecres.wholphin.tvmode.core.ChannelNavigator
import com.github.damontecres.wholphin.tvmode.core.Guide
import com.github.damontecres.wholphin.tvmode.core.GuideCache
import com.github.damontecres.wholphin.tvmode.core.GuideEntry
import com.github.damontecres.wholphin.tvmode.core.ItemDetails
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
    /** The programmes after [now], for the lineup overlay. */
    val upcoming: List<GuideEntry> = emptyList(),
    val showBanner: Boolean = false,
    val showStatic: Boolean = true,
    val digits: String = "",
    /** The typed number matched no channel; shown in red briefly. */
    val digitError: Boolean = false,
    /** How many digits the longest channel number has. */
    val digitSlots: Int = 4,
    val showGuide: Boolean = false,
    val guide: List<GuideRow> = emptyList(),
    val guideStartMs: Long = 0,
    /** Start of the half-hour window the guide grid shows; moves in 30-minute steps. */
    val guideWindowStartMs: Long = 0,
    /**
     * Server time, updated every [CableTvViewModel.COARSE_CLOCK_MS] so screens that only need the minute (guide
     * cells, "min left") don't recompose every tick. Use [CableTvViewModel.clock] for a running clock.
     */
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
        private val settingsStore: TvModeSettingsStore,
    ) : ViewModel() {
        val settings: StateFlow<TvModeSettings> = settingsStore.settings

        private val _details = MutableStateFlow<Map<String, ItemDetails>>(emptyMap())

        /** Programme details from Jellyfin by item id, fetched on demand (see [requestDetails]). */
        val details: StateFlow<Map<String, ItemDetails>> = _details.asStateFlow()
        private val detailsLoading = mutableSetOf<String>()
        private var focusDetailsJob: Job? = null
        private val client = CableTvClient(host.transport)
        private val cache = ScheduleCache()
        private val guideCache = GuideCache()

        /** False once the server turns out to lack the guide endpoint (an older plugin). */
        private var guideEndpoint = true

        private val _clock = MutableStateFlow(0L)

        /** Server time every second, for clocks and progress bars; read it only where it's drawn. */
        val clock: StateFlow<Long> = _clock.asStateFlow()
        private val navigator = ChannelNavigator(emptyList())

        private val _state = MutableStateFlow(CableTvUiState())
        val state: StateFlow<CableTvUiState> = _state.asStateFlow()

        private val _player = MutableStateFlow<Player?>(null)
        val player: StateFlow<Player?> = _player.asStateFlow()

        private var queued: List<PlayItem> = emptyList()
        private var staticUntilMs: Long? = null
        private var bannerHideAtMs = 0L
        private var digitsCommitAtMs: Long? = null
        private var digitErrorUntilMs = 0L
        private var lastDriftCheckMs = 0L
        private var lastChannelRefreshMs = 0L
        private var lastGuideRefreshMs = 0L
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
                _state.update { it.copy(digitSlots = channels.maxOf { c -> c.number.length }.coerceIn(1, 5)) }
                val player = host.createPlayer()
                player.addListener(listener)
                player.playWhenReady = true
                _player.value = player
                _state.update { it.copy(loading = false) }
                navigator.current?.let { tune(it) }
                // Fetch the whole guide now, so opening it later is instant.
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
            val typed = navigator.pendingDigits + value
            val channel = navigator.typeDigit(value)
            when {
                channel != null -> {
                    digitsCommitAtMs = null
                    tune(channel)
                }

                navigator.pendingDigits.isEmpty() -> {
                    // A full-length number that no channel has.
                    digitsCommitAtMs = null
                    digitErrorUntilMs = client.clock.nowMs() + DIGIT_ERROR_MS
                    _state.update { it.copy(digits = typed, digitError = true) }
                }

                else -> {
                    digitsCommitAtMs = client.clock.nowMs() + DIGIT_TIMEOUT_MS
                    _state.update { it.copy(digits = navigator.pendingDigits, digitError = false) }
                }
            }
        }

        fun exit() = host.exit()

        fun imageUrl(
            itemId: String,
            type: String,
            maxHeight: Int,
        ): String = host.imageUrl(itemId, type, maxHeight)

        fun updateSettings(transform: (TvModeSettings) -> TvModeSettings) = settingsStore.update(transform)

        /** Back: from full screen it opens the guide; from the guide it leaves TV mode. */
        fun back() {
            if (_state.value.showGuide) exit() else toggleGuide()
        }

        /** Loads a programme's details once the guide's focus has rested on it briefly. */
        fun requestDetails(itemId: String?) {
            focusDetailsJob?.cancel()
            if (itemId == null) return
            focusDetailsJob =
                viewModelScope.launch {
                    delay(FOCUS_DETAILS_DELAY_MS)
                    loadDetails(itemId)
                }
        }

        /** Moves the guide grid by [steps] half hours, within the fetched schedule. */
        fun shiftGuide(steps: Int): Boolean {
            val current = _state.value
            val start = current.guideStartMs
            val next = (current.guideWindowStartMs + steps * HALF_HOUR_MS).coerceIn(start, start + GUIDE_AHEAD_MS - GUIDE_WINDOW_MS)
            if (next == current.guideWindowStartMs) return false
            _state.update { it.copy(guideWindowStartMs = next) }
            return true
        }

        private suspend fun loadDetails(itemId: String) {
            if (_details.value.containsKey(itemId) || !detailsLoading.add(itemId)) return
            try {
                val loaded = withContext(Dispatchers.IO) { client.details(itemId) }
                _details.update { old ->
                    // Keep the most recent entries only; details are cheap to fetch again.
                    val kept = if (old.size >= MAX_DETAILS) old.entries.drop(old.size - MAX_DETAILS / 2).associate { it.toPair() } else old
                    kept + (itemId to loaded)
                }
            } catch (e: Exception) {
                // Without details the panel shows what the schedule says.
            } finally {
                detailsLoading.remove(itemId)
            }
        }

        fun showInfo() {
            bannerHideAtMs = client.clock.nowMs() + BANNER_MS
            _state.update { it.copy(showBanner = true) }
        }

        fun toggleGuide() {
            val show = !_state.value.showGuide
            _state.update { it.copy(showGuide = show, showBanner = false) }
            if (show) viewModelScope.launch { refreshGuide() }
        }

        fun closeGuide() {
            _state.update { it.copy(showGuide = false) }
            showInfo()
        }

        private fun tune(channel: TvChannel) {
            tuneJob?.cancel()
            staticUntilMs = null
            bannerHideAtMs = client.clock.nowMs() + BANNER_MS
            _state.update { it.copy(channel = channel, digits = "", digitError = false, showBanner = true, showStatic = true) }
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
                    prefetchNeighbours()
                }
        }

        /** Loads the schedules of the channels either side, so surfing up or down starts without a network wait. */
        private suspend fun prefetchNeighbours() {
            val channels = navigator.channels
            val index = channels.indexOfFirst { it.id == navigator.current?.id }
            if (index < 0 || channels.size < 2) return
            val now = client.clock.nowMs()
            val around =
                (-NEIGHBOURS..NEIGHBOURS)
                    .filter { it != 0 }
                    .map { channels[Math.floorMod(index + it, channels.size)].id }
                    .distinct()
                    .filter { !cache.covers(it, now, MIN_AHEAD_MS) }
            if (around.isNotEmpty()) fetch(around, now)
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
            val upcoming = entries.filter { it.startMs >= (now?.endMs ?: client.clock.nowMs()) && !it.offAir }.take(UPCOMING)
            _state.update { it.copy(now = now, next = next, upcoming = upcoming) }
            viewModelScope.launch {
                now?.itemId?.let { loadDetails(it) }
                next?.itemId?.let { loadDetails(it) }
            }
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
            val start = now - (now % HALF_HOUR_MS)
            val end = start + GUIDE_AHEAD_MS
            if (guideEndpoint) {
                val missing = channels.filter { !guideCache.covers(it.id, start, end - HALF_HOUR_MS) }.map { it.id }
                if (missing.isNotEmpty()) {
                    try {
                        // Fetch a little further than shown, so the next refreshes find it cached.
                        val to = end + HALF_HOUR_MS * 2
                        val guides = withContext(Dispatchers.IO) { client.guide(missing, start, to) }
                        if (guides == null) guideEndpoint = false else guides.forEach { guideCache.put(it, start, to) }
                    } catch (e: Exception) {
                        // Show what's cached; the next refresh tries again.
                    }
                }
            }
            if (!guideEndpoint) {
                val missing = channels.filter { !cache.covers(it.id, now, GUIDE_AHEAD_MS) }.map { it.id }
                if (missing.isNotEmpty()) fetch(missing, now)
            }
            val rows =
                withContext(Dispatchers.Default) {
                    channels.map {
                        val entries =
                            if (guideEndpoint) {
                                guideCache.entriesBetween(
                                    it.id,
                                    start,
                                    end,
                                )
                            } else {
                                Guide.entries(cache.slotsBetween(it.id, start, end))
                            }
                        GuideRow(it, entries)
                    }
                }
            _state.update {
                // Keep the grid where the viewer scrolled it, unless the half hour has moved past it.
                val window = it.guideWindowStartMs.coerceIn(start, start + GUIDE_AHEAD_MS - GUIDE_WINDOW_MS)
                it.copy(guide = rows, guideStartMs = start, guideWindowStartMs = window)
            }
        }

        private suspend fun refreshChannels() {
            try {
                val channels = withContext(Dispatchers.IO) { client.channels() }
                channels.forEach { channel ->
                    // A new version means the plugin rebuilt the channel: drop what's cached and re-tune if watching it.
                    guideCache.version(channel.id)?.let { if (it != channel.scheduleVersion) guideCache.invalidate(channel.id) }
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
                _clock.value = now
                if (now / COARSE_CLOCK_MS != _state.value.nowMs / COARSE_CLOCK_MS) {
                    _state.update { it.copy(nowMs = now) }
                }

                digitsCommitAtMs?.let { at ->
                    if (now >= at) {
                        digitsCommitAtMs = null
                        val typed = navigator.pendingDigits
                        val channel = navigator.commitDigits()
                        if (channel != null) {
                            tune(channel)
                        } else {
                            // Show the unknown number in red for a moment.
                            digitErrorUntilMs = now + DIGIT_ERROR_MS
                            _state.update { it.copy(digits = typed, digitError = true) }
                        }
                    }
                }

                if (_state.value.digitError && now >= digitErrorUntilMs) {
                    _state.update { it.copy(digits = "", digitError = false) }
                }

                if (_state.value.showGuide && now - lastGuideRefreshMs >= GUIDE_REFRESH_MS) {
                    lastGuideRefreshMs = now
                    refreshGuide()
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

        companion object {
            /** How often [CableTvUiState.nowMs] moves. */
            const val COARSE_CLOCK_MS = 30_000L
            private const val NEIGHBOURS = 2
            private const val TICK_MS = 500L
            private const val BANNER_MS = 6_000L
            private const val DIGIT_ERROR_MS = 1_200L
            private const val FOCUS_DETAILS_DELAY_MS = 150L
            private const val MAX_DETAILS = 300
            private const val UPCOMING = 3
            private const val GUIDE_REFRESH_MS = 60_000L
            private const val GUIDE_WINDOW_MS = 2 * 60 * 60_000L
            private const val DIGIT_TIMEOUT_MS = 2_000L
            private const val DRIFT_CHECK_MS = 10_000L
            private const val MAX_DRIFT_MS = 5_000L
            private const val CHANNEL_REFRESH_MS = 10 * 60_000L
            private const val MIN_AHEAD_MS = 30 * 60_000L
            private const val REFRESH_WHEN_LESS_THAN_MS = 2 * 60 * 60_000L
            private const val FETCH_BACK_MS = 60 * 60_000L
            private const val FETCH_AHEAD_MS = 6 * 60 * 60_000L
            private const val GUIDE_BACK_MS = 60 * 60_000L
            private const val GUIDE_AHEAD_MS = 6 * 60 * 60_000L
            private const val HALF_HOUR_MS = 30 * 60_000L
        }
    }
