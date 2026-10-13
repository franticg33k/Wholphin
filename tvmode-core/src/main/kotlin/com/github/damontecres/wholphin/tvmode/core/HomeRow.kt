package com.github.damontecres.wholphin.tvmode.core

/** One tile of the home screen row: what a channel is airing right now. */
data class OnNowEntry(
    val channelId: String,
    val number: String,
    val channelName: String,
    val title: String,
    /** Season/episode and episode title, when the programme is an episode. */
    val subtitle: String?,
    val startMs: Long,
    val endMs: Long,
    /** The Jellyfin item to take the tile's picture from; null when the channel's logo is the best there is. */
    val itemId: String?,
    val movie: Boolean,
    val logoUrl: String?,
    val nextTitle: String?,
)

/** Builds the "On now" row shown on the Android TV home screen. */
object HomeRow {
    /** The launcher shows a handful of tiles; more only slows the row down. */
    const val MAX_TILES = 12

    /**
     * What each channel airs at [nowMs], in channel order. Channels that are off the air, or that have no guide yet,
     * are left out.
     */
    fun onNow(
        channels: List<TvChannel>,
        guides: List<ChannelGuide>,
        nowMs: Long,
        max: Int = MAX_TILES,
    ): List<OnNowEntry> {
        val byChannel = guides.associateBy { it.channelId }
        return channels
            .sortedWith(compareBy({ it.number.toIntOrNull() ?: Int.MAX_VALUE }, { it.number }))
            .mapNotNull { channel ->
                val (now, next) = Guide.nowAndNext(byChannel[channel.id]?.entries.orEmpty(), nowMs)
                if (now == null || now.offAir) return@mapNotNull null
                OnNowEntry(
                    channelId = channel.id,
                    number = channel.number,
                    channelName = channel.name,
                    title = now.title,
                    subtitle = listOfNotNull(now.episode, now.episodeTitle).joinToString(" · ").ifBlank { null },
                    startMs = now.startMs,
                    endMs = now.endMs,
                    itemId = now.itemId,
                    movie = now.movie,
                    logoUrl = channel.logoUrl,
                    nextTitle = next?.takeUnless { it.offAir }?.title,
                )
            }.take(max)
    }
}
