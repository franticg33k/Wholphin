package com.github.damontecres.wholphin.tvmode.core

/** One guide cell: a programme with its breaks folded in, or an off-air stretch. */
data class GuideEntry(
    val guideGroup: String,
    val title: String,
    val episode: String?,
    val episodeTitle: String?,
    val startMs: Long,
    val endMs: Long,
    val premiere: Boolean,
    val lineup: String?,
    val offAir: Boolean,
    val itemId: String? = null,
    val year: Int? = null,
    val rating: String? = null,
    val movie: Boolean = false,
    /** "stream", "generated" (weather), "music" or "trailers" for entries that aren't one library item. */
    val kind: String? = null,
    val seriesId: String? = null,
    val artist: String? = null,
) {
    /** What kind of programme this is, for guide colours. */
    val contentType: ContentType
        get() =
            when {
                offAir -> ContentType.OFF_AIR
                rating?.uppercase() in KIDS_RATINGS -> ContentType.KIDS
                movie -> ContentType.MOVIE
                else -> ContentType.SHOW
            }

    private companion object {
        val KIDS_RATINGS = setOf("TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G", "G")
    }
}

/** Guide colour groups. */
enum class ContentType { SHOW, MOVIE, KIDS, OFF_AIR }

object Guide {
    /** Groups slots into guide cells by their guide group, the way the plugin's Live TV guide does. */
    fun entries(slots: List<TvSlot>): List<GuideEntry> {
        val result = mutableListOf<GuideEntry>()
        var group = mutableListOf<TvSlot>()

        fun flush() {
            if (group.isEmpty()) return
            val program = group.firstOrNull { it.kind == SlotKind.PROGRAM }
            result +=
                GuideEntry(
                    guideGroup = group.first().guideGroup,
                    title = program?.title ?: group.firstNotNullOfOrNull { it.title } ?: "Off air",
                    episode = program?.episode,
                    episodeTitle = program?.episodeTitle,
                    startMs = group.first().startMs,
                    endMs = group.last().endMs,
                    premiere = group.any { it.premiere },
                    lineup = group.firstNotNullOfOrNull { it.lineup },
                    offAir = program == null && group.none { it.isPlayable || it.kind == SlotKind.STREAM || it.kind == SlotKind.GENERATED },
                    itemId = program?.itemId,
                    year = program?.year,
                    rating = program?.rating,
                    movie = program?.movie == true,
                    kind =
                        when (group.first().kind) {
                            SlotKind.STREAM -> "stream"
                            SlotKind.GENERATED -> "generated"
                            else -> null
                        },
                    seriesId = program?.seriesId,
                    artist = program?.artist,
                )
            group = mutableListOf()
        }

        for (slot in slots.sortedBy { it.startMs }) {
            if (group.isNotEmpty() && group.last().guideGroup != slot.guideGroup) flush()
            group += slot
        }
        flush()
        return result
    }

    /** The entry airing at [timeMs] and the one after it, for the channel banner. */
    fun nowAndNext(
        entries: List<GuideEntry>,
        timeMs: Long,
    ): Pair<GuideEntry?, GuideEntry?> {
        val index = entries.indexOfFirst { timeMs >= it.startMs && timeMs < it.endMs }
        if (index < 0) return null to entries.firstOrNull { it.startMs > timeMs }
        return entries[index] to entries.getOrNull(index + 1)
    }
}
