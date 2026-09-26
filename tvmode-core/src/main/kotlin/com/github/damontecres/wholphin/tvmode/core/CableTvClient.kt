package com.github.damontecres.wholphin.tvmode.core

import kotlinx.serialization.json.Json

/**
 * Carries a GET request to the Jellyfin server with the user's credentials. The app implements it with its
 * authenticated HTTP client, so this module needs no Android or networking dependencies.
 */
fun interface CableTvTransport {
    /**
     * @param path path under the server's base URL, for example "CableTv/Channels"
     * @param query query parameters
     * @return the response body
     */
    suspend fun get(
        path: String,
        query: Map<String, String>,
    ): String
}

/** A channel's slots for a window, with the version they belong to. */
data class ChannelSchedule(
    val channelId: String,
    val scheduleVersion: String,
    val slots: List<TvSlot>,
)

/** Client for the Cable TV plugin's read-only API. Every response also feeds the [clock]. */
class CableTvClient(
    private val transport: CableTvTransport,
    val clock: ServerClock = ServerClock(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun channels(): List<TvChannel> {
        val dto = fetch<ChannelListDto>("CableTv/Channels", emptyMap()) { it.serverTime }
        return dto.channels.map { it.toChannel() }
    }

    suspend fun schedule(
        channelIds: Collection<String>,
        fromMs: Long,
        toMs: Long,
    ): List<ChannelSchedule> {
        val query =
            buildMap {
                if (channelIds.isNotEmpty()) put("channelIds", channelIds.joinToString(","))
                put(
                    "from",
                    java.time.Instant
                        .ofEpochMilli(fromMs)
                        .toString(),
                )
                put(
                    "to",
                    java.time.Instant
                        .ofEpochMilli(toMs)
                        .toString(),
                )
            }
        val dto = fetch<ScheduleDto>("CableTv/Schedule", query) { it.serverTime }
        return dto.channels.map { c -> ChannelSchedule(c.channelId, c.scheduleVersion, c.slots.map { it.toSlot() }) }
    }

    suspend fun presentation(): PresentationDto = fetch("CableTv/Presentation", emptyMap()) { null }

    /** Programme details from Jellyfin itself, for the guide's info panel and the player overlay. */
    suspend fun details(itemId: String): ItemDetails = fetch<JellyfinItemDto>("Items/$itemId", emptyMap()) { null }.toDetails()

    private suspend inline fun <reified T> fetch(
        path: String,
        query: Map<String, String>,
        serverTime: (T) -> String?,
    ): T {
        val sent = clock.elapsed()
        val body = transport.get(path, query)
        val received = clock.elapsed()
        val dto = json.decodeFromString<T>(body)
        serverTime(dto)?.let { clock.record(parseTime(it), sent, received) }
        return dto
    }
}
