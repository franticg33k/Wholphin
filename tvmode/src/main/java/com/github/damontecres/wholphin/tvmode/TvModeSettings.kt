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
    SATELLITE("Satellite: receiver banner across the top"),
}

/** When the channel logo sits in a corner of the picture. */
enum class WatermarkMode(
    val label: String,
) {
    OFF("Off"),
    PERIODIC("Every few minutes"),
    ALWAYS("Always"),
}

enum class Corner(
    val label: String,
) {
    TOP_RIGHT("Top right"),
    TOP_LEFT("Top left"),
    BOTTOM_LEFT("Bottom left"),
    BOTTOM_RIGHT("Bottom right"),
}

/** How the picture is framed. */
enum class DisplayMode(
    val label: String,
) {
    ORIGINAL("Original aspect ratio"),
    CROP_4_3("4:3, cropped to fill"),
    LETTERBOX_4_3("4:3, letterboxed"),
    BEZEL("4:3 in a CRT set"),
}

enum class Scanlines(
    val label: String,
    val alpha: Float,
) {
    OFF("Off", 0f),
    LIGHT("Light", 0.12f),
    MEDIUM("Medium", 0.22f),
    HEAVY("Heavy", 0.35f),
}

/** Accent and focus colours over the theme's own. */
enum class AccentChoice(
    val label: String,
    val argb: Long?,
) {
    THEME("Theme default", null),
    GOLD("Gold", 0xFFE9C648),
    CYAN("Cyan", 0xFF5BC0DE),
    MAGENTA("Magenta", 0xFFE05BD0),
    GREEN("Green", 0xFF4CD964),
    ORANGE("Orange", 0xFFFF9F43),
    RED("Red", 0xFFFF5A5F),
    INDIGO("Indigo", 0xFF7C7FF5),
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
    val watermark: WatermarkMode = WatermarkMode.PERIODIC,
    val watermarkCorner: Corner = Corner.TOP_RIGHT,
    val watermarkOpacity: Int = 70,
    val ratingBug: Boolean = true,
    val displayMode: DisplayMode = DisplayMode.ORIGINAL,
    val scanlines: Scanlines = Scanlines.OFF,
    val vignette: Boolean = false,
    val breakScreens: Boolean = true,
    val featurePresentation: Boolean = true,
    val upNextCard: Boolean = true,
    /** Hours without a key press before "Still watching?"; 0 = never. */
    val autoSignOffHours: Int = 4,
    val accent: AccentChoice = AccentChoice.THEME,
    val focus: AccentChoice = AccentChoice.THEME,
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
                .putString("watermark", next.watermark.name)
                .putString("watermarkCorner", next.watermarkCorner.name)
                .putInt("watermarkOpacity", next.watermarkOpacity)
                .putBoolean("ratingBug", next.ratingBug)
                .putString("displayMode", next.displayMode.name)
                .putString("scanlines", next.scanlines.name)
                .putBoolean("vignette", next.vignette)
                .putBoolean("breakScreens", next.breakScreens)
                .putBoolean("featurePresentation", next.featurePresentation)
                .putBoolean("upNextCard", next.upNextCard)
                .putInt("autoSignOffHours", next.autoSignOffHours)
                .putString("accent", next.accent.name)
                .putString("focus", next.focus.name)
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
                watermark = enumOr(prefs.getString("watermark", null), d.watermark),
                watermarkCorner = enumOr(prefs.getString("watermarkCorner", null), d.watermarkCorner),
                watermarkOpacity = prefs.getInt("watermarkOpacity", d.watermarkOpacity),
                ratingBug = prefs.getBoolean("ratingBug", d.ratingBug),
                displayMode = enumOr(prefs.getString("displayMode", null), d.displayMode),
                scanlines = enumOr(prefs.getString("scanlines", null), d.scanlines),
                vignette = prefs.getBoolean("vignette", d.vignette),
                breakScreens = prefs.getBoolean("breakScreens", d.breakScreens),
                featurePresentation = prefs.getBoolean("featurePresentation", d.featurePresentation),
                upNextCard = prefs.getBoolean("upNextCard", d.upNextCard),
                autoSignOffHours = prefs.getInt("autoSignOffHours", d.autoSignOffHours),
                accent = enumOr(prefs.getString("accent", null), d.accent),
                focus = enumOr(prefs.getString("focus", null), d.focus),
            )
        }

        private inline fun <reified E : Enum<E>> enumOr(
            name: String?,
            default: E,
        ): E = enumValues<E>().firstOrNull { it.name == name } ?: default
    }
