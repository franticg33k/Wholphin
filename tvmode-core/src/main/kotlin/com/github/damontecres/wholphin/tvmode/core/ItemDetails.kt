package com.github.damontecres.wholphin.tvmode.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The part of Jellyfin's BaseItemDto (GET /Items/{id}) the guide's info panel and the player overlay show.

@Serializable
internal data class JellyfinItemDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("CommunityRating") val communityRating: Float? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("ParentLogoItemId") val parentLogoItemId: String? = null,
    @SerialName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerialName("ParentBackdropImageTags") val parentBackdropImageTags: List<String> = emptyList(),
    @SerialName("MediaStreams") val mediaStreams: List<JellyfinStreamDto> = emptyList(),
)

@Serializable
internal data class JellyfinStreamDto(
    @SerialName("Type") val type: String? = null,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("Channels") val channels: Int? = null,
    @SerialName("ChannelLayout") val channelLayout: String? = null,
    @SerialName("IsDefault") val isDefault: Boolean = false,
    @SerialName("Index") val index: Int? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("IsExternal") val isExternal: Boolean = false,
    @SerialName("IsForced") val isForced: Boolean = false,
)

/** A subtitle track of an item, from Jellyfin's media streams. */
data class SubtitleInfo(
    /** Jellyfin's stream index (for the subtitle URL of an external track). */
    val index: Int,
    /** ISO 639-2 language, for example "eng", or null. */
    val language: String?,
    val label: String?,
    /** For example "subrip", "ass" or "PGSSUB". */
    val codec: String?,
    /** In a separate file: the player has to load it next to the video. */
    val external: Boolean,
    val forced: Boolean,
    val isDefault: Boolean,
) {
    /** Text formats Jellyfin can serve as WebVTT or ASS for the player to load. */
    val loadable: Boolean
        get() = codec?.lowercase() in setOf("subrip", "srt", "ass", "ssa", "webvtt", "vtt", "mov_text", "text", "microdvd", "subviewer")

    /** ASS/SSA keeps its styling when served as ASS; everything else is served as WebVTT. */
    val format: String
        get() = if (codec?.lowercase() in setOf("ass", "ssa")) "ass" else "vtt"
}

/** Language codes as Jellyfin and players write them: "en", "eng" and bibliographic codes like "ger" match. */
object SubtitleLanguages {
    private val bibliographic =
        mapOf(
            "deu" to "ger",
            "fra" to "fre",
            "zho" to "chi",
            "nld" to "dut",
            "ces" to "cze",
            "ell" to "gre",
            "fas" to "per",
            "ron" to "rum",
            "slk" to "slo",
            "msa" to "may",
            "sqi" to "alb",
            "hye" to "arm",
            "eus" to "baq",
            "mya" to "bur",
            "kat" to "geo",
            "isl" to "ice",
            "mkd" to "mac",
            "mri" to "mao",
            "bod" to "tib",
            "cym" to "wel",
        )

    /** Every spelling of a language code: its ISO 639-1, 639-2/T and 639-2/B forms where known. */
    fun variants(code: String): Set<String> {
        val c = code.trim().lowercase()
        if (c.isEmpty()) return emptySet()
        val out = mutableSetOf(c)
        runCatching { java.util.Locale.forLanguageTag(c) }.getOrNull()?.let { locale ->
            locale.language.takeIf { it.isNotEmpty() }?.let(out::add)
            runCatching { locale.isO3Language }.getOrNull()?.takeIf { it.isNotEmpty() }?.let(out::add)
        }
        bibliographic.forEach { (t, b) -> if (t in out || b in out) out += listOf(t, b) }
        return out
    }

    fun matches(
        trackLanguage: String?,
        wanted: String,
    ): Boolean = trackLanguage != null && variants(trackLanguage).intersect(variants(wanted)).isNotEmpty()
}

/** What the guide shows about a programme beyond the schedule. */
data class ItemDetails(
    val itemId: String,
    val overview: String?,
    val officialRating: String?,
    val communityRating: Float?,
    val year: Int?,
    /** Air or release date, "yyyy-MM-dd". */
    val airDate: String?,
    val runtimeMinutes: Int?,
    /** "4K", "HD" or "SD". */
    val quality: String?,
    val videoCodec: String?,
    /** For example "AAC Stereo" or "EAC3 5.1". */
    val audio: String?,
    /** Item whose Logo image to show (the series for an episode), or null. */
    val logoItemId: String?,
    /** Item whose Backdrop image to show, or null. */
    val backdropItemId: String?,
    /** The item's subtitle tracks. */
    val subtitles: List<SubtitleInfo> = emptyList(),
)

internal fun JellyfinItemDto.toDetails(): ItemDetails {
    val video = mediaStreams.firstOrNull { it.type == "Video" }
    val audio = mediaStreams.filter { it.type == "Audio" }.let { list -> list.firstOrNull { it.isDefault } ?: list.firstOrNull() }
    val height = video?.height ?: 0
    val width = video?.width ?: 0
    return ItemDetails(
        itemId = id,
        overview = overview?.takeIf { it.isNotBlank() },
        officialRating = officialRating?.takeIf { it.isNotBlank() },
        communityRating = communityRating?.takeIf { it > 0f },
        year = productionYear,
        airDate = premiereDate?.take(10),
        runtimeMinutes = runTimeTicks?.let { (it / 600_000_000L).toInt() }?.takeIf { it > 0 },
        quality =
            when {
                video == null -> null
                height >= 2000 || width >= 3800 -> "4K"
                height >= 700 || width >= 1260 -> "HD"
                else -> "SD"
            },
        videoCodec =
            video
                ?.codec
                ?.uppercase()
                ?.replace("H264", "H.264")
                ?.replace("HEVC", "HEVC"),
        audio = audio?.let { audioLabel(it) },
        logoItemId =
            when {
                imageTags.containsKey("Logo") -> id
                parentLogoItemId != null -> parentLogoItemId
                else -> null
            },
        backdropItemId =
            when {
                backdropImageTags.isNotEmpty() -> id
                parentBackdropImageTags.isNotEmpty() -> parentBackdropItemId
                else -> null
            },
        subtitles =
            mediaStreams.filter { it.type == "Subtitle" && it.index != null }.map {
                SubtitleInfo(it.index!!, it.language, it.displayTitle, it.codec, it.isExternal, it.isForced, it.isDefault)
            },
    )
}

private fun audioLabel(stream: JellyfinStreamDto): String {
    val codec = stream.codec?.uppercase()?.replace("DCA", "DTS") ?: "Audio"
    val layout =
        when (stream.channels) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            null -> stream.channelLayout
            else -> "${stream.channels} ch"
        }
    return listOfNotNull(codec, layout).joinToString(" ")
}
