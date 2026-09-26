package com.github.damontecres.wholphin.tvmode.core

/**
 * Slots fetched so far, per channel, so tuning in needs no network round trip.
 *
 * A channel's cached slots are replaced whole when its `scheduleVersion` changes (the plugin rebuilt it); otherwise
 * newly fetched windows are merged in.
 */
class ScheduleCache {
    private class Entry(
        val version: String,
        val slots: List<TvSlot>,
        val coveredFromMs: Long,
        val coveredToMs: Long,
    )

    private val entries = mutableMapOf<String, Entry>()

    /** Adds a fetched window. */
    @Synchronized
    fun merge(
        schedule: ChannelSchedule,
        fromMs: Long,
        toMs: Long,
    ) {
        val existing = entries[schedule.channelId]
        val sorted = schedule.slots.sortedBy { it.startMs }
        val windowFrom = minOf(fromMs, sorted.firstOrNull()?.startMs ?: fromMs)
        val windowTo = maxOf(toMs, sorted.lastOrNull()?.endMs ?: toMs)
        if (existing == null || existing.version != schedule.scheduleVersion) {
            entries[schedule.channelId] = Entry(schedule.scheduleVersion, sorted, windowFrom, windowTo)
            return
        }

        val byId = LinkedHashMap<String, TvSlot>()
        existing.slots.forEach { byId[it.id] = it }
        sorted.forEach { byId[it.id] = it }
        val merged = byId.values.sortedBy { it.startMs }
        // Coverage only grows when the windows touch; otherwise keep the part that includes the new window.
        val touches = windowFrom <= existing.coveredToMs && windowTo >= existing.coveredFromMs
        entries[schedule.channelId] =
            if (touches) {
                Entry(existing.version, merged, minOf(windowFrom, existing.coveredFromMs), maxOf(windowTo, existing.coveredToMs))
            } else {
                Entry(existing.version, sorted, windowFrom, windowTo)
            }
    }

    /** The version cached for a channel, or null. */
    @Synchronized
    fun version(channelId: String): String? = entries[channelId]?.version

    /** Forgets a channel, for example after its version changed on the server. */
    @Synchronized
    fun invalidate(channelId: String) {
        entries.remove(channelId)
    }

    /** The slot airing at [timeMs], if cached. */
    @Synchronized
    fun slotAt(
        channelId: String,
        timeMs: Long,
    ): TvSlot? {
        val slots = entries[channelId]?.slots ?: return null
        val index = indexAt(slots, timeMs)
        return if (index >= 0) slots[index] else null
    }

    /** The slot airing at [timeMs] and the ones after it, up to [untilMs]. */
    @Synchronized
    fun slotsFrom(
        channelId: String,
        timeMs: Long,
        untilMs: Long = Long.MAX_VALUE,
    ): List<TvSlot> {
        val slots = entries[channelId]?.slots ?: return emptyList()
        val index = indexAt(slots, timeMs).let { if (it < 0) slots.indexOfFirst { s -> s.startMs >= timeMs } else it }
        if (index < 0) return emptyList()
        return slots.subList(index, slots.size).takeWhile { it.startMs < untilMs }
    }

    /** Slots overlapping [fromMs]..[toMs], for the guide. */
    @Synchronized
    fun slotsBetween(
        channelId: String,
        fromMs: Long,
        toMs: Long,
    ): List<TvSlot> = entries[channelId]?.slots?.filter { it.endMs > fromMs && it.startMs < toMs } ?: emptyList()

    /** Whether the cache covers [nowMs] plus at least [aheadMs] for a channel. */
    @Synchronized
    fun covers(
        channelId: String,
        nowMs: Long,
        aheadMs: Long,
    ): Boolean {
        val entry = entries[channelId] ?: return false
        return entry.coveredFromMs <= nowMs && entry.coveredToMs >= nowMs + aheadMs
    }

    private fun indexAt(
        slots: List<TvSlot>,
        timeMs: Long,
    ): Int {
        var low = 0
        var high = slots.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val slot = slots[mid]
            when {
                timeMs < slot.startMs -> high = mid - 1
                timeMs >= slot.endMs -> low = mid + 1
                else -> return mid
            }
        }
        return -1
    }
}
