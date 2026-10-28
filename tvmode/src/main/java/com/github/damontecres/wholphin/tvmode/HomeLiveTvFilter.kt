package com.github.damontecres.wholphin.tvmode

import android.util.Log
import com.github.damontecres.wholphin.tvmode.core.CableTvChannelMap
import com.github.damontecres.wholphin.tvmode.core.CableTvClient
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The Cable TV channels as the app's home page needs them: Jellyfin's Live TV item ids, and the row's name. */
data class CableTvHomeChannels(
    /** Jellyfin Live TV channel item ids. */
    val liveTvIds: List<UUID>,
    /** What to call the row: the service name set in the plugin ("Cable TV" by default). */
    val title: String,
) {
    /** True when [id] is one of the channels. */
    fun contains(id: UUID): Boolean = id in liveTvIds
}

/**
 * Lets the app's own home page show a Cable TV row in place of its Live TV row. The row stays Jellyfin's "on now"
 * programmes, which already open the TV mode on the right channel; this only narrows them to Cable TV's channels and
 * names the row after the service. Chosen in the TV mode settings.
 */
@Singleton
class HomeLiveTvFilter
    @Inject
    constructor(
        private val host: TvModeHost,
        private val settings: TvModeSettingsStore,
    ) {
        @Volatile
        private var cached: CableTvHomeChannels? = null

        @Volatile
        private var cachedAtMs = 0L

        /**
         * The Cable TV channels to show instead of all of Live TV, or null to leave the row alone: the setting says all
         * of Live TV, or the server has no Cable TV (or can't be asked and none were seen before).
         */
        suspend fun channels(): CableTvHomeChannels? {
            if (settings.settings.value.homeLiveTv != HomeLiveTv.CABLE_TV) return null
            val now = System.currentTimeMillis()
            cached?.takeIf { now - cachedAtMs < TTL_MS }?.let { return it }
            return try {
                val client = CableTvClient(host.transport)
                val channels = client.channels()
                CableTvChannelMap.update(channels)
                val ids = channels.mapNotNull { it.liveTvId }.mapNotNull(::uuidOf)
                val title =
                    runCatching { client.presentation().serviceName }.getOrNull()?.takeIf { it.isNotBlank() }
                        ?: cached?.title
                        ?: DEFAULT_TITLE
                ids
                    .takeIf { it.isNotEmpty() }
                    ?.let { CableTvHomeChannels(it, title) }
                    .also {
                        cached = it
                        cachedAtMs = now
                    }
            } catch (e: Exception) {
                // Not signed in yet, offline, or the plugin isn't there: keep what was seen last, else show Live TV as is.
                Log.w(TAG, "Couldn't list the Cable TV channels for the home page: ${e.message}")
                cached
            }
        }

        private fun uuidOf(id: String): UUID? =
            runCatching {
                val plain = id.replace("-", "")
                UUID.fromString("${plain.substring(0, 8)}-${plain.substring(8, 12)}-${plain.substring(12, 16)}-${plain.substring(16, 20)}-${plain.substring(20)}")
            }.getOrNull()

        private companion object {
            const val TAG = "CableTvHomeRow"
            const val TTL_MS = 5 * 60_000L
            const val DEFAULT_TITLE = "Cable TV"
        }
    }
