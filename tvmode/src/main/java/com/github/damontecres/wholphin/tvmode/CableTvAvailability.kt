package com.github.damontecres.wholphin.tvmode

import com.github.damontecres.wholphin.tvmode.core.CableTvClient
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
    ) {
        /** True when the plugin answers and has at least one channel. */
        suspend fun isAvailable(): Boolean =
            try {
                CableTvClient(host.transport).channels().isNotEmpty()
            } catch (e: Exception) {
                false
            }
    }
