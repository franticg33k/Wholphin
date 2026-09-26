package com.github.damontecres.wholphin.tvmode.core

internal const val MIN = 60_000L
internal const val T0 = 1_790_000_000_000L

internal fun slot(
    id: String,
    startMin: Double,
    endMin: Double,
    kind: SlotKind = SlotKind.PROGRAM,
    item: String? = if (kind == SlotKind.FILLER) null else "item-$id",
    group: String = "g-$id",
    inPointMs: Long = 0,
) = TvSlot(
    id = id,
    kind = kind,
    startMs = T0 + (startMin * MIN).toLong(),
    endMs = T0 + (endMin * MIN).toLong(),
    itemId = item,
    mediaSourceId = item,
    inPointMs = inPointMs,
    outPointMs = inPointMs + ((endMin - startMin) * MIN).toLong(),
    title =
        if (kind == SlotKind.PROGRAM) {
            "Show $id"
        } else if (kind == SlotKind.COMMERCIAL) {
            "Ad $id"
        } else {
            null
        },
    episode = null,
    episodeTitle = null,
    guideGroup = group,
    premiere = false,
    lineup = null,
)

internal fun channel(
    id: String,
    number: String,
) = TvChannel(id, number, "Channel $number", null, "v1", 10)
