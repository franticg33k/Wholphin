package com.github.damontecres.wholphin.tvmode.core

/**
 * One item for the player's queue: play [slot]'s item from [clipStartMs] to [clipEndMs] (positions in the item).
 */
data class PlayItem(
    val slot: TvSlot,
    val clipStartMs: Long,
    val clipEndMs: Long,
)

/** What to do when tuning to a channel at a given moment. */
sealed interface TunePlan {
    /**
     * Queue [items] and start the first at [startPositionMs] (relative to its clip start). More items follow once the
     * player reaches the end of the queue; [queuedUntilMs] is when the last queued item ends on the schedule.
     */
    data class Play(
        val items: List<PlayItem>,
        val startPositionMs: Long,
        val queuedUntilMs: Long,
    ) : TunePlan

    /** Nothing playable airs now (filler or off air): show static until [untilMs], then tune again. */
    data class Static(
        val slot: TvSlot,
        val untilMs: Long,
    ) : TunePlan

    /** The schedule for now isn't loaded yet. */
    data object NeedsSchedule : TunePlan
}

/**
 * Turns cached slots into player instructions. Everything is computed from the schedule and the server clock, so a
 * channel change needs no network call.
 */
object TuneInPlanner {
    /** How many items to queue ahead of the one playing, so the player can preload them. */
    const val QUEUE_AHEAD = 3

    /** Joins closer than this to a slot's end skip to the next slot instead of flashing a few frames. */
    const val MIN_JOIN_REMAINING_MS = 1_500L

    /**
     * @param upcoming the slot airing at [nowMs] followed by the next ones, as from [ScheduleCache.slotsFrom]
     */
    fun plan(
        upcoming: List<TvSlot>,
        nowMs: Long,
        queueAhead: Int = QUEUE_AHEAD,
    ): TunePlan {
        var slots = upcoming.dropWhile { it.endMs <= nowMs }
        val first = slots.firstOrNull() ?: return TunePlan.NeedsSchedule
        if (first.startMs > nowMs) return TunePlan.NeedsSchedule

        var joinAt = nowMs
        if (first.endMs - nowMs < MIN_JOIN_REMAINING_MS && slots.size > 1) {
            slots = slots.drop(1)
            joinAt = slots.first().startMs
        }

        val current = slots.first()
        if (!current.isPlayable) {
            return TunePlan.Static(current, current.endMs)
        }

        val items = mutableListOf<PlayItem>()
        for (slot in slots) {
            if (!slot.isPlayable || items.size > queueAhead) break
            if (items.isNotEmpty() && slot.startMs != items.last().slot.endMs) break
            items += PlayItem(slot, slot.inPointMs, slot.outPointMs)
        }

        val offset = (joinAt - current.startMs).coerceAtLeast(0)
        return TunePlan.Play(items, offset, items.last().slot.endMs)
    }

    /**
     * The items to append after [lastQueued] so the queue stays [queueAhead] items ahead of [playing]. Stops at filler;
     * the player shows static there and tunes again when it ends.
     */
    fun extend(
        upcoming: List<TvSlot>,
        playing: TvSlot,
        lastQueued: TvSlot,
        queueAhead: Int = QUEUE_AHEAD,
    ): List<PlayItem> {
        val afterPlaying = upcoming.dropWhile { it.id != playing.id }
        val queuedAhead = afterPlaying.indexOfFirst { it.id == lastQueued.id }
        if (queuedAhead < 0) return emptyList()
        val wanted = queueAhead - queuedAhead
        if (wanted <= 0) return emptyList()

        val result = mutableListOf<PlayItem>()
        var previous = lastQueued
        for (slot in afterPlaying.drop(queuedAhead + 1)) {
            if (result.size >= wanted || !slot.isPlayable || slot.startMs != previous.endMs) break
            result += PlayItem(slot, slot.inPointMs, slot.outPointMs)
            previous = slot
        }
        return result
    }

    /**
     * How far the player is from where the schedule says it should be, in milliseconds (positive when behind).
     *
     * @param positionMs player position within the current item's clip
     */
    fun drift(
        slot: TvSlot,
        positionMs: Long,
        nowMs: Long,
    ): Long = (nowMs - slot.startMs) - positionMs
}
