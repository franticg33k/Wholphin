package com.github.damontecres.wholphin.tvmode

import androidx.media3.common.Player
import com.github.damontecres.wholphin.tvmode.core.CableTvTransport

/**
 * What the TV mode needs from the app. Wholphin implements it with its signed-in API client and player factory; this
 * interface is the only coupling between the two.
 */
interface TvModeHost {
    /** Authenticated GET requests to the current server. */
    val transport: CableTvTransport

    /** A direct-play (static) stream URL for an item's media source; [audio] for music. */
    fun streamUrl(
        itemId: String,
        mediaSourceId: String?,
        audio: Boolean = false,
    ): String

    /** URL of an item's subtitle track [index], converted by the server to [format] ("vtt" or "ass"). */
    fun subtitleUrl(
        itemId: String,
        mediaSourceId: String?,
        index: Int,
        format: String,
    ): String

    /**
     * URL of an item's image ("Logo", "Backdrop", "Primary"), scaled to at most [maxHeight] pixels. Jellyfin serves
     * images without authentication, so the URL can go straight to the image loader.
     */
    fun imageUrl(
        itemId: String,
        type: String,
        maxHeight: Int,
    ): String

    /** Creates the video player, configured with the user's playback settings. */
    suspend fun createPlayer(): Player

    /** Releases a player created by [createPlayer]. */
    fun releasePlayer(player: Player)

    /** Leaves TV mode (back to the previous page). */
    fun exit()
}
