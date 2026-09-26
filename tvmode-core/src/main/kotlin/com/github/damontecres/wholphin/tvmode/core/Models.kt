package com.github.damontecres.wholphin.tvmode.core

import kotlinx.serialization.Serializable
import java.time.Instant

// Wire format of the Cable TV plugin's API (docs/api-contract.md in jellyfin-cable-tv).
// Nullable fields may be omitted by the server.

@Serializable
data class ChannelDto(
    val channelId: String,
    val number: String,
    val name: String,
    val logoUrl: String? = null,
    val scheduleVersion: String,
    val poolSize: Int = 0,
    val category: String? = null,
)

@Serializable
data class ChannelListDto(
    val serverTime: String,
    val channels: List<ChannelDto> = emptyList(),
)

@Serializable
data class SlotDto(
    val slotId: String,
    val kind: String,
    val start: String,
    val end: String,
    val itemId: String? = null,
    val mediaSourceId: String? = null,
    val inPointMs: Long = 0,
    val outPointMs: Long = 0,
    val title: String? = null,
    val episode: String? = null,
    val episodeTitle: String? = null,
    val guideGroup: String,
    val premiere: Boolean? = null,
    val lineup: String? = null,
    val year: Int? = null,
    val rating: String? = null,
    val movie: Boolean? = null,
)

@Serializable
data class ChannelScheduleDto(
    val channelId: String,
    val number: String,
    val scheduleVersion: String,
    val slots: List<SlotDto> = emptyList(),
)

@Serializable
data class ScheduleDto(
    val serverTime: String,
    val from: String,
    val to: String,
    val scheduleVersion: String,
    val channels: List<ChannelScheduleDto> = emptyList(),
)

@Serializable
data class GuideProgramDto(
    val guideGroup: String,
    val start: String,
    val end: String,
    val itemId: String? = null,
    val title: String? = null,
    val episode: String? = null,
    val episodeTitle: String? = null,
    val premiere: Boolean? = null,
    val lineup: String? = null,
    val year: Int? = null,
    val rating: String? = null,
    val movie: Boolean? = null,
)

@Serializable
data class ChannelGuideDto(
    val channelId: String,
    val scheduleVersion: String,
    val programs: List<GuideProgramDto> = emptyList(),
)

@Serializable
data class GuideDto(
    val serverTime: String,
    val from: String,
    val to: String,
    val channels: List<ChannelGuideDto> = emptyList(),
)

internal fun GuideProgramDto.toEntry() =
    GuideEntry(
        guideGroup = guideGroup,
        title = title ?: "Off air",
        episode = episode,
        episodeTitle = episodeTitle,
        startMs = parseTime(start),
        endMs = parseTime(end),
        premiere = premiere == true,
        lineup = lineup,
        offAir = itemId == null,
        itemId = itemId,
        year = year,
        rating = rating?.takeIf { it.isNotBlank() },
        movie = movie == true,
    )

@Serializable
data class NowDto(
    val serverTime: String,
    val channelId: String,
    val scheduleVersion: String,
    val current: SlotDto,
    val offsetMs: Long,
    val next: List<SlotDto> = emptyList(),
)

@Serializable
data class ChannelBrandingDto(
    val channelId: String,
    val logoUrl: String? = null,
)

@Serializable
data class PresentationDto(
    val serviceName: String = "Cable TV",
    val channels: List<ChannelBrandingDto> = emptyList(),
)

/** What a slot plays. */
enum class SlotKind {
    PROGRAM,
    COMMERCIAL,
    BUMPER,
    FILLER,
    STREAM,
    GENERATED,
    UNKNOWN,
    ;

    companion object {
        fun parse(value: String): SlotKind = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

/** A channel as the TV mode uses it. */
data class TvChannel(
    val id: String,
    val number: String,
    val name: String,
    val logoUrl: String?,
    val scheduleVersion: String,
    val poolSize: Int,
    val category: String? = null,
)

/**
 * One entry on a channel's timeline, with times as epoch milliseconds on the server's clock.
 *
 * [inPointMs]..[outPointMs] is the part of the item this slot plays.
 */
data class TvSlot(
    val id: String,
    val kind: SlotKind,
    val startMs: Long,
    val endMs: Long,
    val itemId: String?,
    val mediaSourceId: String?,
    val inPointMs: Long,
    val outPointMs: Long,
    val title: String?,
    val episode: String?,
    val episodeTitle: String?,
    val guideGroup: String,
    val premiere: Boolean,
    val lineup: String?,
    val year: Int? = null,
    val rating: String? = null,
    val movie: Boolean = false,
) {
    val durationMs: Long get() = endMs - startMs

    /** Whether the player can play this slot; filler and unknown kinds are shown as static instead. */
    val isPlayable: Boolean
        get() = itemId != null && kind in setOf(SlotKind.PROGRAM, SlotKind.COMMERCIAL, SlotKind.BUMPER)

    fun covers(timeMs: Long): Boolean = timeMs in startMs until endMs
}

internal fun parseTime(value: String): Long = Instant.parse(value).toEpochMilli()

internal fun ChannelDto.toChannel() =
    TvChannel(
        channelId,
        number,
        name,
        logoUrl,
        scheduleVersion,
        poolSize,
        category?.takeIf {
            it.isNotBlank()
        },
    )

internal fun SlotDto.toSlot() =
    TvSlot(
        id = slotId,
        kind = SlotKind.parse(kind),
        startMs = parseTime(start),
        endMs = parseTime(end),
        itemId = itemId,
        mediaSourceId = mediaSourceId,
        inPointMs = inPointMs,
        outPointMs = outPointMs,
        title = title,
        episode = episode,
        episodeTitle = episodeTitle,
        guideGroup = guideGroup,
        premiere = premiere == true,
        lineup = lineup,
        year = year,
        rating = rating?.takeIf { it.isNotBlank() },
        movie = movie == true,
    )
