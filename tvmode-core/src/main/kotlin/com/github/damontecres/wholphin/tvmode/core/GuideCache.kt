package com.github.damontecres.wholphin.tvmode.core

/**
 * Guide entries per channel for the window fetched, so the guide opens from memory. A changed schedule version
 * replaces the channel's entries.
 */
class GuideCache {
    private class Entry(
        val version: String,
        val fromMs: Long,
        val toMs: Long,
        val entries: List<GuideEntry>,
    )

    private val channels = HashMap<String, Entry>()

    @Synchronized
    fun covers(
        channelId: String,
        fromMs: Long,
        toMs: Long,
    ): Boolean = channels[channelId]?.let { it.fromMs <= fromMs && it.toMs >= toMs } == true

    @Synchronized
    fun put(
        guide: ChannelGuide,
        fromMs: Long,
        toMs: Long,
    ) {
        channels[guide.channelId] = Entry(guide.scheduleVersion, fromMs, toMs, guide.entries.sortedBy { it.startMs })
    }

    @Synchronized
    fun entriesBetween(
        channelId: String,
        fromMs: Long,
        toMs: Long,
    ): List<GuideEntry> = channels[channelId]?.entries?.filter { it.endMs > fromMs && it.startMs < toMs } ?: emptyList()

    @Synchronized
    fun version(channelId: String): String? = channels[channelId]?.version

    @Synchronized
    fun invalidate(channelId: String) {
        channels.remove(channelId)
    }
}
