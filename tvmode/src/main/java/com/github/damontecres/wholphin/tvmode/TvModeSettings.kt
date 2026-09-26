package com.github.damontecres.wholphin.tvmode

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Built-in looks. */
enum class TvThemeId(
    val label: String,
) {
    RETRO("Retro cable"),
    MIDNIGHT("Midnight"),
    PHOSPHOR("Phosphor"),
    MODERN("Modern"),
}

/** What shows while a channel tunes in or airs filler. */
enum class TuningStyle(
    val label: String,
) {
    STATIC("Static"),
    COLOR_BARS("Colour bars"),
    STANDBY("Please stand by"),
}

/** How the guide's channel column looks. */
enum class ChannelColumnStyle(
    val label: String,
) {
    NUMBERS("Numbers"),
    LOGOS("Logos only"),
    NUMBERS_AND_LOGOS("Numbers + logos"),
}

/** The player overlay shown on channel changes and with Info. */
enum class OverlayStyle(
    val label: String,
) {
    CLASSIC("Classic: now and next along the bottom"),
    LINEUP("Lineup: bottom bar with the next programmes"),
}

data class TvModeSettings(
    val theme: TvThemeId = TvThemeId.RETRO,
    val tuningStyle: TuningStyle = TuningStyle.STATIC,
    val tuningLogo: Boolean = true,
    val channelColumn: ChannelColumnStyle = ChannelColumnStyle.NUMBERS_AND_LOGOS,
    val overlayStyle: OverlayStyle = OverlayStyle.CLASSIC,
    val clock24h: Boolean = false,
    val dimEnded: Boolean = true,
    val timeIndicator: Boolean = true,
    val detailedGuide: Boolean = true,
    val contentTypeColors: Boolean = true,
    val mediaInfo: Boolean = true,
)

/** TV mode's own settings, kept on the device (they're about the look of this TV, not the server). */
@Singleton
class TvModeSettingsStore
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("cable_tv_mode", Context.MODE_PRIVATE)
        private val _settings = MutableStateFlow(load())
        val settings: StateFlow<TvModeSettings> = _settings.asStateFlow()

        fun update(transform: (TvModeSettings) -> TvModeSettings) {
            val next = transform(_settings.value)
            _settings.value = next
            prefs
                .edit()
                .putString("theme", next.theme.name)
                .putString("tuningStyle", next.tuningStyle.name)
                .putBoolean("tuningLogo", next.tuningLogo)
                .putString("channelColumn", next.channelColumn.name)
                .putString("overlayStyle", next.overlayStyle.name)
                .putBoolean("clock24h", next.clock24h)
                .putBoolean("dimEnded", next.dimEnded)
                .putBoolean("timeIndicator", next.timeIndicator)
                .putBoolean("detailedGuide", next.detailedGuide)
                .putBoolean("contentTypeColors", next.contentTypeColors)
                .putBoolean("mediaInfo", next.mediaInfo)
                .apply()
        }

        private fun load(): TvModeSettings {
            val d = TvModeSettings()
            return TvModeSettings(
                theme = enumOr(prefs.getString("theme", null), d.theme),
                tuningStyle = enumOr(prefs.getString("tuningStyle", null), d.tuningStyle),
                tuningLogo = prefs.getBoolean("tuningLogo", d.tuningLogo),
                channelColumn = enumOr(prefs.getString("channelColumn", null), d.channelColumn),
                overlayStyle = enumOr(prefs.getString("overlayStyle", null), d.overlayStyle),
                clock24h = prefs.getBoolean("clock24h", d.clock24h),
                dimEnded = prefs.getBoolean("dimEnded", d.dimEnded),
                timeIndicator = prefs.getBoolean("timeIndicator", d.timeIndicator),
                detailedGuide = prefs.getBoolean("detailedGuide", d.detailedGuide),
                contentTypeColors = prefs.getBoolean("contentTypeColors", d.contentTypeColors),
                mediaInfo = prefs.getBoolean("mediaInfo", d.mediaInfo),
            )
        }

        private inline fun <reified E : Enum<E>> enumOr(
            name: String?,
            default: E,
        ): E = enumValues<E>().firstOrNull { it.name == name } ?: default
    }
