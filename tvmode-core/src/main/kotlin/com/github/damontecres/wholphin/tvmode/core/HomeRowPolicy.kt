package com.github.damontecres.wholphin.tvmode.core

/** What to do with the home screen row after something happened. */
enum class RowAction {
    /** Replace the tiles with fresh ones. */
    PUBLISH,

    /** Leave the row as it is. */
    KEEP,

    /** Take the row off the home screen. */
    REMOVE,
}

/**
 * When the home screen row may be removed. A row that vanishes until the app is opened again is worse than one that is
 * a little stale, so it is removed only when the server has said, plainly, that there is nothing to show.
 */
object HomeRowPolicy {
    /** After a refresh that reached the server: [channels] it has, and how many [tiles] are on the air right now. */
    fun afterRefresh(
        channels: Int,
        tiles: Int,
    ): RowAction =
        when {
            channels == 0 -> RowAction.REMOVE

            // Every channel off the air for a moment, or a guide that is still empty: keep the last tiles.
            tiles == 0 -> RowAction.KEEP

            else -> RowAction.PUBLISH
        }

    /**
     * After asking the server for its channels.
     * @param status the HTTP status of a failed request, or null when it succeeded or never got an answer
     * @param channels how many channels a successful answer listed; null when there was no answer
     */
    fun afterAvailabilityCheck(
        status: Int?,
        channels: Int?,
    ): RowAction =
        when {
            channels != null -> if (channels == 0) RowAction.REMOVE else RowAction.PUBLISH

            // Only "no such endpoint" means the plugin isn't there. A refused login (401/403), a server error or a
            // network problem says nothing about whether Cable TV exists.
            status == 404 -> RowAction.REMOVE

            else -> RowAction.KEEP
        }
}
