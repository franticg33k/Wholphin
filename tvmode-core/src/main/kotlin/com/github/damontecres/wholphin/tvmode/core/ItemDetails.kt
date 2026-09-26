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
)

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
