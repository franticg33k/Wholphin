package com.github.damontecres.wholphin.tvmode.core

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference

/**
 * Deep links into the TV mode: `wholphin://cabletv` opens it and `wholphin://cabletv?channel=<id>` also tunes to a
 * channel. The Android TV home screen row uses them, and so can anything else that can fire an intent.
 */
object CableTvLinks {
    const val SCHEME = "wholphin"
    const val HOST = "cabletv"
    const val CHANNEL = "channel"

    /** The link that opens the TV mode, on [channelId] when given. */
    fun uri(channelId: String? = null): String =
        "$SCHEME://$HOST" + (channelId?.let { "?$CHANNEL=" + URLEncoder.encode(it, "UTF-8") } ?: "")

    /** True when [uri] is a TV mode link. */
    fun isLink(uri: String?): Boolean = parse(uri) != null

    /**
     * Reads a TV mode link.
     * @return null when it isn't one; else the channel to tune to (null for none)
     */
    fun parse(uri: String?): Result? {
        val parsed = runCatching { URI(uri ?: return null) }.getOrNull() ?: return null
        if (!parsed.scheme.equals(SCHEME, ignoreCase = true) || !parsed.host.equals(HOST, ignoreCase = true)) return null
        val channel =
            parsed.rawQuery
                ?.split('&')
                ?.map { it.substringBefore('=') to it.substringAfter('=', "") }
                ?.firstOrNull { it.first == CHANNEL }
                ?.second
                ?.let { URLDecoder.decode(it, "UTF-8") }
                ?.takeIf { it.isNotBlank() }
        return Result(channel)
    }

    /** A parsed link: [channelId] is the channel to tune to, if any. */
    data class Result(
        val channelId: String?,
    )
}

/**
 * A request from outside the TV mode (a deep link) to tune to a channel, held until the TV screen starts and takes it.
 */
object CableTvLaunch {
    private val pending = AtomicReference<String?>(null)

    /** Asks the TV mode to tune to [channelId] when it opens. */
    fun request(channelId: String?) = pending.set(channelId)

    /** Takes the requested channel, if any; it is returned once. */
    fun take(): String? = pending.getAndSet(null)
}
