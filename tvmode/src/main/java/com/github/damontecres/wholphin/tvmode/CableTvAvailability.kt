package com.github.damontecres.wholphin.tvmode

import com.github.damontecres.wholphin.tvmode.core.CableTvClient
import com.github.damontecres.wholphin.tvmode.core.CableTvHttpException
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
            // Null when the server can't be reached: that says nothing about whether it has Cable TV.
            val available =
                try {
                    CableTvClient(host.transport).channels().isNotEmpty()
                } catch (e: CableTvHttpException) {
                    // A 4xx means the server answered without the plugin; a 5xx says nothing.
                    if (e.status < 500) false else null
                } catch (e: Exception) {
                    null
                }
            when {
                available == true && settings.settings.value.homeRow -> homeRow.ensureScheduled()
                available != null -> homeRow.cancel()
            }
            return available == true
        }
    }
