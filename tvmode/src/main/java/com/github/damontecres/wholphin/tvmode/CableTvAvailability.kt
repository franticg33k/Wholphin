package com.github.damontecres.wholphin.tvmode

import com.github.damontecres.wholphin.tvmode.core.CableTvChannelMap
import com.github.damontecres.wholphin.tvmode.core.CableTvClient
import com.github.damontecres.wholphin.tvmode.core.CableTvHttpException
import com.github.damontecres.wholphin.tvmode.core.HomeRowPolicy
import com.github.damontecres.wholphin.tvmode.core.RowAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the current server runs the Cable TV plugin, so the app only offers TV mode where it works.
 */
@Singleton
class CableTvAvailability
    @Inject
    constructor(
        private val host: TvModeHost,
        private val homeRow: HomeRowScheduler,
        private val settings: TvModeSettingsStore,
    ) {
        /**
         * True when the plugin answers and has at least one channel. This is also where the home screen row is started
         * (or taken away, on a server without Cable TV): it is the one place that knows the server has channels.
         */
        suspend fun isAvailable(): Boolean {
            var status: Int? = null
            var count: Int? = null
            try {
                val channels = CableTvClient(host.transport).channels()
                CableTvChannelMap.update(channels)
                count = channels.size
            } catch (e: CableTvHttpException) {
                status = e.status
            } catch (e: Exception) {
                // No answer (offline, not signed in yet): says nothing about whether the server has Cable TV.
            }
            when (HomeRowPolicy.afterAvailabilityCheck(status, count)) {
                RowAction.PUBLISH -> if (settings.settings.value.homeRow) homeRow.ensureScheduled() else homeRow.cancel()
                RowAction.REMOVE -> homeRow.cancel()
                RowAction.KEEP -> Unit
            }
            return (count ?: 0) > 0
        }
    }
